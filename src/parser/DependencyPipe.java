package parser;



import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.HashSet;
import java.util.LinkedList;


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
				
		DependencyReader reader = DependencyReader.createDependencyReader(options);
		reader.startReading(file);
		DependencyInstance inst = reader.nextInstance();
		
		int cnt = 0;
		while (inst != null) {
			inst.setInstIds(tagDictionary, wordDictionary, typeAlphabet, wordVecDictionary);			
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
		HashSet<String> mcposTagSet = new HashSet<String>();
		DependencyReader reader = DependencyReader.createDependencyReader(options);
		reader.startReading(file);
		
		DependencyInstance inst = reader.nextInstance();
		int cnt = 0;
		
		while(inst != null) {
			
			for (int i = 0; i < inst.length; ++i) {
				if (inst.postags != null) posTagSet.add(inst.postags[i]);
				if (inst.cpostags != null) cposTagSet.add(inst.cpostags[i]);
			}
			
			inst.setInstIds(tagDictionary, wordDictionary, typeAlphabet, wordVecDictionary);
			
		    initFeatureAlphabets(inst);
				
		    inst = reader.nextInstance();
		    cnt++;
	        if (options.maxNumSent != -1 && cnt >= options.maxNumSent) break;
		}
		
		closeAlphabets();
		reader.close();
		
		System.out.printf("[%d ms]%n", System.currentTimeMillis() - start);
		
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
			
			inst.setInstIds(tagDictionary, wordDictionary, typeAlphabet, wordVecDictionary);
			
		    //createFeatures(inst);
			lt.add(new DependencyInstance(inst));		    
			
			inst = reader.nextInstance();
			cnt++;
			if (options.maxNumSent != -1 && cnt >= options.maxNumSent) break;
			if (cnt % 100 == 0)
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
    	
    	inst.setInstIds(tagDictionary, wordDictionary, typeAlphabet, wordVecDictionary);
    			
	    //createFeatures(inst);
	    
	    return inst;
    }
    
    public void initFeatureAlphabets(DependencyInstance inst) 
    {
        
        for (int i = 0; i < inst.length; ++i)
            createWordFeatures(inst, i);
    	
        int[] heads = inst.heads;
        int[] deprelids = inst.deprelids;
    	for (int i = 0; i < inst.length; ++i) {
    		
    		if (heads[i] == -1) continue;
    	     
    		int parent = heads[i];
    		createArcFeatures(inst, parent, i);	// arc features    		
    		if (options.learnLabel) {
    			int type = deprelids[i]; 
    			boolean toRight = parent < i;
    			createLabelFeatures(inst, parent, type, toRight, false);
    			createLabelFeatures(inst, i, type, toRight, true);
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
        int[] posA = inst.cpostagids;
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
    
    /************************************************************************
     *  Region end #
     ************************************************************************/
    
    
    /************************************************************************
     * Region start #
     * 
     *  Functions that create 2nd order feature vectors
     *  
     ************************************************************************/

    public FeatureVector createTripsFeatureVector(DependencyInstance inst, int par,
    		int ch1, int ch2) {

    	FeatureVector fv = new FeatureVector(arcAlphabet.size());
    	
    	int[] pos = inst.postagids;
    	int[] posA = inst.cpostagids;

    	// ch1 is always the closes to par
    	int dirFlag = (((par < ch2 ? 0 : 1) << 1) | 1);

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

    void addTurboSib(DependencyInstance inst, int par, int ch1, int ch2, int dirFlag, FeatureVector fv) {
    	int[] posA = inst.cpostagids;
    	int[] lemma = inst.lemmaids;
    	int len = inst.length;

    	int HC = posA[par];
    	int SC = ch1 == par ? TOKEN_START : posA[ch1];
    	int MC = posA[ch2];

    	int HL = lemma[par];
    	int SL = ch1 == par ? TOKEN_START : lemma[ch1];
    	int ML = lemma[ch2];

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

    public FeatureVector createSibFeatureVector(DependencyInstance inst, int ch1, int ch2, boolean isST) {
    	
    	FeatureVector fv = new FeatureVector(arcAlphabet.size());

    	int[] pos = inst.postagids;
    	int[] posA = inst.cpostagids;
    	int[] toks = inst.formids;
    	int[] lemma = inst.lemmaids;

    	// ch1 is always the closes to par

    	int SP = isST ? TOKEN_START : pos[ch1];
    	int MP = pos[ch2];
    	int SW = isST ? TOKEN_START : toks[ch1];
    	int MW = toks[ch2];
    	int SC = isST ? TOKEN_START : posA[ch1];
    	int MC = posA[ch2];
    	int SL = isST ? TOKEN_START : lemma[ch1];
    	int ML = lemma[ch2];

    	int flag = getBinnedDistance(ch1 - ch2);

    	long code = 0;

    	code = createArcCodePP(SP_MP, SP, MP);
    	addArcFeature(code, fv);
    	addArcFeature(code | flag, fv);

    	code = createArcCodePP(SW_MW, SW, MW);
    	addArcFeature(code, fv);
    	addArcFeature(code | flag, fv);

    	code = createArcCodePP(SW_MP, MP, SW);
    	addArcFeature(code, fv);
    	addArcFeature(code | flag, fv);

    	code = createArcCodePP(SP_MW, SP, MW);
    	addArcFeature(code, fv);
    	addArcFeature(code | flag, fv);

    	code = createArcCodePP(SC_MC, SC, MC);
    	addArcFeature(code, fv);
    	addArcFeature(code | flag, fv);

    	code = createArcCodePP(SL_ML, SL, ML);
    	addArcFeature(code, fv);
    	addArcFeature(code | flag, fv);

    	code = createArcCodePP(SL_MC, MC, SL);
    	addArcFeature(code, fv);
    	addArcFeature(code | flag, fv);

    	code = createArcCodePP(SC_ML, SC, ML);
    	addArcFeature(code, fv);
    	addArcFeature(code | flag, fv);

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
}
