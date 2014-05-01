package parser;



import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

import parser.DependencyInstance.SpecialPos;
import parser.FeatureTemplate.Arc;
import parser.Options.LearningMode;
import parser.Options.PossibleLang;
import parser.io.DependencyReader;
import utils.Alphabet;
import utils.Dictionary;
import utils.FeatureVector;
import utils.Utils;
import static parser.FeatureTemplate.Arc.*;
import static parser.FeatureTemplate.Word.*;

public class DependencyPipe implements Serializable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	//public static boolean useLexicalFeature = true;
	
	public static int TOKEN_START = 1;
	public static int TOKEN_END = 2;
	public static int TOKEN_MID = 3;

	// for punc
	public static int TOKEN_QUOTE = 4;
	public static int TOKEN_RRB = 5;
	public static int TOKEN_LRB = 6;

	
    public transient Options options;
    public Dictionary tagDictionary;
    public Dictionary wordDictionary;    
    public int tagNumBits, wordNumBits, disNumBits = 4;
    
	public static String unknowWord = "*UNKNOWN*";	
	public Dictionary wordVecDictionary;
	public double[][] wordVectors = null;
	public double[] unknownWv = null;
	public transient boolean onlyLowerCase = false;
	
	public int numArcFeats;					// number of arc structure features
	public int numWordFeats;				// number of word features
	
    public int[] types;						// array that maps label index to label hash code in tagDictionary
    public Alphabet typeAlphabet;
	private Alphabet wordAlphabet;			// the alphabet of word features (e.g. \phi_h, \phi_m)
	private Alphabet arcAlphabet;			// the alphabet of 1st order arc features (e.g. \phi_{h->m})
	
	// language specific info
	public int ccDepType;
	public HashSet<String> conjWord;
	public HashMap<String, String> coarseMap;
	
	public DependencyPipe(Options options) throws IOException 
	{
		tagDictionary = new Dictionary();
		wordDictionary = new Dictionary();
		typeAlphabet = new Alphabet();
		
		wordAlphabet = new Alphabet();
		arcAlphabet = new Alphabet();
		this.options = options;
		
		numArcFeats = 0;
		numWordFeats = 0;
		
		loadLanguageInfo();
	}
	
	/***
	 * load language specific information
	 * ccDepType: coordination dependency type
	 * conjWord: word considered as a conjunction
	 * coarseMap: fine-to-coarse map 
	 */
	public void loadLanguageInfo() throws IOException {
		// load coarse map
		BufferedReader br = new BufferedReader(new FileReader(options.unimapFile));
		coarseMap = new HashMap<String, String>();
		String str = null;
		while ((str = br.readLine()) != null) {
			String[] data = str.split("\\s+");
			coarseMap.put(data[0], data[1]);
		}
		br.close();
		
		coarseMap.put("<root-POS>", "ROOT");
		
		// decide ccDepType
		PossibleLang lang = options.lang;
		if (lang == PossibleLang.Arabic || lang == PossibleLang.Slovene
				|| lang == PossibleLang.Chinese || lang == PossibleLang.Czech
				|| lang == PossibleLang.Dutch) {
			ccDepType = 0;
		} else if (lang == PossibleLang.Bulgarian || lang == PossibleLang.German
				|| lang == PossibleLang.Portuguese || lang == PossibleLang.Spanish) {
			ccDepType = 1;
		} else if (lang == PossibleLang.Danish || lang == PossibleLang.English08) {
			ccDepType = 2;
		} else if (lang == PossibleLang.Japanese) {
			ccDepType = 3;
		} else if (lang == PossibleLang.Swedish) {
			ccDepType = 4;
		} else if (lang == PossibleLang.Turkish) {
			ccDepType = 5;
		} else {
			ccDepType = 0;
		}
		
		// fill conj word
		conjWord = new HashSet<String>();
		switch (lang) {
		case Turkish:
			conjWord.add("ve");
			conjWord.add("veya");
			break;
		case Arabic:
			conjWord.add("w");
			conjWord.add(">w");
			conjWord.add(">n");
			break;
		case Bulgarian:
			conjWord.add("и");
			conjWord.add("или");
			break;
		case Chinese:
			conjWord.add("和");
			conjWord.add("或");
			break;
		case Czech:
			conjWord.add("a");
			conjWord.add("ale");
			conjWord.add("i");
			conjWord.add("nebo");
			break;
		case Danish:
			conjWord.add("og");
			conjWord.add("eller");
			break;
		case Dutch:
			conjWord.add("en");
			conjWord.add("of");
			break;
		case English08:
			conjWord.add("and");
			conjWord.add("or");
			break;
		case German:
			conjWord.add("und");
			conjWord.add("oder");
			break;
		case Japanese:
			conjWord.add("ya");
			break;
		case Portuguese:
			conjWord.add("e");
			conjWord.add("ou");
			break;
		case Slovene:
			conjWord.add("in");
			conjWord.add("ali");
			break;
		case Spanish:
			conjWord.add("y");
			conjWord.add("e");
			conjWord.add("o");
			break;
		case Swedish:
			conjWord.add("och");
			conjWord.add("eller");
			break;
		default:
			break;
		}
	}
	
	/***
	 * Build dictionaries that maps word strings, POS strings, etc into
	 * corresponding integer IDs. This method is called before creating 
	 * the feature alphabets and before training a dependency model. 
	 * 
	 * @param file file path of the training data
	 * @throws IOException
	 */
	public void createDictionaries(String file) throws IOException 
	{
		
		long start = System.currentTimeMillis();
		System.out.print("Creating dictionaries ... ");
		
		// all forms, lemmas, pos etc should be 1-base indexed.
		tagDictionary.lookupIndex("#ZERO_INDEX#");
		wordDictionary.lookupIndex("#ZERO_INDEX#");
		
		// special symbols used in features
		tagDictionary.lookupIndex("#TOKEN_START#");
		tagDictionary.lookupIndex("#TOKEN_MID#");
		tagDictionary.lookupIndex("#TOKEN_END#");		
		wordDictionary.lookupIndex("#TOKEN_START#");
		wordDictionary.lookupIndex("#TOKEN_MID#");
		wordDictionary.lookupIndex("#TOKEN_END#");
		wordDictionary.lookupIndex("form=\"");
		wordDictionary.lookupIndex("form=)");
		wordDictionary.lookupIndex("form=(");
				
		DependencyReader reader = DependencyReader.createDependencyReader(options);
		reader.startReading(file);
		DependencyInstance inst = reader.nextInstance();
		
		int cnt = 0;
		while (inst != null) {
			inst.setInstIds(tagDictionary, wordDictionary, typeAlphabet, wordVecDictionary, coarseMap, conjWord, options.lang);			
			inst = reader.nextInstance();
			
			++cnt;
			if (options.maxNumSent != -1 && cnt >= options.maxNumSent) break;
		}
		reader.close();
		
		wordDictionary.stopGrowth();
		tagDictionary.stopGrowth();
		wordNumBits = Utils.log2(wordDictionary.size() + 1);
		tagNumBits = Utils.log2(tagDictionary.size() + 1);
		
		System.out.printf("[%d ms]%n", System.currentTimeMillis() - start);
		System.out.printf("%d %d%n", numWordFeatBits, numArcFeatBits);
		System.out.printf("Lexical items: %d (%d bits)%n", wordDictionary.size(), wordNumBits);
		System.out.printf("Tag/label items: %d (%d bits)%n", tagDictionary.size(), tagNumBits);
	}

	
	/***
	 * Create feature alphabets, which maps 64-bit feature code into
	 * its integer index (starting from index 0). This method is called
	 * before training a dependency model.
	 * 
	 * @param file  file path of the training data
	 * @throws IOException
	 */
	public void createAlphabets(String file) throws IOException 
	{
	
		createDictionaries(file);
		
		if (options.wordVectorFile != null)
			loadWordVectors(options.wordVectorFile);
		
		long start = System.currentTimeMillis();
		System.out.print("Creating Alphabet ... ");
		
		HashSet<String> posTagSet = new HashSet<String>();
		HashSet<String> cposTagSet = new HashSet<String>();
		DependencyReader reader = DependencyReader.createDependencyReader(options);
		reader.startReading(file);
		
		DependencyInstance inst = reader.nextInstance();
        long tokens = 0;
		int cnt = 0;
		
		while(inst != null) {

		    ++cnt;
            tokens += (inst.length - 1);

			for (int i = 0; i < inst.length; ++i) {
				if (inst.postags != null) posTagSet.add(inst.postags[i]);
				if (inst.cpostags != null) cposTagSet.add(inst.cpostags[i]);
			}
			
			inst.setInstIds(tagDictionary, wordDictionary, typeAlphabet, wordVecDictionary, coarseMap, conjWord, options.lang);
			
		    initFeatureAlphabets(inst);
				
		    inst = reader.nextInstance();

	        if (options.maxNumSent != -1 && cnt >= options.maxNumSent) break;
		}
		
		closeAlphabets();
		reader.close();
		
		System.out.printf("[%d ms]%n", System.currentTimeMillis() - start);
        
        System.out.printf("Average sentence length: %.2f%n", (tokens+0.0)/(cnt+0.0));
		System.out.printf("Num of CONLL fine POS tags: %d%n", posTagSet.size());
		System.out.printf("Num of CONLL coarse POS tags: %d%n", cposTagSet.size());
		System.out.printf("Num of Features: %d %d%n", 
				numWordFeats, numArcFeats);
	    System.out.printf("Num of labels: %d%n", types.length);
	    
//	    if (wordVectors != null)
//	    	System.out.printf("WV unseen rate: %f%n", 
//	    			(wvMiss + 1e-8)/(wvMiss + wvHit + 1e-8));
	}
	
	/***
	 * Load word vectors. The real-value word vectors are used as auxiliary
	 * features for the tensor scores.
	 * 
	 * @param file  file path of the word vector file.
	 * @throws IOException
	 */
	public void loadWordVectors(String file) throws IOException 
	{
		
		System.out.println("Loading word vectors...");
		
		BufferedReader in = new BufferedReader(
				new InputStreamReader(new FileInputStream(file),"UTF8"));
		
		wordVecDictionary = new Dictionary();
		String line = in.readLine();
		while (line != null) {
			line = line.trim();
			String[] parts = line.split("[ \t]");
			String word = parts[0];
			wordVecDictionary.lookupIndex(word);
			line = in.readLine();
		}
		in.close();
		wordVecDictionary.stopGrowth();
		
		in = new BufferedReader(
				new InputStreamReader(new FileInputStream(file),"UTF8"));
		wordVectors = new double[wordVecDictionary.size()+1][];
		int upperCases = 0;
        int cnt = 0;
        double sumL2 = 0, minL2 = Double.POSITIVE_INFINITY, maxL2 = 0;
		line = in.readLine();
		while (line != null) {
			line = line.trim();
			String[] parts = line.split("[ \t]");
			
			String word = parts[0];
			upperCases += Character.isUpperCase(word.charAt(0)) ? 1 : 0;
            ++cnt;
            
            double s = 0;
			double [] v = new double[parts.length - 1];
			for (int i = 0; i < v.length; ++i) {
				v[i] = Double.parseDouble(parts[i+1]);
                s += v[i]*v[i];
            }
			s = Math.sqrt(s);
            sumL2 += s;
            minL2 = Math.min(minL2, s);
            maxL2 = Math.max(maxL2, s);
                        
            if (word.equalsIgnoreCase(unknowWord))
            	unknownWv = v;
            else {
            	int wordId = wordVecDictionary.lookupIndex(word);
            	if (wordId > 0) wordVectors[wordId] = v;
            }
            
			line = in.readLine();
		}
		in.close();

        sumL2 /= cnt;
        System.out.printf("Vector norm: Avg: %f  Min: %f  Max: %f%n", 
        		sumL2, minL2, maxL2);     
	}
	
	/***
	 * Close alphabets so the feature set wouldn't grow.
	 */
    public void closeAlphabets() 
    {
		
		typeAlphabet.stopGrowth();
		wordAlphabet.stopGrowth();
		arcAlphabet.stopGrowth();

		types = new int[typeAlphabet.size()];
		long[] keys = typeAlphabet.toArray();
		for(int i = 0; i < keys.length; i++) {
		    int indx = typeAlphabet.lookupIndex(keys[i]);
		    types[indx] = (int)keys[i];
		}
	
    }
    
    
    public DependencyInstance[] createInstances(String file) throws IOException 
    {
    	
    	long start = System.currentTimeMillis();
    	System.out.print("Creating instances ... ");
    	
    	DependencyReader reader = DependencyReader.createDependencyReader(options);
		reader.startReading(file);

		LinkedList<DependencyInstance> lt = new LinkedList<DependencyInstance>();
		DependencyInstance inst = reader.nextInstance();
						
		int cnt = 0;
		while(inst != null) {
			
			inst.setInstIds(tagDictionary, wordDictionary, typeAlphabet, wordVecDictionary, coarseMap, conjWord, options.lang);
			
		    //createFeatures(inst);
			lt.add(new DependencyInstance(inst));		    
			
			inst = reader.nextInstance();
			cnt++;
			if (options.maxNumSent != -1 && cnt >= options.maxNumSent) break;
			if (cnt % 1000 == 0)
				System.out.printf("%d ", cnt);
		}
				
		reader.close();
		closeAlphabets();
				
		DependencyInstance[] insts = new DependencyInstance[lt.size()];
		int N = 0;
		for (DependencyInstance p : lt) {
			insts[N++] = p;
		}
		
		System.out.printf("%d [%d ms]%n", cnt, System.currentTimeMillis() - start);
	    
		return insts;
	}
    
    public DependencyInstance createInstance(DependencyReader reader) throws IOException 
    {
    	
    	DependencyInstance inst = reader.nextInstance();
    	if (inst == null) return null;
    	
    	inst.setInstIds(tagDictionary, wordDictionary, typeAlphabet, wordVecDictionary, coarseMap, conjWord, options.lang);
    			
	    //createFeatures(inst);
	    
	    return inst;
    }
    
    public void initFeatureAlphabets(DependencyInstance inst) 
    {
        int n = inst.length;
        
    	// word 
        for (int i = 0; i < n; ++i)
            createWordFeatures(inst, i);
    	
        int[] heads = inst.heads;
        int[] deplbids = inst.deplbids;
    	
        // 1st order arc
        for (int i = 0; i < n; ++i) {
    		
    		if (heads[i] == -1) continue;
    	     
    		int parent = heads[i];
    		createArcFeatures(inst, parent, i);	// arc features    		
    		if (options.learnLabel) {
    			int type = deplbids[i]; 
    			boolean toRight = parent < i;
    			createLabelFeatures(inst, parent, type, toRight, false);
    			createLabelFeatures(inst, i, type, toRight, true);
    		}
    	}
    	
        if (options.learningMode != LearningMode.Basic) {
        	
        	DependencyArcList arcLis = new DependencyArcList(heads);
    		
    		// 2nd order (h,m,s) & (m,s)
    		for (int h = 0; h < n; ++h) {
    			
    			int st = arcLis.startIndex(h);
    			int ed = arcLis.endIndex(h);
    			
    			for (int p = st; p+1 < ed; ++p) {
    				// mod and sib
    				int m = arcLis.get(p);
    				int s = arcLis.get(p+1);
    				
    				if (options.useCS) {
    					createTripsFeatureVector(inst, h, m, s);
    					createSibFeatureVector(inst, m, s/*, false*/);
    				}
					
					// gp-sibling
					int gp = heads[h];
					if (options.useGS && gp >= 0) {
						createGPSibFeatureVector(inst, gp, h, m, s);
					}
					
					// tri-sibling
					if (options.useTS && p + 2 < ed) {
						int s2 = arcLis.get(p + 2);
						createTriSibFeatureVector(inst, h, m, s, s2);
					}
					
					// parent, sibling and child
					if (options.usePSC) {
						// mod's child
						int mst = arcLis.startIndex(m);
						int med = arcLis.endIndex(m);
						
						for (int mp = mst; mp < med; ++mp) {
							int c = arcLis.get(mp);
							createPSCFeatureVector(inst, h, m, c, s);
						}
						
						// sib's child
						int sst = arcLis.startIndex(s);
						int sed = arcLis.endIndex(s);
						
						for (int sp = sst; sp < sed; ++sp) {
							int c = arcLis.get(sp);
							createPSCFeatureVector(inst, h, s, c, m);
						}
					}
    			}
    		}
			
			for (int m = 1; m < n; ++m) {
				int h = heads[m];
				
				Utils.Assert(h >= 0);
				
				// grandparent
				int gp = heads[h];
				if (options.useGP && gp != -1) {
					createGPCFeatureVector(inst, gp, h, m);
				}
				
				// head bigram
				if (options.useHB && m + 1 < n) {
					int h2 = heads[m + 1];
					Utils.Assert(h2 >= 0);
					
					createHeadBiFeatureVector(inst, m, h, h2);
				}
				
				// great-grandparent
				if (options.useGGP && gp != -1 && heads[gp] != -1) {
					int ggp = heads[gp];
					createGGPCFeatureVector(inst, ggp, gp, h, m);
				}
			}
			
			// global feature
			if (options.useHO) {
				FeatureVector fv = new FeatureVector(arcAlphabet.size());
				
				// non-proj
				for (int i = 0; i < n; ++i) {
					if (heads[i] == -1)
						continue;
					int num = getBinnedDistance(arcLis.nonproj[i]);
					createNonprojFeatureVector(inst, num, heads[i], i);
				}

				int[] toks = inst.formids;
				int[] pos = inst.postagids;
				int[] posA = inst.cpostagids;
				SpecialPos[] specialPos = inst.specialPos;
				int[] spanLeft = arcLis.left;
				int[] spanRight = arcLis.right;

				long code = 0;

				for (int i = 0; i < n; ++i) {
					// pp attachment
					if (SpecialPos.P == specialPos[i]) {
						int par = heads[i];
						int[] c = findPPArg(inst.heads, inst.specialPos, arcLis, i);
						for (int z = 0; z < c.length; ++z) {
							if (par != -1 && c[z] != -1) {
								createPPFeatureVector(inst, par, i, c[z]);
							}
						}
					}

					// conjunction pos
					if (SpecialPos.C == specialPos[i]) {
						int[] arg = findConjArg(arcLis, heads, i);
						int head = arg[0];
						int left = arg[1];
						int right = arg[2];
						if (left != -1 && right != -1 && left < right) {
							createCC1FeatureVector(inst, left, i, right);
							if (head != -1) {
								createCC2FeatureVector(inst, i, head, left);
								createCC2FeatureVector(inst, i, head, right);
							}
						}
					}

					// punc head
					if (SpecialPos.PNX == specialPos[i]) {
						int j = findPuncCounterpart(toks, i);
						if (j != -1 && heads[i] == heads[j])
							createPNXFeatureVector(inst, heads[i], i, j);
					}
				}

				int rb = getMSTRightBranch(specialPos, arcLis, 0, 0);
				
				code = createArcCodeP(Arc.RB, 0x0);
				addArcFeature(code, (double)rb / n, fv);
				
				for (int m = 1; m < n; ++m) {

					// child num
					int leftNum = 0;
					int rightNum = 0;
					int maxDigit = 64 - Arc.numArcFeatBits - 4;
					int maxChildStrNum = (maxDigit / tagNumBits) - 1;
					int childStrNum = 0;
					code = pos[m];
					
					int st = arcLis.startIndex(m);
					int ed = arcLis.endIndex(m);
					
					for (int j = st; j < ed; ++j) {
						int cid = arcLis.get(j);
						if (SpecialPos.PNX != specialPos[cid]) {
							if (cid < m && leftNum < GlobalFeatureData.MAX_CHILD_NUM)
								leftNum++;
							else if (cid > m && rightNum < GlobalFeatureData.MAX_CHILD_NUM)
								rightNum++;
							if (childStrNum < maxChildStrNum) {
								code = ((code << tagNumBits) | pos[cid]);
								childStrNum++;
							}
						}
					}
					code = ((code << Arc.numArcFeatBits) | Arc.CN_STR.ordinal()) << 4;
					addArcFeature(code, fv);

					createChildNumFeatureVector(inst, m, leftNum, rightNum);

					// span
					int end = spanRight[m] == n ? 1 : 0;
					int punc = (spanRight[m] < n && SpecialPos.PNX == specialPos[spanRight[m]]) ? 1 : 0;
					int bin = Math.min(GlobalFeatureData.MAX_SPAN_LENGTH, (spanRight[m] - spanLeft[m]));
					createSpanFeatureVector(inst, m, end, punc, bin);

					if (heads[m] != -1) {
						// neighbors
						int leftID = spanLeft[m] > 0 ? posA[spanLeft[m] - 1] : DependencyPipe.TOKEN_START;
						int rightID = spanRight[m] < n ? posA[spanRight[m]] : DependencyPipe.TOKEN_END;
						if (leftID > 0 && rightID > 0) {
							createNeighborFeatureVector(inst, heads[m], m, leftID, rightID);
						}
					}
				}
			}
        }		
    }
    
    
	/************************************************************************
	 * Region start #
	 * 
	 *  Functions that create feature vectors for arc structures in the
	 *  sentence. Arc structures could be 1-order arcs (i.e. parent-child),
	 *  2-order arcs (e.g. parent-siblings or grandparent-parent-child) and
	 *  so on.
	 *  
	 ************************************************************************/
    
    /**
     * Create 1st order feature vector of an dependency arc. 
     * 
     * This is an re-implementation of MST parser 1st order feature construction. 
     * There is slightly difference on constructions of morphology features and 
     * bigram features, in order to reduce redundancy.
     * 
     * @param inst 	the current sentence
     * @param h		index of the head
     * @param c		index of the modifier
     * @return
     */
    public FeatureVector createArcFeatures(DependencyInstance inst, int h, int c) 
    {
    	
    	int attDist = getBinnedDistance(h-c);
    	
    	FeatureVector fv = new FeatureVector(arcAlphabet.size());
    	
    	addBasic1OFeatures(fv, inst, h, c, attDist);
    	
    	addCore1OPosFeatures(fv, inst, h, c, attDist);
    		    		
    	addCore1OBigramFeatures(fv, inst.formids[h], inst.postagids[h], 
    			inst.formids[c], inst.postagids[c], attDist);
    	    		
		if (inst.lemmaids != null)
			addCore1OBigramFeatures(fv, inst.lemmaids[h], inst.postagids[h], 
					inst.lemmaids[c], inst.postagids[c], attDist);
		
		addCore1OBigramFeatures(fv, inst.formids[h], inst.cpostagids[h], 
    			inst.formids[c], inst.cpostagids[c], attDist);
		
		if (inst.lemmaids != null)
			addCore1OBigramFeatures(fv, inst.lemmaids[h], inst.cpostagids[h], 
					inst.lemmaids[c], inst.cpostagids[c], attDist);
    	
    	if (inst.featids[h] != null && inst.featids[c] != null) {
    		for (int i = 0, N = inst.featids[h].length; i < N; ++i)
    			for (int j = 0, M = inst.featids[c].length; j < M; ++j) {
    				
    				addCore1OBigramFeatures(fv, inst.formids[h], inst.featids[h][i], 
    						inst.formids[c], inst.featids[c][j], attDist);
    				
    				if (inst.lemmas != null)
    					addCore1OBigramFeatures(fv, inst.lemmaids[h], inst.featids[h][i], 
    							inst.lemmaids[c], inst.featids[c][j], attDist);
    			}
    	}
    			
    	return fv;
    }
    
    public void addBasic1OFeatures(FeatureVector fv, DependencyInstance inst, 
    		int h, int m, int attDist) 
    {
    	
    	long code = 0; 			// feature code
    	
    	int[] forms = inst.formids, lemmas = inst.lemmaids, postags = inst.postagids;
    	int[] cpostags = inst.cpostagids;
    	int[][] feats = inst.featids;
    	

    	code = createArcCodeW(CORE_HEAD_WORD, forms[h]);
    	addArcFeature(code, fv);
    	addArcFeature(code | attDist, fv);
    	    	    	
    	code = createArcCodeW(CORE_MOD_WORD, forms[m]);
    	addArcFeature(code, fv);
    	addArcFeature(code | attDist, fv);
    	
    	code = createArcCodeWW(HW_MW, forms[h], forms[m]);
    	addArcFeature(code, fv);
    	addArcFeature(code | attDist, fv);
    	
    	int pHF = h == 0 ? TOKEN_START : (h == m+1 ? TOKEN_MID : forms[h-1]);
    	int nHF = h == inst.length - 1 ? TOKEN_END : (h+1 == m ? TOKEN_MID : forms[h+1]);
    	int pMF = m == 0 ? TOKEN_START : (m == h+1 ? TOKEN_MID : forms[m-1]);
    	int nMF = m == inst.length - 1 ? TOKEN_END : (m+1 == h ? TOKEN_MID : forms[m+1]);
    	
    	code = createArcCodeW(CORE_HEAD_pWORD, pHF);
    	addArcFeature(code, fv);
    	addArcFeature(code | attDist, fv);
    	
    	code = createArcCodeW(CORE_HEAD_nWORD, nHF);
    	addArcFeature(code, fv);
    	addArcFeature(code | attDist, fv);
    	
    	code = createArcCodeW(CORE_MOD_pWORD, pMF);
    	addArcFeature(code, fv);
    	addArcFeature(code | attDist, fv);
    	
    	code = createArcCodeW(CORE_MOD_nWORD, nMF);
    	addArcFeature(code, fv);
    	addArcFeature(code | attDist, fv);
	
		
    	code = createArcCodeP(CORE_HEAD_POS, postags[h]);
    	addArcFeature(code, fv);
    	addArcFeature(code | attDist, fv);
    	
    	code = createArcCodeP(CORE_HEAD_POS, cpostags[h]);
    	addArcFeature(code, fv);
    	addArcFeature(code | attDist, fv);
    	
    	code = createArcCodeP(CORE_MOD_POS, postags[m]);
    	addArcFeature(code, fv);
    	addArcFeature(code | attDist, fv);
    	
    	code = createArcCodeP(CORE_MOD_POS, cpostags[m]);
    	addArcFeature(code, fv);
    	addArcFeature(code | attDist, fv);
    	
    	code = createArcCodePP(HP_MP, postags[h], postags[m]);
    	addArcFeature(code, fv);
    	addArcFeature(code | attDist, fv);
    	
    	code = createArcCodePP(HP_MP, cpostags[h], cpostags[m]);
    	addArcFeature(code, fv);
    	addArcFeature(code | attDist, fv);
    	
    	     	
    	if (lemmas != null) {
    		code = createArcCodeW(CORE_HEAD_WORD, lemmas[h]);
        	addArcFeature(code, fv);
        	addArcFeature(code | attDist, fv);
        	
    		code = createArcCodeW(CORE_MOD_WORD, lemmas[m]);
        	addArcFeature(code, fv);
        	addArcFeature(code | attDist, fv);
        	
        	code = createArcCodeWW(HW_MW, lemmas[h], lemmas[m]);
        	addArcFeature(code, fv);
        	addArcFeature(code | attDist, fv);
        	
	    	int pHL = h == 0 ? TOKEN_START : (h == m+1 ? TOKEN_MID : lemmas[h-1]);
	    	int nHL = h == inst.length - 1 ? TOKEN_END : (h+1 == m ? TOKEN_MID : lemmas[h+1]);
	    	int pML = m == 0 ? TOKEN_START : (m == h+1 ? TOKEN_MID : lemmas[m-1]);
	    	int nML = m == inst.length - 1 ? TOKEN_END : (m+1 == h ? TOKEN_MID : lemmas[m+1]);
	    	
	    	code = createArcCodeW(CORE_HEAD_pWORD, pHL);
	    	addArcFeature(code, fv);
	    	addArcFeature(code | attDist, fv);
	    	
	    	code = createArcCodeW(CORE_HEAD_nWORD, nHL);
	    	addArcFeature(code, fv);
	    	addArcFeature(code | attDist, fv);
	    	
	    	code = createArcCodeW(CORE_MOD_pWORD, pML);
	    	addArcFeature(code, fv);
	    	addArcFeature(code | attDist, fv);
	    	
	    	code = createArcCodeW(CORE_MOD_nWORD, nML);
	    	addArcFeature(code, fv);
	    	addArcFeature(code | attDist, fv);
    	}
    	
		if (feats[h] != null)
			for (int i = 0, N = feats[h].length; i < N; ++i) {
				code = createArcCodeP(CORE_HEAD_POS, feats[h][i]);
	        	addArcFeature(code, fv);
	        	addArcFeature(code | attDist, fv);
			}
		
		if (feats[m] != null)
			for (int i = 0, N = feats[m].length; i < N; ++i) {
				code = createArcCodeP(CORE_MOD_POS, feats[m][i]);
	        	addArcFeature(code, fv);
	        	addArcFeature(code | attDist, fv);
			}
		
		if (feats[h] != null && feats[m] != null) {
			for (int i = 0, N = feats[h].length; i < N; ++i)
				for (int j = 0, M = feats[m].length; j < M; ++j) {
			    	code = createArcCodePP(HP_MP, feats[h][i], feats[m][j]);
			    	addArcFeature(code, fv);
			    	addArcFeature(code | attDist, fv);
				}
		}
		
		if (wordVectors != null) {
			
			int wvid = inst.wordVecIds[h];
			double [] v = wvid > 0 ? wordVectors[wvid] : unknownWv;
			if (v != null) {
				for (int i = 0; i < v.length; ++i) {
					code = createArcCodeW(HEAD_EMB, i);
					addArcFeature(code, v[i], fv);
					addArcFeature(code | attDist, v[i], fv);
				}
			}
			
			wvid = inst.wordVecIds[m];
			v = wvid > 0 ? wordVectors[wvid] : unknownWv;
			if (v != null) {
				for (int i = 0; i < v.length; ++i) {
					code = createArcCodeW(MOD_EMB, i);
					addArcFeature(code, v[i], fv);
					addArcFeature(code | attDist, v[i], fv);
				}
			}
		}
    }
    
    public void addCore1OPosFeatures(FeatureVector fv, DependencyInstance inst, 
    		int h, int c, int attDist) 
    {  	
    	
    	int[] pos = inst.postagids;
    	int[] posA = inst.cpostagids;
	
    	int pHead = pos[h], pHeadA = posA[h];
    	int pMod = pos[c], pModA = posA[c];
    	int pHeadLeft = h > 0 ? (h-1 == c ? TOKEN_MID : pos[h-1]) : TOKEN_START;    	
    	int pModRight = c < pos.length-1 ? (c+1 == h ? TOKEN_MID : pos[c+1]) : TOKEN_END;
    	int pHeadRight = h < pos.length-1 ? (h+1 == c ? TOKEN_MID: pos[h+1]) : TOKEN_END;
    	int pModLeft = c > 0 ? (c-1 == h ? TOKEN_MID : pos[c-1]) : TOKEN_START;
    	int pHeadLeftA = h > 0 ? (h-1 == c ? TOKEN_MID : posA[h-1]) : TOKEN_START;    	
    	int pModRightA = c < posA.length-1 ? (c+1 == h ? TOKEN_MID : posA[c+1]) : TOKEN_END;
    	int pHeadRightA = h < posA.length-1 ? (h+1 == c ? TOKEN_MID: posA[h+1]) : TOKEN_END;
    	int pModLeftA = c > 0 ? (c-1 == h ? TOKEN_MID : posA[c-1]) : TOKEN_START;
    	
    	    	
    	long code = 0;
    	
    	// feature posR posMid posL
    	int small = h < c ? h : c;
    	int large = h > c ? h : c;
    	for(int i = small+1; i < large; i++) {    		
    		code = createArcCodePPP(HP_BP_MP, pHead, pos[i], pMod);
    		addArcFeature(code, fv);
    		addArcFeature(code | attDist, fv);
    		
    		code = createArcCodePPP(HP_BP_MP, pHeadA, posA[i], pModA);
    		addArcFeature(code, fv);
    		addArcFeature(code | attDist, fv);
    	}
    	
    	// feature posL-1 posL posR posR+1
    	code = createArcCodePPPP(HPp_HP_MP_MPn, pHeadLeft, pHead, pMod, pModRight);
		addArcFeature(code, fv);
		addArcFeature(code | attDist, fv);
		
    	code = createArcCodePPP(HP_MP_MPn, pHead, pMod, pModRight);
		addArcFeature(code, fv);
		addArcFeature(code | attDist, fv);
		
    	code = createArcCodePPP(HPp_HP_MP, pHeadLeft, pHead, pMod);
		addArcFeature(code, fv);
		addArcFeature(code | attDist, fv);
		
    	code = createArcCodePPP(HPp_MP_MPn, pHeadLeft, pMod, pModRight);
		addArcFeature(code, fv);
		addArcFeature(code | attDist, fv);
		
    	code = createArcCodePPP(HPp_HP_MPn, pHeadLeft, pHead, pModRight);
		addArcFeature(code, fv);
		addArcFeature(code | attDist, fv);

    	code = createArcCodePPPP(HPp_HP_MP_MPn, pHeadLeftA, pHeadA, pModA, pModRightA);
		addArcFeature(code, fv);
		addArcFeature(code | attDist, fv);
		
    	code = createArcCodePPP(HP_MP_MPn, pHeadA, pModA, pModRightA);
		addArcFeature(code, fv);
		addArcFeature(code | attDist, fv);
		
    	code = createArcCodePPP(HPp_HP_MP, pHeadLeftA, pHeadA, pModA);
		addArcFeature(code, fv);
		addArcFeature(code | attDist, fv);
		
    	code = createArcCodePPP(HPp_MP_MPn, pHeadLeftA, pModA, pModRightA);
		addArcFeature(code, fv);
		addArcFeature(code | attDist, fv);
		
    	code = createArcCodePPP(HPp_HP_MPn, pHeadLeftA, pHeadA, pModRightA);
		addArcFeature(code, fv);
		addArcFeature(code | attDist, fv);
		
    	
    	// feature posL posL+1 posR-1 posR
		code = createArcCodePPPP(HP_HPn_MPp_MP, pHead, pHeadRight, pModLeft, pMod);
		addArcFeature(code, fv);
		addArcFeature(code | attDist, fv);
		
		code = createArcCodePPP(HP_MPp_MP, pHead, pModLeft, pMod);
		addArcFeature(code, fv);
		addArcFeature(code | attDist, fv);
		
		code = createArcCodePPP(HP_HPn_MP, pHead, pHeadRight, pMod);
		addArcFeature(code, fv);
		addArcFeature(code | attDist, fv);
		
		code = createArcCodePPP(HPn_MPp_MP, pHeadRight, pModLeft, pMod);
		addArcFeature(code, fv);
		addArcFeature(code | attDist, fv);
		
		code = createArcCodePPP(HP_HPn_MPp, pHead, pHeadRight, pModLeft);
		addArcFeature(code, fv);
		addArcFeature(code | attDist, fv);
		
		code = createArcCodePPPP(HP_HPn_MPp_MP, pHeadA, pHeadRightA, pModLeftA, pModA);
		addArcFeature(code, fv);
		addArcFeature(code | attDist, fv);
		
		code = createArcCodePPP(HP_MPp_MP, pHeadA, pModLeftA, pModA);
		addArcFeature(code, fv);
		addArcFeature(code | attDist, fv);
		
		code = createArcCodePPP(HP_HPn_MP, pHeadA, pHeadRightA, pModA);
		addArcFeature(code, fv);
		addArcFeature(code | attDist, fv);
		
		code = createArcCodePPP(HPn_MPp_MP, pHeadRightA, pModLeftA, pModA);
		addArcFeature(code, fv);
		addArcFeature(code | attDist, fv);
		
		code = createArcCodePPP(HP_HPn_MPp, pHeadA, pHeadRightA, pModLeftA);
		addArcFeature(code, fv);
		addArcFeature(code | attDist, fv);
	
    	
		// feature posL-1 posL posR-1 posR
		// feature posL posL+1 posR posR+1
		code = createArcCodePPPP(HPp_HP_MPp_MP, pHeadLeft, pHead, pModLeft, pMod);
		addArcFeature(code, fv);
		addArcFeature(code | attDist, fv);
		
		code = createArcCodePPPP(HP_HPn_MP_MPn, pHead, pHeadRight, pMod, pModRight);
		addArcFeature(code, fv);
		addArcFeature(code | attDist, fv);
		
		code = createArcCodePPPP(HPp_HP_MPp_MP, pHeadLeftA, pHeadA, pModLeftA, pModA);
		addArcFeature(code, fv);
		addArcFeature(code | attDist, fv);
		
		code = createArcCodePPPP(HP_HPn_MP_MPn, pHeadA, pHeadRightA, pModA, pModRightA);
		addArcFeature(code, fv);
		addArcFeature(code | attDist, fv);
		
    }

    public void addCore1OBigramFeatures(FeatureVector fv, int head, int headP, 
    		int mod, int modP, int attDist) 
    {
    	
    	long code = 0;
    	
    	code = createArcCodeWWPP(HW_MW_HP_MP, head, mod, headP, modP);
    	addArcFeature(code, fv);
    	addArcFeature(code | attDist, fv);
    	
    	code = createArcCodeWPP(MW_HP_MP, mod, headP, modP);
    	addArcFeature(code, fv);
    	addArcFeature(code | attDist, fv);
    	
    	code = createArcCodeWPP(HW_HP_MP, head, headP, modP);
    	addArcFeature(code, fv);
    	addArcFeature(code | attDist, fv);
    	
    	code = createArcCodeWP(MW_HP, mod, headP);
    	addArcFeature(code, fv);
    	addArcFeature(code | attDist, fv);
    	
    	code = createArcCodeWP(HW_MP, head, modP);
    	addArcFeature(code, fv);
    	addArcFeature(code | attDist, fv);
    	    	
    	code = createArcCodeWP(HW_HP, head, headP);
    	addArcFeature(code, fv);
    	addArcFeature(code | attDist, fv);
    	
    	code = createArcCodeWP(MW_MP, mod, modP);
    	addArcFeature(code, fv);
    	addArcFeature(code | attDist, fv);
      
    }
    
    /************************************************************************
     *  Region end #
     ************************************************************************/
    
    
    
    /************************************************************************
     * Region start #
     * 
     *  Functions that create feature vectors of a specific word in the 
     *  sentence
     *  
     ************************************************************************/
    
    public FeatureVector createWordFeatures(DependencyInstance inst, int i) 
    {
    	
    	int[] pos = inst.postagids;
        int[] toks = inst.formids;
        int[][] feats = inst.featids;
        
        int w0 = toks[i];
        int l0 = inst.lemmaids == null ? 0 : inst.lemmaids[i];
        
        FeatureVector fv = new FeatureVector(wordAlphabet.size());
    	
    	long code = 0;
        
    	code = createWordCodeP(WORDFV_BIAS, 0);
    	addWordFeature(code, fv);

    	code = createWordCodeW(WORDFV_W0, w0);
    	addWordFeature(code, fv);
    	
    	int Wp = i == 0 ? TOKEN_START : toks[i-1];
    	int Wn = i == inst.length - 1 ? TOKEN_END : toks[i+1];
    		    	
    	code = createWordCodeW(WORDFV_Wp, Wp);
    	addWordFeature(code, fv);
    	
    	code = createWordCodeW(WORDFV_Wn, Wn);
    	addWordFeature(code, fv);

    	
		if (l0 != 0) {
    		code = createWordCodeW(WORDFV_W0, l0);
    		addWordFeature(code, fv);
    		
	    	int Lp = i == 0 ? TOKEN_START : inst.lemmaids[i-1];
	    	int Ln = i == inst.length - 1 ? TOKEN_END : inst.lemmaids[i+1];
	    		    	
	    	code = createWordCodeW(WORDFV_Wp, Lp);
	    	addWordFeature(code, fv);
	    	
	    	code = createWordCodeW(WORDFV_Wn, Ln);
	    	addWordFeature(code, fv);
		}
		
		if (feats[i] != null) {
    		for (int u = 0; u < feats[i].length; ++u) {
    			int f = feats[i][u];
    			
    			code = createWordCodeP(WORDFV_P0, f);
    			addWordFeature(code, fv);
    			
                if (l0 != 0) {
                	code = createWordCodeWP(WORDFV_W0P0, l0, f);
                	addWordFeature(code, fv);
                }
                
            }
		}
			
        int p0 = pos[i];
    	int pLeft = i > 0 ? pos[i-1] : TOKEN_START;
    	int pRight = i < pos.length-1 ? pos[i+1] : TOKEN_END;
    	
    	code = createWordCodeP(WORDFV_P0, p0);
    	addWordFeature(code, fv);
    	code = createWordCodeP(WORDFV_Pp, pLeft);
    	addWordFeature(code, fv);
    	code = createWordCodeP(WORDFV_Pn, pRight);
    	addWordFeature(code, fv);
    	code = createWordCodePP(WORDFV_PpP0, pLeft, p0);
    	addWordFeature(code, fv);
    	code = createWordCodePP(WORDFV_P0Pn, p0, pRight);
    	addWordFeature(code, fv);
    	code = createWordCodePPP(WORDFV_PpP0Pn, pLeft, p0, pRight);
    	addWordFeature(code, fv);
    		    	
		if (l0 != 0) {
    		code = createWordCodeWP(WORDFV_W0P0, l0, p0);
    		addWordFeature(code, fv);
		}
    	    	
    	if (wordVectors != null) {
    		addWordVectorFeatures(inst, i, 0, fv);
    		addWordVectorFeatures(inst, i, -1, fv);
    		addWordVectorFeatures(inst, i, 1, fv);	
    	}
    	
    	return fv;
    }
    
    public void addWordVectorFeatures(DependencyInstance inst, int i, int dis, FeatureVector fv) {
    	
    	int d = getBinnedDistance(dis);
    	double [] v = unknownWv;
    	int pos = i + dis;
    	
    	if (pos >= 0 && pos < inst.length) {
    		int wvid = inst.wordVecIds[pos];
    		if (wvid > 0) v = wordVectors[wvid];
    	}
    	
		//if (v == unknownWv) ++wvMiss; else ++wvHit;
		
		if (v != null) {
			for (int j = 0; j < v.length; ++j) {
				long code = createWordCodeW(WORDFV_EMB, j);
				addWordFeature(code | d, v[j], fv);
			}
		}
    }

    /************************************************************************
     *  Region end #
     ************************************************************************/
    
    
    
    /************************************************************************
     * Region start #
     * 
     *  Functions that create feature vectors for labeled arcs
     *  
     ************************************************************************/
    
    public FeatureVector createLabelFeatures(DependencyInstance inst, int word,
    		int type, boolean toRight, boolean isChild) 
    {
    	
    	FeatureVector fv = new FeatureVector(arcAlphabet.size());
    	if (!options.learnLabel) return fv;
    	    	
    	int att = 1;
    	if (toRight) att |= 2;
    	if (isChild) att |= 4;
    	
    	int[] toks = inst.formids;
    	int[] pos = inst.postagids;
    	
    	int w = toks[word];
    	
    	long code = 0;
    	
    	code = createArcCodeP(CORE_LABEL_NTS1, type);
		addArcFeature(code, fv);
		addArcFeature(code | att, fv);		
				
    	int wP = pos[word];
    	int wPm1 = word > 0 ? pos[word-1] : TOKEN_START;
    	int wPp1 = word < pos.length-1 ? pos[word+1] : TOKEN_END;
    	
		code = createArcCodeWPP(CORE_LABEL_NTH, w, wP, type);
		addArcFeature(code, fv);
		addArcFeature(code | att, fv);	
		
		code = createArcCodePP(CORE_LABEL_NTI, wP, type);
		addArcFeature(code, fv);
		addArcFeature(code | att, fv);	
		
		code = createArcCodePPP(CORE_LABEL_NTIA, wPm1, wP, type);
		addArcFeature(code, fv);
		addArcFeature(code | att, fv);	
		
		code = createArcCodePPP(CORE_LABEL_NTIB, wP, wPp1, type);
		addArcFeature(code, fv);
		addArcFeature(code | att, fv);	
		
		code = createArcCodePPPP(CORE_LABEL_NTIC, wPm1, wP, wPp1, type);
		addArcFeature(code, fv);
		addArcFeature(code | att, fv);	
		
		code = createArcCodeWP(CORE_LABEL_NTJ, w, type);
		addArcFeature(code, fv);
		addArcFeature(code | att, fv);	
    	
    	return fv;
    }
    
    public FeatureVector createTripsFeatureVector(DependencyInstance inst, int par,
    		int ch1, int ch2) {

    	FeatureVector fv = new FeatureVector(arcAlphabet.size());
    	
    	int[] pos = inst.postagids;
    	int[] posA = inst.cpostagids;

    	// ch1 is always the closes to par
    	int dirFlag = ((((par < ch1 ? 0 : 1) << 1) | (par < ch2 ? 0 : 1)) << 1) | 1;

    	int HP = pos[par];
    	int SP = ch1 == par ? TOKEN_START : pos[ch1];
    	int MP = pos[ch2];
    	int HC = posA[par];
    	int SC = ch1 == par ? TOKEN_START : posA[ch1];
    	int MC  = posA[ch2];

    	long code = 0;

    	code = createArcCodePPP(HP_SP_MP, HP, SP, MP);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodePPP(HC_SC_MC, HC, SC, MC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	addTurboSib(inst, par, ch1, ch2, dirFlag, fv);
    	
    	return fv;
    }

    void addTurboSib(DependencyInstance inst, int par, int ch1, int ch2, int dirFlag, FeatureVector fv) 
    {
    	int[] posA = inst.cpostagids;
    	//int[] lemma = inst.lemmaids;
    	int[] lemma = inst.lemmaids != null ? inst.lemmaids : inst.formids;
    	int len = inst.length;

    	int HC = posA[par];
    	int SC = ch1 == par ? TOKEN_START : posA[ch1];
    	int MC = posA[ch2];
    	
    	int pHC = par > 0 ? posA[par - 1] : TOKEN_START;
    	int nHC = par < len - 1 ? posA[par + 1] : TOKEN_END;
    	int pSC = ch1 > 0 ? posA[ch1 - 1] : TOKEN_START;
    	int nSC = ch1 < len - 1 ? posA[ch1 + 1] : TOKEN_END;
    	int pMC = ch2 > 0 ? posA[ch2 - 1] : TOKEN_START;
    	int nMC = ch2 < len - 1 ? posA[ch2 + 1] : TOKEN_END;

    	long code = 0;

    	// CCC
    	code = createArcCodePPPP(pHC_HC_SC_MC, pHC, HC, SC, MC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodePPPP(HC_nHC_SC_MC, HC, nHC, SC, MC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodePPPP(HC_pSC_SC_MC, HC, pSC, SC, MC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodePPPP(HC_SC_nSC_MC, HC, SC, nSC, MC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodePPPP(HC_SC_pMC_MC, HC, SC, pMC, MC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodePPPP(HC_SC_MC_nMC, HC, SC, MC, nMC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	int HL = lemma[par];
    	int SL = ch1 == par ? TOKEN_START : lemma[ch1];
    	int ML = lemma[ch2];
    	
    	// LCC
    	code = createArcCodeWPPP(pHC_HL_SC_MC, HL, pHC, SC, MC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodeWPPP(HL_nHC_SC_MC, HL, nHC, SC, MC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodeWPPP(HL_pSC_SC_MC, HL, pSC, SC, MC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodeWPPP(HL_SC_nSC_MC, HL, SC, nSC, MC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodeWPPP(HL_SC_pMC_MC, HL, SC, pMC, MC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodeWPPP(HL_SC_MC_nMC, HL, SC, MC, nMC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	// CLC
    	code = createArcCodeWPPP(pHC_HC_SL_MC, SL, pHC, HC, MC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodeWPPP(HC_nHC_SL_MC, SL, HC, nHC, MC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodeWPPP(HC_pSC_SL_MC, SL, HC, pSC, MC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodeWPPP(HC_SL_nSC_MC, SL, HC, nSC, MC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodeWPPP(HC_SL_pMC_MC, SL, HC, pMC, MC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodeWPPP(HC_SL_MC_nMC, SL, HC, MC, nMC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	// CCL
    	code = createArcCodeWPPP(pHC_HC_SC_ML, ML, pHC, HC, SC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodeWPPP(HC_nHC_SC_ML, ML, HC, nHC, SC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodeWPPP(HC_pSC_SC_ML, ML, HC, pSC, SC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodeWPPP(HC_SC_nSC_ML, ML, HC, SC, nSC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodeWPPP(HC_SC_pMC_ML, ML, HC, SC, pMC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodeWPPP(HC_SC_ML_nMC, ML, HC, SC, nMC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);
    	
    	code = createArcCodePPPPP(HC_MC_SC_pHC_pMC, HC, MC, SC, pHC, pMC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodePPPPP(HC_MC_SC_pHC_pSC, HC, MC, SC, pHC, pSC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodePPPPP(HC_MC_SC_pMC_pSC, HC, MC, SC, pMC, pSC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodePPPPP(HC_MC_SC_nHC_nMC, HC, MC, SC, nHC, nMC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodePPPPP(HC_MC_SC_nHC_nSC, HC, MC, SC, nHC, nSC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodePPPPP(HC_MC_SC_nMC_nSC, HC, MC, SC, nMC, nSC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodePPPPP(HC_MC_SC_pHC_nMC, HC, MC, SC, pHC, nMC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodePPPPP(HC_MC_SC_pHC_nSC, HC, MC, SC, pHC, nSC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodePPPPP(HC_MC_SC_pMC_nSC, HC, MC, SC, pMC, nSC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodePPPPP(HC_MC_SC_nHC_pMC, HC, MC, SC, nHC, pMC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodePPPPP(HC_MC_SC_nHC_pSC, HC, MC, SC, nHC, pSC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodePPPPP(HC_MC_SC_nMC_pSC, HC, MC, SC, nMC, pSC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);
    }

    public FeatureVector createSibFeatureVector(DependencyInstance inst, int ch1, int ch2/*, boolean isST*/) {
    	
    	FeatureVector fv = new FeatureVector(arcAlphabet.size());

    	int[] pos = inst.postagids;
    	int[] posA = inst.cpostagids;
    	int[] toks = inst.formids;
    	//int[] lemma = inst.lemmaids;
    	int[] lemma = inst.lemmaids != null ? inst.lemmaids : inst.formids;
    	
    	// ch1 is always the closes to par

    	int SP = /*isST ? TOKEN_START :*/ pos[ch1];
    	int MP = pos[ch2];
    	int SW = /*isST ? TOKEN_START :*/ toks[ch1];
    	int MW = toks[ch2];
    	int SC = /*isST ? TOKEN_START :*/ posA[ch1];
    	int MC = posA[ch2];


    	//Utils.Assert(ch1 < ch2);
    	int flag = getBinnedDistance(ch1 - ch2);

    	long code = 0;

    	code = createArcCodePP(SP_MP, SP, MP);
    	addArcFeature(code, fv);
    	addArcFeature(code | flag, fv);

    	code = createArcCodeWW(SW_MW, SW, MW);
    	addArcFeature(code, fv);
    	addArcFeature(code | flag, fv);

    	code = createArcCodeWP(SW_MP, SW, MP);
    	addArcFeature(code, fv);
    	addArcFeature(code | flag, fv);

    	code = createArcCodeWP(SP_MW, MW, SP);
    	addArcFeature(code, fv);
    	addArcFeature(code | flag, fv);

    	code = createArcCodePP(SC_MC, SC, MC);
    	addArcFeature(code, fv);
    	addArcFeature(code | flag, fv);
    	
    		
    	int SL = /*isST ? TOKEN_START :*/ lemma[ch1];
    	int ML = lemma[ch2];
    	
    	code = createArcCodeWW(SL_ML, SL, ML);
    	addArcFeature(code, fv);
    	addArcFeature(code | flag, fv);

    	code = createArcCodeWP(SL_MC, SL, MC);
    	addArcFeature(code, fv);
    	addArcFeature(code | flag, fv);

    	code = createArcCodeWP(SC_ML, ML, SC);
    	addArcFeature(code, fv);
    	addArcFeature(code | flag, fv);
	    	
    	
    	return fv;
    }

    public FeatureVector createHeadBiFeatureVector(DependencyInstance inst, int ch, int par1, int par2) {
    	FeatureVector fv = new FeatureVector(arcAlphabet.size());
    	
    	int[] pos = inst.postagids;
    	int[] posA = inst.cpostagids;

    	// exactly 16 combination, so need one more template
    	int flag = 0;
    	if (par1 == par2)
    		flag = 1;
    	else if (par1 == ch + 1)
    		flag = 2;
    	else if (par2 == ch)
    		flag = 3;

    	int dirFlag = flag;
    	dirFlag = (dirFlag << 1) | (par1 < ch ? 1 : 0);
    	dirFlag = (dirFlag << 1) | (par2 < ch + 1 ? 1 : 0);

    	long code = 0;

    	int H1P = pos[par1];
    	int H2P = pos[par2];
    	int M1P = pos[ch];
    	int M2P = pos[ch + 1];
    	int H1C = posA[par1];
    	int H2C = posA[par2];
    	int M1C = posA[ch];
    	int M2C = posA[ch + 1];

    	code = createArcCodePPPP(H1P_H2P_M1P_M2P, H1P, H2P, M1P, M2P);
    	addArcFeature(code | flag, fv);
    	code = createArcCodePPPP(H1P_H2P_M1P_M2P_DIR, H1P, H2P, M1P, M2P);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodePPPP(H1C_H2C_M1C_M2C, H1C, H2C, M1C, M2C);
    	addArcFeature(code | flag, fv);
    	code = createArcCodePPPP(H1C_H2C_M1C_M2C_DIR, H1C, H2C, M1C, M2C);
    	addArcFeature(code | dirFlag, fv);
    	
    	return fv;
    }

    public FeatureVector createGPCFeatureVector(DependencyInstance inst, int gp, int par, int c) {

    	FeatureVector fv = new FeatureVector(arcAlphabet.size());

    	int[] pos = inst.postagids;
    	int[] posA = inst.cpostagids;
    	//int[] lemma = inst.lemmaids;
    	int[] lemma = inst.lemmaids != null ? inst.lemmaids : inst.formids;
    	
    	int flag = (((((gp < par ? 0 : 1) << 1) | (par < c ? 0 : 1)) << 1) | 1);

    	int GP = pos[gp];
    	int HP = pos[par];
    	int MP = pos[c];
    	int GC = posA[gp];
    	int HC = posA[par];
    	int MC = posA[c];
    	long code = 0;

    	code = createArcCodePPP(GP_HP_MP, GP, HP, MP);
    	addArcFeature(code, fv);
    	addArcFeature(code | flag, fv);

    	code = createArcCodePPP(GC_HC_MC, GC, HC, MC);
    	addArcFeature(code, fv);
    	addArcFeature(code | flag, fv);
    
        int GL = lemma[gp];
        int HL = lemma[par];
        int ML = lemma[c];

        code = createArcCodeWPP(GL_HC_MC, GL, HC, MC);
        addArcFeature(code, fv);
        addArcFeature(code | flag, fv);

        code = createArcCodeWPP(GC_HL_MC, HL, GC, MC);
        addArcFeature(code, fv);
        addArcFeature(code | flag, fv);

        code = createArcCodeWPP(GC_HC_ML, ML, GC, HC);
        addArcFeature(code, fv);
        addArcFeature(code | flag, fv);

    	addTurboGPC(inst, gp, par, c, flag, fv);
    	
    	return fv;
    }

    void addTurboGPC(DependencyInstance inst, int gp, int par, int c, int dirFlag, FeatureVector fv) {
    	int[] posA = inst.cpostagids;
    	//int[] lemma = inst.lemmaids;
    	int[] lemma = inst.lemmaids != null ? inst.lemmaids : inst.formids;
    	int len = posA.length;

    	int GC = posA[gp];
    	int HC = posA[par];
    	int MC = posA[c];

    	int pGC = gp > 0 ? posA[gp - 1] : TOKEN_START;
    	int nGC = gp < len - 1 ? posA[gp + 1] : TOKEN_END;
    	int pHC = par > 0 ? posA[par - 1] : TOKEN_START;
    	int nHC = par < len - 1 ? posA[par + 1] : TOKEN_END;
    	int pMC = c > 0 ? posA[c - 1] : TOKEN_START;
    	int nMC = c < len - 1 ? posA[c + 1] : TOKEN_END;

    	long code = 0;

    	// CCC
    	code = createArcCodePPPP(pGC_GC_HC_MC, pGC, GC, HC, MC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodePPPP(GC_nGC_HC_MC, GC, nGC, HC, MC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodePPPP(GC_pHC_HC_MC, GC, pHC, HC, MC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodePPPP(GC_HC_nHC_MC, GC, HC, nHC, MC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodePPPP(GC_HC_pMC_MC, GC, HC, pMC, MC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodePPPP(GC_HC_MC_nMC, GC, HC, MC, nMC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodePPPPP(GC_HC_MC_pGC_pHC, GC, HC, MC, pGC, pHC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodePPPPP(GC_HC_MC_pGC_pMC, GC, HC, MC , pGC, pMC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodePPPPP(GC_HC_MC_pHC_pMC, GC, HC, MC, pHC, pMC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodePPPPP(GC_HC_MC_nGC_nHC, GC, HC, MC, nGC, nHC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodePPPPP(GC_HC_MC_nGC_nMC, GC, HC, MC, nGC, nMC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodePPPPP(GC_HC_MC_nHC_nMC, GC, HC, MC, nHC, nMC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodePPPPP(GC_HC_MC_pGC_nHC, GC, HC, MC, pGC, nHC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodePPPPP(GC_HC_MC_pGC_nMC, GC, HC, MC, pGC, nMC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodePPPPP(GC_HC_MC_pHC_nMC, GC, HC, MC, pHC, nMC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodePPPPP(GC_HC_MC_nGC_pHC, GC, HC, MC, nGC, pHC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodePPPPP(GC_HC_MC_nGC_pMC, GC, HC, MC, nGC, pMC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodePPPPP(GC_HC_MC_nHC_pMC, GC, HC, MC, nHC, pMC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodePP(GC_HC, GC, HC);
    	addArcFeature(code, fv);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodePP(GC_MC, GC, MC);
    	addArcFeature(code | dirFlag, fv);

    	code = createArcCodePP(HC_MC, HC, MC);
    	addArcFeature(code | dirFlag, fv);
        
        int GL = lemma[gp];
        int HL = lemma[par];
        int ML = lemma[c];

        // LCC
        code = createArcCodeWPPP(pGC_GL_HC_MC, GL, pGC, HC, MC);
        addArcFeature(code, fv);
        addArcFeature(code | dirFlag, fv);

        code = createArcCodeWPPP(GL_nGC_HC_MC, GL, nGC, HC, MC);
        addArcFeature(code, fv);
        addArcFeature(code | dirFlag, fv);

        code = createArcCodeWPPP(GL_pHC_HC_MC, GL, pHC, HC, MC);
        addArcFeature(code, fv);
        addArcFeature(code | dirFlag, fv);

        code = createArcCodeWPPP(GL_HC_nHC_MC, GL, HC, nHC, MC);
        addArcFeature(code, fv);
        addArcFeature(code | dirFlag, fv);

        code = createArcCodeWPPP(GL_HC_pMC_MC, GL, HC, pMC, MC);
        addArcFeature(code, fv);
        addArcFeature(code | dirFlag, fv);

        code = createArcCodeWPPP(GL_HC_MC_nMC, GL, HC, MC, nMC);
        addArcFeature(code, fv);
        addArcFeature(code | dirFlag, fv);

        // CLC
        code = createArcCodeWPPP(pGC_GC_HL_MC, HL, pGC, GC, MC);
        addArcFeature(code, fv);
        addArcFeature(code | dirFlag, fv);

        code = createArcCodeWPPP(GC_nGC_HL_MC, HL, GC, nGC, MC);
        addArcFeature(code, fv);
        addArcFeature(code | dirFlag, fv);

        code = createArcCodeWPPP(GC_pHC_HL_MC, HL, GC, pHC, MC);
        addArcFeature(code, fv);
        addArcFeature(code | dirFlag, fv);

        code = createArcCodeWPPP(GC_HL_nHC_MC, HL, GC, nHC, MC);
        addArcFeature(code, fv);
        addArcFeature(code | dirFlag, fv);

        code = createArcCodeWPPP(GC_HL_pMC_MC, HL, GC, pMC, MC);
        addArcFeature(code, fv);
        addArcFeature(code | dirFlag, fv);

        code = createArcCodeWPPP(GC_HL_MC_nMC, HL, GC, MC, nMC);
        addArcFeature(code, fv);
        addArcFeature(code | dirFlag, fv);

        // CCL
        code = createArcCodeWPPP(pGC_GC_HC_ML, ML, pGC, GC, HC);
        addArcFeature(code, fv);
        addArcFeature(code | dirFlag, fv);

        code = createArcCodeWPPP(GC_nGC_HC_ML, ML, GC, nGC, HC);
        addArcFeature(code, fv);
        addArcFeature(code | dirFlag, fv);

        code = createArcCodeWPPP(GC_pHC_HC_ML, ML, GC, pHC, HC);
        addArcFeature(code, fv);
        addArcFeature(code | dirFlag, fv);

        code = createArcCodeWPPP(GC_HC_nHC_ML, ML, GC, HC, nHC);
        addArcFeature(code, fv);
        addArcFeature(code | dirFlag, fv);

        code = createArcCodeWPPP(GC_HC_pMC_ML, ML, GC, HC, pMC);
        addArcFeature(code, fv);
        addArcFeature(code | dirFlag, fv);

        code = createArcCodeWPPP(GC_HC_ML_nMC, ML, GC, HC, nMC);
        addArcFeature(code, fv);
        addArcFeature(code | dirFlag, fv);

        code = createArcCodeWWP(GL_HL_MC, GL, HL, MC);
        addArcFeature(code, fv);
        addArcFeature(code | dirFlag, fv);

        code = createArcCodeWWP(GL_HC_ML, GL, ML, HC);
        addArcFeature(code, fv);
        addArcFeature(code | dirFlag, fv);

        code = createArcCodeWWP(GC_HL_ML, HL, ML, GC);
        addArcFeature(code, fv);
        addArcFeature(code | dirFlag, fv);

        //code = createArcCodeWWW(GL_HL_ML, GL, HL, ML);
        //addCode(TemplateType::TThirdOrder, code, fv);

        code = createArcCodeWP(GL_HC, GL, HC);
        addArcFeature(code, fv);
        addArcFeature(code | dirFlag, fv);

        code = createArcCodeWP(GC_HL, HL, GC);
        addArcFeature(code, fv);
        addArcFeature(code | dirFlag, fv);

        code = createArcCodeWW(GL_HL, GL, HL);
        addArcFeature(code, fv);
        addArcFeature(code | dirFlag, fv);

        code = createArcCodeWP(GL_MC, GL, MC);
        addArcFeature(code | dirFlag, fv);

        code = createArcCodeWP(GC_ML, ML, GC);
        addArcFeature(code | dirFlag, fv);

        code = createArcCodeWW(GL_ML, GL, ML);
        addArcFeature(code | dirFlag, fv);

        code = createArcCodeWP(HL_MC, HL, MC);
        addArcFeature(code | dirFlag, fv);

        code = createArcCodeWP(HC_ML, ML, HC);
        addArcFeature(code | dirFlag, fv);

        code = createArcCodeWW(HL_ML, HL, ML);
        addArcFeature(code | dirFlag, fv);
    }

    /************************************************************************
     *  Region end #
     ************************************************************************/
    
    
    /************************************************************************
     * Region start #
     * 
     *  Functions that create 3rd order feature vectors
     *  
     ************************************************************************/

    public FeatureVector createGPSibFeatureVector(DependencyInstance inst, int par, int arg, int prev, int curr) {
    	FeatureVector fv = new FeatureVector(arcAlphabet.size());

    	int[] posA = inst.cpostagids;
        //int[] lemma = inst.lemmaids;
        int[] lemma = inst.lemmaids != null ? inst.lemmaids : inst.formids;
    	
        int flag = par < arg ? 0 : 1;					// bit 1
    	flag = (flag << 1) | (arg < prev ? 0 : 1);		// bit 2
    	flag = (flag << 1) | (arg < curr ? 0 : 1);		// bit 3
    	flag = (flag << 1) | 1;							// bit 4

    	int GC = posA[par];
    	int HC = posA[arg];
    	int SC = posA[prev];
    	int MC = posA[curr];
    	long code = 0;

    	code = createArcCodePPPP(GC_HC_MC_SC, GC, HC, SC, MC);
    	addArcFeature(code, fv);
    	addArcFeature(code | flag, fv);
    
        int GL = lemma[par];
        int HL = lemma[arg];
        int SL = lemma[prev];
        int ML = lemma[curr];

        code = createArcCodeWPPP(GL_HC_MC_SC, GL, HC, SC, MC);
        addArcFeature(code, fv);
        addArcFeature(code | flag, fv);

        code = createArcCodeWPPP(GC_HL_MC_SC, HL, GC, SC, MC);
        addArcFeature(code, fv);
        addArcFeature(code | flag, fv);

        code = createArcCodeWPPP(GC_HC_ML_SC, ML, GC, HC, SC);
        addArcFeature(code, fv);
        addArcFeature(code | flag, fv);

        code = createArcCodeWPPP(GC_HC_MC_SL, SL, GC, HC, MC);
        addArcFeature(code, fv);
        addArcFeature(code | flag, fv);

    	return fv;
    }

    public FeatureVector createTriSibFeatureVector(DependencyInstance inst, int arg, int prev, int curr, int next) {
    	FeatureVector fv = new FeatureVector(arcAlphabet.size());

    	int[] posA = inst.cpostagids;
    	//int[] lemma = inst.lemmaids;
    	int[] lemma = inst.lemmaids != null ? inst.lemmaids : inst.formids;
    	
    	int flag = arg < prev ? 0 : 1;					// bit 1
    	flag = (flag << 1) | (arg < curr ? 0 : 1);		// bit 2
    	flag = (flag << 1) | (arg < next ? 0 : 1);		// bit 3
    	flag = (flag << 1) | 1;							// bit 4

    	int HC = posA[arg];
    	int PC = posA[prev];
    	int MC = posA[curr];
    	int NC = posA[next];
    	long code = 0;

    	code = createArcCodePPPP(HC_PC_MC_NC, HC, PC, MC, NC);
    	addArcFeature(code, fv);
    	addArcFeature(code | flag, fv);

    	code = createArcCodePPP(HC_PC_NC, HC, PC, NC);
    	addArcFeature(code, fv);
    	addArcFeature(code | flag, fv);

    	code = createArcCodePPP(PC_MC_NC, PC, MC, NC);
    	addArcFeature(code, fv);
    	addArcFeature(code | flag, fv);

    	code = createArcCodePP(PC_NC, PC, NC);
    	addArcFeature(code, fv);
    	addArcFeature(code | flag, fv);
        
        int HL = lemma[arg];
        int PL = lemma[prev];
        int ML = lemma[curr];
        int NL = lemma[next];

        code = createArcCodeWPPP(HL_PC_MC_NC, HL, PC, MC, NC);
        addArcFeature(code, fv);
        addArcFeature(code | flag, fv);

        code = createArcCodeWPPP(HC_PL_MC_NC, PL, HC, MC, NC);
        addArcFeature(code, fv);
        addArcFeature(code | flag, fv);

        code = createArcCodeWPPP(HC_PC_ML_NC, ML, HC, PC, NC);
        addArcFeature(code, fv);
        addArcFeature(code | flag, fv);

        code = createArcCodeWPPP(HC_PC_MC_NL, NL, HC, PC, MC);
        addArcFeature(code, fv);
        addArcFeature(code | flag, fv);

        code = createArcCodeWPP(HL_PC_NC, HL, PC, NC);
        addArcFeature(code, fv);
        addArcFeature(code | flag, fv);

        code = createArcCodeWPP(HC_PL_NC, PL, HC, NC);
        addArcFeature(code, fv);
        addArcFeature(code | flag, fv);

        code = createArcCodeWPP(HC_PC_NL, NL, HC, PC);
        addArcFeature(code, fv);
        addArcFeature(code | flag, fv);

        code = createArcCodeWPP(PL_MC_NC, PL, MC, NC);
        addArcFeature(code, fv);
        addArcFeature(code | flag, fv);

        code = createArcCodeWPP(PC_ML_NC, ML, PC, NC);
        addArcFeature(code, fv);
        addArcFeature(code | flag, fv);

        code = createArcCodeWPP(PC_MC_NL, NL, PC, MC);
        addArcFeature(code, fv);
        addArcFeature(code | flag, fv);

        code = createArcCodeWP(PL_NC, PL, NC);
        addArcFeature(code, fv);
        addArcFeature(code | flag, fv);

        code = createArcCodeWP(PC_NL, NL, PC);
        addArcFeature(code, fv);
        addArcFeature(code | flag, fv);
            
    	return fv;
    }

    public FeatureVector createGGPCFeatureVector(DependencyInstance inst, int ggp, int gp, int par, int c) {
    	FeatureVector fv = new FeatureVector(arcAlphabet.size());
    	
    	int[] posA = inst.cpostagids;
    	int[] lemma = inst.lemmaids != null ? inst.lemmaids : inst.formids;

    	int flag = ggp < gp ? 0 : 1;				// bit 4
    	flag = (flag << 1) | (gp < par ? 0 : 1);	// bit 3
    	flag = (flag << 1) | (par < c ? 0 : 1);		// bit 2
    	flag = (flag << 1) | 1;						// bit 1

    	int GGC = posA[ggp];
    	int GC = posA[gp];
    	int HC = posA[par];
    	int MC = posA[c];
    	int GGL = lemma[ggp];
    	int GL = lemma[gp];
    	int HL = lemma[par];
    	int ML = lemma[c];

    	long code = 0;

    	code = createArcCodePPPP(GGC_GC_HC_MC, GGC, GC, HC, MC);
        addArcFeature(code, fv);
        addArcFeature(code | flag, fv);

    	code = createArcCodeWPPP(GGL_GC_HC_MC, GGL, GC, HC, MC);
        addArcFeature(code, fv);
        addArcFeature(code | flag, fv);

    	code = createArcCodeWPPP(GGC_GL_HC_MC, GL, GGC, HC, MC);
        addArcFeature(code, fv);
        addArcFeature(code | flag, fv);

    	code = createArcCodeWPPP(GGC_GC_HL_MC, HL, GGC, GC, MC);
        addArcFeature(code, fv);
        addArcFeature(code | flag, fv);

    	code = createArcCodeWPPP(GGC_GC_HC_ML, ML, GGC, GC, HC);
        addArcFeature(code, fv);
        addArcFeature(code | flag, fv);

    	code = createArcCodePPP(GGC_HC_MC, GGC, HC, MC);
        addArcFeature(code, fv);
        addArcFeature(code | flag, fv);

    	code = createArcCodeWPP(GGL_HC_MC, GGL, HC, MC);
        addArcFeature(code, fv);
        addArcFeature(code | flag, fv);

    	code = createArcCodeWPP(GGC_HL_MC, HL, GGC, MC);
        addArcFeature(code, fv);
        addArcFeature(code | flag, fv);

    	code = createArcCodeWPP(GGC_HC_ML, ML, GGC, HC);
        addArcFeature(code, fv);
        addArcFeature(code | flag, fv);

    	code = createArcCodePPP(GGC_GC_MC, GGC, GC, MC);
        addArcFeature(code, fv);
        addArcFeature(code | flag, fv);

    	code = createArcCodeWPP(GGL_GC_MC, GGL, GC, MC);
        addArcFeature(code, fv);
        addArcFeature(code | flag, fv);

    	code = createArcCodeWPP(GGC_GL_MC, GL, GGC, MC);
        addArcFeature(code, fv);
        addArcFeature(code | flag, fv);

    	code = createArcCodeWPP(GGC_GC_ML, ML, GGC, GC);
        addArcFeature(code, fv);
        addArcFeature(code | flag, fv);

    	code = createArcCodePP(GGC_MC, GGC, MC);
        addArcFeature(code, fv);
        addArcFeature(code | flag, fv);

    	code = createArcCodeWP(GGL_MC, GGL, MC);
        addArcFeature(code, fv);
        addArcFeature(code | flag, fv);

    	code = createArcCodeWP(GGC_ML, ML, GGC);
        addArcFeature(code, fv);
        addArcFeature(code | flag, fv);

    	code = createArcCodeWW(GGL_ML, GGL, ML);
        addArcFeature(code, fv);
        addArcFeature(code | flag, fv);
        
        return fv;
    }

    public FeatureVector createPSCFeatureVector(DependencyInstance inst, int par, int mod, int ch, int sib) {
    	FeatureVector fv = new FeatureVector(arcAlphabet.size());
    	
    	int[] posA = inst.cpostagids;
    	int[] lemma = inst.lemmaids != null ? inst.lemmaids : inst.formids;

    	int type = (mod - par) * (mod - sib) > 0 ? 0x0 : 0x1;	// 0-1

    	int dir = 0x0;						// 0-2
    	if (mod < par && sib < par)
    		dir = 0x0;
    	else if (mod > par && sib > par)
    		dir = 0x1;
    	else
    		dir = 0x2;
    	
    	dir = (dir << 1) | (mod < ch ? 0x0 : 0x1);	// 0-5
    	dir = (dir << 1) | type;		// 0-11
    	dir += 2;			// 2-13

    	int HC = posA[par];
    	int MC = posA[mod];
    	int CC = posA[ch];
    	int SC = posA[sib];
    	int HL = lemma[par];
    	int ML = lemma[mod];
    	int CL = lemma[ch];
    	int SL = lemma[sib];

    	long code = 0;

    	code = createArcCodePPPP(HC_MC_CC_SC, HC, MC, CC, SC);
        addArcFeature(code | type, fv);
        addArcFeature(code | dir, fv);

    	code = createArcCodeWPPP(HL_MC_CC_SC, HL, MC, CC, SC);
        addArcFeature(code | type, fv);
        addArcFeature(code | dir, fv);

    	code = createArcCodeWPPP(HC_ML_CC_SC, ML, HC, CC, SC);
        addArcFeature(code | type, fv);
        addArcFeature(code | dir, fv);

    	code = createArcCodeWPPP(HC_MC_CL_SC, CL, HC, MC, SC);
        addArcFeature(code | type, fv);
        addArcFeature(code | dir, fv);

    	code = createArcCodeWPPP(HC_MC_CC_SL, SL, HC, MC, CC);
        addArcFeature(code | type, fv);
        addArcFeature(code | dir, fv);

    	code = createArcCodePPP(HC_CC_SC, HC, CC, SC);
        addArcFeature(code | type, fv);
        addArcFeature(code | dir, fv);

    	code = createArcCodeWPP(HL_CC_SC, HL, CC, SC);
        addArcFeature(code | type, fv);
        addArcFeature(code | dir, fv);

    	code = createArcCodeWPP(HC_CL_SC, CL, HC, SC);
        addArcFeature(code | type, fv);
        addArcFeature(code | dir, fv);

    	code = createArcCodeWPP(HC_CC_SL, SL, HC, CC);
        addArcFeature(code | type, fv);
        addArcFeature(code | dir, fv);
    	
    	return fv;
    }


    /************************************************************************
     *  Region end #
     ************************************************************************/
    
    
    /************************************************************************
     * Region start #
     * 
     *  Functions that create global feature vectors
     *  
     ************************************************************************/

    public int findLeftNearestChild(DependencyArcList arclis, int pid, int id) {
    	// find the pid's child which is left closest to id
    	int ret = -1;
    	int st = arclis.startIndex(pid);
    	int en = arclis.endIndex(pid);
    	
    	for (int i = en - 1; i >= st; --i)
    		if (arclis.get(i) < id) {
    			ret = arclis.get(i);
    			break;
    		}
    	return ret;
    }

    public int findRightNearestChild(DependencyArcList arclis, int pid, int id) {
    	// find the pid's child which is right closest to id
    	int ret = -1;
    	int st = arclis.startIndex(pid);
    	int en = arclis.endIndex(pid);

    	for (int i = st; i < en; ++i)
    		if (arclis.get(i) > id) {
    			ret = arclis.get(i);
    			break;
    		}
    	return ret;
    }

    public int[] findConjArg(DependencyArcList arclis, int[] deps, int arg) {
    	// 0: head; 1:left; 2:right
    	int head = -1;
    	int left = -1;
    	int right = -1;

    	if (ccDepType == 0) {
    		// head left arg right
    		//   0   2    1    2
    		right = findRightNearestChild(arclis, arg, arg);
    		left = findLeftNearestChild(arclis, arg, arg);
    		head = deps[arg];
    	} else if (ccDepType == 1) {
    		// head left arg right
    		//   0   1    2    2
    		if (deps[arg] < arg) {
    			left = deps[arg];
    			if (left != -1) {
    				right = findRightNearestChild(arclis, left, arg);
    				head = deps[left];
    			}
    		}
    	} else if (ccDepType == 2) {
    		// head left arg right
    		//   0   1    2    3
    		if (deps[arg] < arg) {
    			left = deps[arg];
    			if (left != -1)
    				head = deps[left];
    			right = findRightNearestChild(arclis, arg, arg);
    		}
    	} else if (ccDepType == 3) {
    		// left arg right head
    		//  3    3   4     0
    		if (deps[arg] > arg && deps[deps[arg]] > deps[arg]) {
    			right = deps[arg];
    			left = findLeftNearestChild(arclis, right, arg);
    			head = deps[right];
    		}

    	} else if (ccDepType == 4) {
    		// head left arg right
    		//  0    1    4    2
    		if (deps[arg] > arg) {
    			right = deps[arg];
    			if (deps[right] < arg) {
    				left = deps[right];
    				if (left != -1) {
    					head = deps[left];
    				}
    			}
    		}
    	} else if (ccDepType == 5) {
    		// left arg right head
    		//  2    3    4    0
    		if (deps[arg] > arg) {
    			right = deps[arg];
    			left = findLeftNearestChild(arclis, arg, arg);
    			if (right != -1)
    				head = deps[right];
    		}
    	} else {
    		Utils.Assert(false);
    	}

    	int[] ret = new int[3];
    	ret[0] = head;
    	ret[1] = left;
    	ret[2] = right;
    	return ret;
    }

    public int[] findPPArg(int[] heads, SpecialPos[] specialPos, DependencyArcList arclis, int arg) {
    	int st = arclis.startIndex(arg);
    	int ed = arclis.endIndex(arg);
    	
    	int c = st == ed ? -1 : arclis.get(st);
    	int c2 = -1;
    	if (c != -1 && specialPos[c] == SpecialPos.C) {
    		if (ccDepType == 0) {
    			// prep is head
    			c2 = findRightNearestChild(arclis, c, c);
    			c = findLeftNearestChild(arclis, c, c);
    		}
    		else if (ccDepType == 1) {
    			// prep is left, should not happen
    			// c = findRightNearestChild(s.child[arg], c);
    		}
    		else if (ccDepType == 2) {
    			c = findRightNearestChild(arclis, arg, c);
    		}
    		// others not valid
    	}
    	int len = 0;
    	int head = heads[arg];
    	if (c != -1 && c != head)
    		len++;
    	if (c2 != -1 && c2 != head)
    		len++;

    	int[] ret = new int[len];
    	len = 0;
    	if (c != -1 && c != head) {
    		ret[len] = c;
    		len++;
    	}
    	if (c2 != -1 && c2 != head) {
    		ret[len] = c2;
    		len++;
    	}
    	return ret;
    }

    public int getMSTRightBranch(SpecialPos[] specialPos, DependencyArcList arclis, int id, int dep) {
    	int node = 1;
    	int st = arclis.startIndex(id);
    	int en = arclis.endIndex(id);
    	
    	if (dep > 10000) {
    		System.out.println("get right branch bug");
    		System.exit(0);
    	}
    	for (int i = en - 1; i >= st; --i) {
    		//if (s.pos[s.child[id][i]].equals("PNX"))
    		if (SpecialPos.PNX == specialPos[arclis.get(i)])
    			continue;
    		node += getMSTRightBranch(specialPos, arclis, arclis.get(i), dep + 1);
    		break;
    	}
    	return node;
    }

    public FeatureVector createPPFeatureVector(DependencyInstance inst, int gp, int par, int c) {
    	FeatureVector fv = new FeatureVector(arcAlphabet.size());

    	int[] posA = inst.cpostagids;
    	int[] lemma = inst.lemmaids != null ? inst.lemmaids : inst.formids;

    	int HC = posA[gp];
    	int MC = posA[c];
    	int HL = lemma[gp];
    	int ML = lemma[c];
    	int PL = lemma[par];

    	long code = 0;

    	code = createArcCodePP(PP_HC_MC, HC, MC);
    	addArcFeature(code, fv);

    	code = createArcCodeWP(PP_HL_MC, HL, MC);
    	addArcFeature(code, fv);

    	code = createArcCodeWP(PP_HC_ML, ML, HC);
    	addArcFeature(code, fv);

    	code = createArcCodeWW(PP_HL_ML, HL, ML);
    	addArcFeature(code, fv);

    	code = createArcCodeWPP(PP_PL_HC_MC, PL, HC, MC);
    	addArcFeature(code, fv);

    	code = createArcCodeWWP(PP_PL_HL_MC, PL, HL, MC);
    	addArcFeature(code, fv);

    	code = createArcCodeWWP(PP_PL_HC_ML, PL, ML, HC);
    	addArcFeature(code, fv);

    	//code = fe->genCodeWWW(HighOrder::PP_PL_HL_ML, HL, ML, PL);
    	//addArcFeature(code, fv);
    	
    	return fv;
    }

    public FeatureVector createCC1FeatureVector(DependencyInstance inst, int left, int arg, int right) {
    	FeatureVector fv = new FeatureVector(arcAlphabet.size());
    	
    	int[] pos = inst.postagids;
    	int[] word = inst.formids;
    	int[] posA = inst.cpostagids;
    	int[][] feats = inst.featids;

    	int CP = pos[arg];
    	int CW = word[arg];
    	int LP = pos[left];
    	int RP = pos[right];
    	int LC = posA[left];
    	int RC = posA[right];

    	long code = 0;

    	code = createArcCodePPP(CC_CP_LP_RP, CP, LP, RP);
    	addArcFeature(code, fv);

    	code = createArcCodePPP(CC_CP_LC_RC, CP, LC, RC);
    	addArcFeature(code, fv);

    	code = createArcCodeWPP(CC_CW_LP_RP, CW, LP, RP);
    	addArcFeature(code, fv);

    	code = createArcCodeWPP(CC_CW_LC_RC, CW, LC, RC);
    	addArcFeature(code, fv);
    	
    	if (feats[left] != null && feats[right] != null) {
        	for (int i = 0; i < feats[left].length; ++i) {
        		if (feats[left][i] <= 0)
        			continue;
        		for (int j = 0; j < feats[right].length; ++j) {
        			if (feats[right][j] <= 0)
        				continue;
        			if (feats[left][i] == feats[right][j]) {
        				code = createArcCodePPP(CC_LC_RC_FID, feats[left][i], LC, RC);
        				addArcFeature(code, fv);
        				break;
        			}
        		}
        	}
    	}
    	
    	return fv;
    }

    public FeatureVector createCC2FeatureVector(DependencyInstance inst, int arg, int head, int child) {
    	FeatureVector fv = new FeatureVector(arcAlphabet.size());

    	int[] pos = inst.postagids;
    	int[] word = inst.formids;
    	int[] posA = inst.cpostagids;
    	int[] lemma = inst.lemmaids != null ? inst.lemmaids : inst.formids;
    	int[][] feats = inst.featids;

    	int CP = pos[arg];
    	int CW = word[arg];
    	int HC = posA[head];
    	int HL = lemma[head];
    	int AC = posA[child];
    	int AL = lemma[child];

    	long code = 0;

    	code = createArcCodePPP(CC_CP_HC_AC, CP, HC, AC);
    	addArcFeature(code, fv);

    	code = createArcCodeWWP(CC_CP_HL_AL, HL, AL, CP);
    	addArcFeature(code, fv);

    	code = createArcCodeWPP(CC_CW_HC_AC, CW, HC, AC);
    	addArcFeature(code, fv);

    	//code = fe->genCodeWWW(HighOrder::CC_CW_HL_AL, CW, HL, AL);
    	//addArcFeature(code, fv);

    	code = createArcCodePP(HP_MP, pos[head], pos[child]);
    	addArcFeature(code, fv);

    	code = createArcCodePP(HP_MP, posA[head], posA[child]);
    	addArcFeature(code, fv);

    	code = createArcCodeWP(HW_MP, lemma[head], pos[child]);
    	addArcFeature(code, fv);

    	code = createArcCodeWP(MW_HP, lemma[child], pos[head]);
    	addArcFeature(code, fv);

    	code = createArcCodeWW(HW_MW, lemma[head], lemma[child]);
    	addArcFeature(code, fv);

    	if (feats[head] != null && feats[child] != null) {
    		for (int fh = 0; fh < feats[head].length; ++fh) {
    			if (feats[head][fh] <= 0)
    				continue;
    			for (int fc = 0; fc < feats[child].length; ++fc) {
    				if (feats[child][fc] <= 0)
    					continue;

    				int IDH = feats[head][fh];
    				int IDM = feats[child][fc];

    				code = createArcCodePP(HP_MP, IDH, IDM);
    				addArcFeature(code, fv);
    			}
    		}
    	}
    	
    	return fv;
    }

    public FeatureVector createPNXFeatureVector(DependencyInstance inst, int head, int arg, int pair) {
    	FeatureVector fv = new FeatureVector(arcAlphabet.size());
    	
    	int[] pos = inst.postagids;
    	int[] word = inst.formids;

    	int flag = (head - arg) * (head - pair) < 0 ? 0 : 1;
    	flag = (flag + 1);

    	long code = 0;

    	code = createArcCodeW(PNX_MW, word[arg]);
    	addArcFeature(code | flag, fv);

    	//code = createArcCodeWP(PNX_HP_MW, pos[head], word[arg]);
	code = createArcCodeWP(PNX_HP_MW, word[arg], pos[head]);
    	addArcFeature(code | flag, fv);
    	
    	return fv;
    }

    public FeatureVector createChildNumFeatureVector(DependencyInstance s, int id, int leftNum, int rightNum) {
    	FeatureVector fv = new FeatureVector(arcAlphabet.size());
    	
    	int childNum = Math.min(GlobalFeatureData.MAX_CHILD_NUM, leftNum + rightNum);
    	int HP = s.postagids[id];
    	int HL = s.lemmaids != null ? s.lemmaids[id] : s.formids[id];
    	
    	long code = 0;

    	code = createArcCodePP(CN_HP_NUM, HP, childNum);
    	addArcFeature(code, fv);

    	code = createArcCodeWP(CN_HL_NUM, HL, childNum);
    	addArcFeature(code, fv);

    	code = createArcCodePPP(CN_HP_LNUM_RNUM, HP, leftNum, rightNum);
    	addArcFeature(code, fv);
    	
    	return fv;
    }

    public FeatureVector createSpanFeatureVector(DependencyInstance s, int id, int end, int punc, int bin) {
    	FeatureVector fv = new FeatureVector(arcAlphabet.size());
    	
    	int HP = s.postagids[id];
    	int HC = s.cpostagids[id];
    	int epFlag = (end << 1) | punc;

    	long code = 0;

    	code = createArcCodePPP(HV_HP, HP, epFlag, bin);
    	addArcFeature(code, fv);

    	code = createArcCodePPP(HV_HC, HC, epFlag, bin);
    	addArcFeature(code, fv);
    	
    	return fv;
    }

    public FeatureVector createNeighborFeatureVector(DependencyInstance s, int par, int id, int left, int right) {
    	FeatureVector fv = new FeatureVector(arcAlphabet.size());
    	
    	int HP = s.postagids[id];
    	int HC = s.cpostagids[id];
    	int HL = s.lemmaids != null ? s.lemmaids[id] : s.formids[id];
    	int GC = s.cpostagids[par];
    	int GL = s.lemmaids != null ? s.lemmaids[par] : s.formids[id];

    	long code = 0;

    	code = createArcCodePPP(NB_HP_LC_RC, HP, left, right);
    	addArcFeature(code, fv);

    	code = createArcCodePPP(NB_HC_LC_RC, HC, left, right);
    	addArcFeature(code, fv);

    	code = createArcCodeWPP(NB_HL_LC_RC, HL, left, right);
    	addArcFeature(code, fv);

    	code = createArcCodePPPP(NB_GC_HC_LC_RC, GC, HC, left, right);
    	addArcFeature(code, fv);

    	code = createArcCodeWPPP(NB_GC_HL_LC_RC, HL, GC, left, right);
    	addArcFeature(code, fv);

    	code = createArcCodeWPPP(NB_GL_HC_LC_RC, GL, HC, left, right);
    	addArcFeature(code, fv);
    	
    	return fv;
    }

    int findPuncCounterpart(int[] word, int arg) {
    	int quoteID = TOKEN_QUOTE;
    	int lrbID = TOKEN_LRB;
    	int rrbID = TOKEN_RRB;
    	if (word[arg] == quoteID) {
    		boolean left = false;
    		int prev = -1;
    		int curr = -1;
    		for (int i = 1; i < word.length; ++i) {
    			if (word[i] == quoteID) {
    				left = !left;
    				prev = curr;
    				curr = i;
    			}
    			if (i == arg) {
    				break;
    			}
    		}
    		if (left) {
    			// left quote
    			curr = -1;
    			for (int i = arg + 1; i < word.length; ++i) {
    				if (word[i] == quoteID) {
    					curr = i;
    					break;
    				}
    			}
    		} else {
    			// right quote
    			curr = prev;
    		}
    		return curr;
    	} else if (word[arg] == lrbID) {
    		// left bracket
    		int curr = -1;
    		for (int i = arg + 1; i < word.length; ++i) {
    			if (word[i] == rrbID) {
    				curr = i;
    				break;
    			}
    		}
    		return curr;
    	} else if (word[arg] == rrbID) {
    		int curr = -1;
    		for (int i = arg - 1; i >= 0; --i) {
    			if (word[i] == lrbID) {
    				curr = i;
    				break;
    			}
    		}
    		return curr;
    	}

    	return -1;
    }

    public FeatureVector createNonprojFeatureVector(DependencyInstance inst, 
    		int num, int head, int mod) {
    	FeatureVector fv = new FeatureVector(arcAlphabet.size());

    	int[] posA = inst.cpostagids;
    	int[] lemma = inst.lemmaids != null ? inst.lemmaids : inst.formids;

    	int distFlag = getBinnedDistance(head - mod);

    	// use head->mod, rather than small->large
    	int HC = posA[head];
    	int MC = posA[mod];
    	int HL = lemma[head];
    	int ML = lemma[mod];

    	int projFlag = num;		// 0..7

    	long code = 0;

    	code = createArcCodeP(NP, projFlag);
    	addArcFeature(code, fv);
    	addArcFeature(code | distFlag, fv);

    	code = createArcCodePP(NP_MC, projFlag, MC);
    	addArcFeature(code, fv);
    	addArcFeature(code | distFlag, fv);

    	code = createArcCodePP(NP_HC, projFlag, HC);
    	addArcFeature(code, fv);
    	addArcFeature(code | distFlag, fv);

    	code = createArcCodeWP(NP_HL, HL, projFlag);
    	addArcFeature(code, fv);
    	addArcFeature(code | distFlag, fv);

    	code = createArcCodeWP(NP_ML, ML, projFlag);
    	addArcFeature(code, fv);
    	addArcFeature(code | distFlag, fv);

    	code = createArcCodePPP(NP_HC_MC, projFlag, HC, MC);
    	addArcFeature(code, fv);
    	addArcFeature(code | distFlag, fv);

    	code = createArcCodeWPP(NP_HL_MC, HL, projFlag, MC);
    	addArcFeature(code, fv);
    	addArcFeature(code | distFlag, fv);

    	code = createArcCodeWPP(NP_HC_ML, ML, projFlag, HC);
    	addArcFeature(code, fv);
    	addArcFeature(code | distFlag, fv);

    	code = createArcCodeWWP(NP_HL_ML, HL, ML, projFlag);
    	addArcFeature(code, fv);
    	addArcFeature(code | distFlag, fv);
    	
    	return fv;
    }

    /************************************************************************
     *  Region end #
     ************************************************************************/
   
    /************************************************************************
     * Region start #
     * 
     *  Functions that add feature codes into feature vectors and alphabets
     *  
     ************************************************************************/
    
    public void addArcFeature(long code, FeatureVector mat) {
    	int id = arcAlphabet.lookupIndex(code, numArcFeats);
    	if (id >= 0) {
    		mat.addEntry(id, 1.0);
    		if (id == numArcFeats) ++numArcFeats;
    	}
    }
    
    public void addArcFeature(long code, double value, FeatureVector mat) {
    	int id = arcAlphabet.lookupIndex(code, numArcFeats);
    	if (id >= 0) {
    		mat.addEntry(id, value);
    		if (id == numArcFeats) ++numArcFeats;
    	}
    }
    
    public void addWordFeature(long code, FeatureVector mat) {
    	int id = wordAlphabet.lookupIndex(code, numWordFeats);
    	if (id >= 0) {
    		mat.addEntry(id, 1.0);
    		if (id == numWordFeats) ++numWordFeats;
    	}
    }
    
    public void addWordFeature(long code, double value, FeatureVector mat) {
    	int id = wordAlphabet.lookupIndex(code, numWordFeats);
    	if (id >= 0) {
    		mat.addEntry(id, value);
    		if (id == numWordFeats) ++numWordFeats;
    	}
    }
    
    /************************************************************************
     *  Region end #
     ************************************************************************/
    
    
    
    /************************************************************************
     * Region start #
     * 
     *  Functions to create or parse 64-bit feature code
     *  
     *  A feature code is like:
     *  
     *    X1 X2 .. Xk TEMP DIST
     *  
     *  where Xi   is the integer id of a word, pos tag, etc.
     *        TEMP is the integer id of the feature template
     *        DIST is the integer binned length  (4 bits)
     ************************************************************************/
    
    public int getBinnedDistance(int x) {
    	int flag = 0;
    	int add = 0;
    	if (x < 0) {
    		x = -x;
    		//flag = 8;
    		add = 7;
    	}
    	if (x > 10)          // x > 10
    		flag |= 0x7;
    	else if (x > 5)		 // x = 6 .. 10
    		flag |= 0x6;
    	else
    		flag |= x;   	 // x = 1 .. 5
    	return flag+add;
    }
    
    public long extractArcTemplateCode(long code) {
    	return (code >> 4) & ((1 << numArcFeatBits)-1);
    }
    
    public long extractDistanceCode(long code) {
    	return code & 15;
    }
    
    public void extractArcCodeP(long code, int[] x) {
    	code = (code >> 4) >> numArcFeatBits;
	    x[0] = (int) (code & ((1 << tagNumBits)-1));
    }
    
    public void extractArcCodePP(long code, int[] x) {
    	code = (code >> 4) >> numArcFeatBits;
	    x[1] = (int) (code & ((1 << tagNumBits)-1));
	    code = code >> tagNumBits;
	    x[0] = (int) (code & ((1 << tagNumBits)-1));
    }
    
    public void extractArcCodePPP(long code, int[] x) {
    	code = (code >> 4) >> numArcFeatBits;
	    x[2] = (int) (code & ((1 << tagNumBits)-1));
	    code = code >> tagNumBits;
	    x[1] = (int) (code & ((1 << tagNumBits)-1));
	    code = code >> tagNumBits;
	    x[0] = (int) (code & ((1 << tagNumBits)-1));
    }
    
    public void extractArcCodePPPP(long code, int[] x) {
    	code = (code >> 4) >> numArcFeatBits;
	    x[3] = (int) (code & ((1 << tagNumBits)-1));
	    code = code >> tagNumBits;
	    x[2] = (int) (code & ((1 << tagNumBits)-1));
	    code = code >> tagNumBits;
	    x[1] = (int) (code & ((1 << tagNumBits)-1));
	    code = code >> tagNumBits;
	    x[0] = (int) (code & ((1 << tagNumBits)-1));
    }
    
    public void extractArcCodeW(long code, int[] x) {
    	code = (code >> 4) >> numArcFeatBits;
	    x[0] = (int) (code & ((1 << wordNumBits)-1));
    }
    
    public void extractArcCodeWW(long code, int[] x) {
    	code = (code >> 4) >> numArcFeatBits;
	    x[1] = (int) (code & ((1 << wordNumBits)-1));
	    code = code >> wordNumBits;
	    x[0] = (int) (code & ((1 << wordNumBits)-1));
    }
    
    public void extractArcCodeWP(long code, int[] x) {
    	code = (code >> 4) >> numArcFeatBits;
	    x[1] = (int) (code & ((1 << tagNumBits)-1));
	    code = code >> tagNumBits;
	    x[0] = (int) (code & ((1 << wordNumBits)-1));
    }
    
    public void extractArcCodeWPP(long code, int[] x) {
    	code = (code >> 4) >> numArcFeatBits;
	    x[2] = (int) (code & ((1 << tagNumBits)-1));
	    code = code >> tagNumBits;
	    x[1] = (int) (code & ((1 << tagNumBits)-1));
	    code = code >> tagNumBits;
	    x[0] = (int) (code & ((1 << wordNumBits)-1));
    }
    
    public void extractArcCodeWWPP(long code, int[] x) {
    	code = (code >> 4) >> numArcFeatBits;
	    x[3] = (int) (code & ((1 << tagNumBits)-1));
	    code = code >> tagNumBits;
	    x[2] = (int) (code & ((1 << tagNumBits)-1));
	    code = code >> tagNumBits;
	    x[1] = (int) (code & ((1 << wordNumBits)-1));
	    code = code >> wordNumBits;
	    x[0] = (int) (code & ((1 << wordNumBits)-1));
    }
    
    public long createArcCodeP(FeatureTemplate.Arc temp, long x) {
    	return ((x << numArcFeatBits) | temp.ordinal()) << 4;
    }
    
    public long createArcCodePP(FeatureTemplate.Arc temp, long x, long y) {
    	return ((((x << tagNumBits) | y) << numArcFeatBits) | temp.ordinal()) << 4;
    }
    
    public long createArcCodePPP(FeatureTemplate.Arc temp, long x, long y, long z) {
    	return ((((((x << tagNumBits) | y) << tagNumBits) | z) << numArcFeatBits)
    			| temp.ordinal()) << 4;
    }
    
    public long createArcCodePPPP(FeatureTemplate.Arc temp, long x, long y, long u, long v) {
    	return ((((((((x << tagNumBits) | y) << tagNumBits) | u) << tagNumBits) | v)
    			<< numArcFeatBits) | temp.ordinal()) << 4;
    }
    
    public long createArcCodePPPPP(FeatureTemplate.Arc temp, long x, long y, long u, long v, long w) {
    	return ((((((((((x << tagNumBits) | y) << tagNumBits) | u) << tagNumBits) | v) << tagNumBits) | w)
    			<< numArcFeatBits) | temp.ordinal()) << 4;
    }
    
    public long createArcCodeW(FeatureTemplate.Arc temp, long x) {
    	return ((x << numArcFeatBits) | temp.ordinal()) << 4;
    }
    
    public long createArcCodeWW(FeatureTemplate.Arc temp, long x, long y) {
    	return ((((x << wordNumBits) | y) << numArcFeatBits) | temp.ordinal()) << 4;
    }
    
    public long createArcCodeWP(FeatureTemplate.Arc temp, long x, long y) {
    	return ((((x << tagNumBits) | y) << numArcFeatBits) | temp.ordinal()) << 4;
    }
    
    public long createArcCodeWPP(FeatureTemplate.Arc temp, long x, long y, long z) {
    	return ((((((x << tagNumBits) | y) << tagNumBits) | z) << numArcFeatBits)
    			| temp.ordinal()) << 4;
    }
    
    public long createArcCodeWPPP(FeatureTemplate.Arc temp, long x, long y, long u, long v) {
    	return ((((((((x << tagNumBits) | y) << tagNumBits) | u) << tagNumBits) | v) << numArcFeatBits)
    			| temp.ordinal()) << 4;
    }
    
    public long createArcCodeWWP(FeatureTemplate.Arc temp, long x, long y, long z) {
    	return ((((((x << wordNumBits) | y) << tagNumBits) | z) << numArcFeatBits) | temp.ordinal()) << 4;
    }
    
    public long createArcCodeWWPP(FeatureTemplate.Arc temp, long x, long y, long u, long v) {
    	return ((((((((x << wordNumBits) | y) << tagNumBits) | u) << tagNumBits) | v)
    			<< numArcFeatBits) | temp.ordinal()) << 4;
    }
    
    public long createWordCodeW(FeatureTemplate.Word temp, long x) {
    	return ((x << numWordFeatBits) | temp.ordinal()) << 4;
    }
    
    public long createWordCodeP(FeatureTemplate.Word temp, long x) {
    	return ((x << numWordFeatBits) | temp.ordinal()) << 4;
    }
    
    public long createWordCodePP(FeatureTemplate.Word temp, long x, long y) {
    	return ((((x << tagNumBits) | y) << numWordFeatBits) | temp.ordinal()) << 4;
    }
    
    public long createWordCodePPP(FeatureTemplate.Word temp, long x, long y, long z) {
    	return ((((((x << tagNumBits) | y) << tagNumBits) | z) << numWordFeatBits)
    			| temp.ordinal()) << 4;
    }
    
    public long createWordCodeWP(FeatureTemplate.Word temp, long x, long y) {
    	return ((((x << tagNumBits) | y) << numWordFeatBits) | temp.ordinal()) << 4;
    }
    
    /************************************************************************
     *  Region end #
     ************************************************************************/
    
    public void fillParameters(LowRankParam tensor, Parameters params) {
        //System.out.println(arcAlphabet.size());	
    	long[] codes = arcAlphabet.toArray();
    	int[] x = new int[4];
    	
    	for (long code : codes) {
    		
    		int id = arcAlphabet.lookupIndex(code);
    		if (id < 0) continue;
    		
    		int dist = (int) extractDistanceCode(code);
    		int temp = (int) extractArcTemplateCode(code);
    		long head = 0, mod = 0;

        	//code = createArcCodePPPP(CORE_POS_PT0, pHeadLeft, pHead, pMod, pModRight);
    		if (temp == HPp_HP_MP_MPn.ordinal()) {
    			extractArcCodePPPP(code, x);
    			head = createWordCodePP(WORDFV_PpP0, x[0], x[1]);
    			mod = createWordCodePP(WORDFV_P0Pn, x[2], x[3]);
    		}
    		
        	//code = createArcCodePPP(CORE_POS_PT1, pHead, pMod, pModRight);
    		else if (temp == HP_MP_MPn.ordinal()) {
    			extractArcCodePPP(code, x);
    			head = createWordCodeP(WORDFV_P0, x[0]);
    			mod = createWordCodePP(WORDFV_P0Pn, x[1], x[2]);
    		}

        	//code = createArcCodePPP(CORE_POS_PT2, pHeadLeft, pHead, pMod);
    		else if (temp == HPp_HP_MP.ordinal()) {
    			extractArcCodePPP(code, x);
    			head = createWordCodePP(WORDFV_PpP0, x[0], x[1]);
    			mod = createWordCodeP(WORDFV_P0, x[2]);
    		}
    		
        	//code = createArcCodePPP(CORE_POS_PT3, pHeadLeft, pMod, pModRight);
    		else if (temp == HPp_MP_MPn.ordinal()) {
    			extractArcCodePPP(code, x);
    			head = createWordCodeP(WORDFV_Pp, x[0]);
    			mod = createWordCodePP(WORDFV_P0Pn, x[1], x[2]);
    		}
    		
        	//code = createArcCodePPP(CORE_POS_PT4, pHeadLeft, pHead, pModRight);
    		else if (temp == HPp_HP_MPn.ordinal()) {
    			extractArcCodePPP(code, x);
    			head = createWordCodePP(WORDFV_PpP0, x[0], x[1]);
    			mod = createWordCodeP(WORDFV_Pn, x[2]);
    		}
        	        	
    		//code = createArcCodePPPP(CORE_POS_APT0, pHead, pHeadRight, pModLeft, pMod);
    		else if (temp == HP_HPn_MPp_MP.ordinal()) {
    			extractArcCodePPPP(code, x);
    			head = createWordCodePP(WORDFV_P0Pn, x[0], x[1]);
    			mod = createWordCodePP(WORDFV_PpP0, x[2], x[3]);
    		}
    		
    		//code = createArcCodePPP(CORE_POS_APT1, pHead, pModLeft, pMod);
    		else if (temp == HP_MPp_MP.ordinal()) {
    			extractArcCodePPP(code, x);
    			head = createWordCodeP(WORDFV_P0, x[0]);
    			mod = createWordCodePP(WORDFV_PpP0, x[1], x[2]);
    		}
    		
    		//code = createArcCodePPP(CORE_POS_APT2, pHead, pHeadRight, pMod);
    		else if (temp == HP_HPn_MP.ordinal()) {
    			extractArcCodePPP(code, x);
    			head = createWordCodePP(WORDFV_P0Pn, x[0], x[1]);
    			mod = createWordCodeP(WORDFV_P0, x[2]);
    		}
    		
    		//code = createArcCodePPP(CORE_POS_APT3, pHeadRight, pModLeft, pMod);
    		else if (temp == HPn_MPp_MP.ordinal()) {
    			extractArcCodePPP(code, x);
    			head = createWordCodeP(WORDFV_Pn, x[0]);
    			mod = createWordCodePP(WORDFV_PpP0, x[1], x[2]);
    		}
    		
    		//code = createArcCodePPP(CORE_POS_APT4, pHead, pHeadRight, pModLeft);
    		else if (temp == HP_HPn_MPp.ordinal()) {
    			extractArcCodePPP(code, x);
    			head = createWordCodePP(WORDFV_P0Pn, x[0], x[1]);
    			mod = createWordCodeP(WORDFV_Pp, x[2]);
    		}

    		//code = createArcCodePPPP(CORE_POS_BPT, pHeadLeft, pHead, pModLeft, pMod);
    		else if (temp == HPp_HP_MPp_MP.ordinal()) {
    			extractArcCodePPPP(code, x);
    			head = createWordCodePP(WORDFV_PpP0, x[0], x[1]);
    			mod = createWordCodePP(WORDFV_PpP0, x[2], x[3]);
    		}
    		
    		//code = createArcCodePPPP(CORE_POS_CPT, pHead, pHeadRight, pMod, pModRight);
    		else if (temp == HP_HPn_MP_MPn.ordinal()) {
    			extractArcCodePPPP(code, x);
    			head = createWordCodePP(WORDFV_P0Pn, x[0], x[1]);
    			mod = createWordCodePP(WORDFV_P0Pn, x[2], x[3]);
    		}
    		
        	//code = createArcCodeWWPP(CORE_BIGRAM_A, head, mod, headP, modP);
    		else if (temp == HW_MW_HP_MP.ordinal()) {
    			extractArcCodeWWPP(code, x);
    			head = createWordCodeWP(WORDFV_W0P0, x[0], x[2]);
    			mod = createWordCodeWP(WORDFV_W0P0, x[1], x[3]);
    		}
        	
        	//code = createArcCodeWPP(CORE_BIGRAM_B, mod, headP, modP);
    		else if (temp == MW_HP_MP.ordinal()) {
    			extractArcCodeWPP(code, x);
    			head = createWordCodeP(WORDFV_P0, x[1]);
    			mod = createWordCodeWP(WORDFV_W0P0, x[0], x[2]);
    		}
        	
        	//code = createArcCodeWPP(CORE_BIGRAM_C, head, headP, modP);
    		else if (temp == HW_HP_MP.ordinal()) {
    			extractArcCodeWPP(code, x);
    			head = createWordCodeWP(WORDFV_W0P0, x[0], x[1]);
    			mod = createWordCodeP(WORDFV_P0, x[2]);
    		}
        	
        	//code = createArcCodeWP(CORE_BIGRAM_D, mod, headP);
    		else if (temp == MW_HP.ordinal()) {
    			extractArcCodeWP(code, x);
    			head = createWordCodeP(WORDFV_P0, x[1]);
    			mod = createWordCodeW(WORDFV_W0, x[0]);
    		}
        	
        	//code = createArcCodeWP(CORE_BIGRAM_E, head, modP);
    		else if (temp == HW_MP.ordinal()) {
    			extractArcCodeWP(code, x);
    			head = createWordCodeW(WORDFV_W0, x[0]);
    			mod = createWordCodeP(WORDFV_P0, x[1]);
    		}
        	
            //code = createArcCodeWW(CORE_BIGRAM_F, head, mod);
    		else if (temp == HW_MW.ordinal()) {
    			extractArcCodeWW(code, x);
    			head = createWordCodeW(WORDFV_W0, x[0]);
    			mod = createWordCodeW(WORDFV_W0, x[1]);
    		}
        	
            //code = createArcCodePP(CORE_BIGRAM_G, headP, modP);
    		else if (temp == HP_MP.ordinal()) {
    			extractArcCodePP(code, x);
    			head = createWordCodeW(WORDFV_P0, x[0]);
    			mod = createWordCodeW(WORDFV_P0, x[1]);
    		}
    		
        	//code = createArcCodeWP(CORE_BIGRAM_H, head, headP);
    		else if (temp == HW_HP.ordinal()) {
    			extractArcCodeWP(code, x);
    			head = createWordCodeWP(WORDFV_W0P0, x[0], x[1]);
    			mod = createWordCodeP(WORDFV_BIAS, 0);
    		}
    		
        	//code = createArcCodeWP(CORE_BIGRAM_K, mod, modP);
    		else if (temp == MW_MP.ordinal()) {
    			extractArcCodeWP(code, x);    			
    			head = createWordCodeP(WORDFV_BIAS, 0);
    			mod = createWordCodeWP(WORDFV_W0P0, x[0], x[1]);
    		}
    		
    		else if (temp == CORE_HEAD_WORD.ordinal()) {
    			extractArcCodeW(code, x);
    			head = createWordCodeW(WORDFV_W0, x[0]);
    			mod = createWordCodeP(WORDFV_BIAS, 0);
    		}
    		
    		else if (temp == CORE_HEAD_POS.ordinal()) {
    			extractArcCodeP(code, x);
    			head = createWordCodeP(WORDFV_P0, x[0]);
    			mod = createWordCodeP(WORDFV_BIAS, 0);	
    		}
    		
    		else if (temp == CORE_MOD_WORD.ordinal()) {
    			extractArcCodeW(code, x);
    			head = createWordCodeP(WORDFV_BIAS, 0);
    			mod = createWordCodeW(WORDFV_W0, x[0]);    			
    		}
    		
    		else if (temp == CORE_MOD_POS.ordinal()) {
    			extractArcCodeP(code, x);
    			head = createWordCodeP(WORDFV_BIAS, 0);	
    			mod = createWordCodeP(WORDFV_P0, x[0]);
    		}
    		
    		else if (temp == CORE_HEAD_pWORD.ordinal()) {
    			extractArcCodeW(code, x);
    			head = createWordCodeW(WORDFV_Wp, x[0]);
    			mod = createWordCodeP(WORDFV_BIAS, 0);
    		}
    		
    		else if (temp == CORE_HEAD_nWORD.ordinal()) {
    			extractArcCodeW(code, x);
    			head = createWordCodeW(WORDFV_Wn, x[0]);
    			mod = createWordCodeP(WORDFV_BIAS, 0);
    		}
    		
    		else if (temp == CORE_MOD_pWORD.ordinal()) {
    			extractArcCodeW(code, x);
    			mod = createWordCodeW(WORDFV_Wp, x[0]);
    			head = createWordCodeP(WORDFV_BIAS, 0);
    		}
    		
    		else if (temp == CORE_MOD_nWORD.ordinal()) {
    			extractArcCodeW(code, x);
    			mod = createWordCodeW(WORDFV_Wn, x[0]);
    			head = createWordCodeP(WORDFV_BIAS, 0);
    		}
    		
    		else if (temp == HEAD_EMB.ordinal()) {
    			extractArcCodeW(code, x);
    			head = createWordCodeW(WORDFV_EMB, x[0]);
    			mod = createWordCodeP(WORDFV_BIAS, 0);
    		}
    		
    		else if (temp == MOD_EMB.ordinal()) {
    			extractArcCodeW(code, x);
    			mod = createWordCodeW(WORDFV_EMB, x[0]);
    			head = createWordCodeP(WORDFV_BIAS, 0);
    		}
    		
    		else {
    			//System.out.println(temp);
    			continue;
    		}
    		
    		int headId = wordAlphabet.lookupIndex(head);
    		int modId = wordAlphabet.lookupIndex(mod);
    		if (headId >= 0 && modId >= 0) {
    			double value = params.params[id];
    			tensor.putEntry(headId, modId, dist, value);
            }
    	}
    	
    }
}
