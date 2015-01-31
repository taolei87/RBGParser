package parser;



import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

//import javax.swing.text.html.HTMLDocument.HTMLReader.TagAction;

import parser.DependencyInstance.SpecialPos;
import parser.Options.LearningMode;
import parser.Options.PossibleLang;
import parser.feature.FeatureTemplate;
import parser.feature.FeatureTemplate.Arc;
import parser.feature.SyntacticFeatureFactory;
import parser.io.DependencyReader;
import utils.Alphabet;
import utils.Dictionary;
import utils.DictionarySet;
import utils.FeatureVector;
import utils.Utils;
import static parser.feature.FeatureTemplate.Arc.*;
import static parser.feature.FeatureTemplate.Word.*;
import static utils.DictionarySet.DictionaryTypes.*;

public class DependencyPipe implements Serializable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	//public static boolean useLexicalFeature = true;
	
	
    public Options options;
    public DictionarySet dictionaries;
    public SyntacticFeatureFactory synFactory;
        
	public static String unknowWord = "*UNKNOWN*";	
	public double[][] wordVectors = null;
	public double[] unknownWv = null;
		
    public String[] types;					// array that maps label index to label string
    public String[] args;
	
	// language specific info
	public HashSet<String> conjWord;
	public HashMap<String, String> coarseMap;
	
	public DependencyPipe(Options options) throws IOException 
	{
		dictionaries = new DictionarySet();
		synFactory = new SyntacticFeatureFactory(options);
		
		this.options = options;
				
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
        coarseMap = new HashMap<String, String>();
        try {
            BufferedReader br = new BufferedReader(new FileReader(options.unimapFile));
            String str = null;
            while ((str = br.readLine()) != null) {
                String[] data = str.split("\\s+");
                coarseMap.put(data[0], data[1]);
            }
            br.close();
            
            coarseMap.put("<root-POS>", "ROOT");
        } catch (Exception e) {
            System.out.println("Warning: couldn't find coarse POS map for this language");
        }

		// decide ccDepType
        int ccDepType = 0;
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
		synFactory.ccDepType = ccDepType;
		
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
		System.out.println("Creating dictionaries ... ");
		//
		//TObjectIntHashMap<String> pathCounts = new TObjectIntHashMap<String>();
		//TIntIntHashMap pathlengthCounts = new TIntIntHashMap();
		
        dictionaries.setCounters();
        
		DependencyReader reader = DependencyReader.createDependencyReader(options);
		reader.startReading(file);
		DependencyInstance inst = reader.nextInstance();
		
		int cnt = 0;
		while (inst != null) {
			inst.setInstIds(dictionaries, coarseMap, conjWord, options.lang);
			
			inst = reader.nextInstance();	
			++cnt;
			if (options.maxNumSent != -1 && cnt >= options.maxNumSent) break;
		}
		reader.close();
		
		//dumpPathStats(pathCounts, pathlengthCounts);
		
		dictionaries.filterDictionary(DEPLABEL);
		//dictionaries.filterDictionary(WORD);
		dictionaries.closeCounters();
		
		synFactory.TOKEN_START = dictionaries.lookupIndex(POS, "#TOKEN_START#");
		synFactory.TOKEN_END = dictionaries.lookupIndex(POS, "#TOKEN_END#");
		synFactory.TOKEN_MID = dictionaries.lookupIndex(POS, "#TOKEN_MID#");
		dictionaries.lookupIndex(WORD, "#TOKEN_START#");
		dictionaries.lookupIndex(WORD, "#TOKEN_END#");
		dictionaries.lookupIndex(WORD, "#TOKEN_MID#");
		synFactory.TOKEN_QUOTE = dictionaries.lookupIndex(WORD, "form=\"");
		synFactory.TOKEN_RRB = dictionaries.lookupIndex(WORD, "form=)");
		synFactory.TOKEN_LRB = dictionaries.lookupIndex(WORD, "form=(");
        Utils.Assert(dictionaries.lookupIndex(WORD, "form=(") == synFactory.TOKEN_LRB);
        Utils.Assert(dictionaries.lookupIndex(WORD, "form=\"") == synFactory.TOKEN_QUOTE);
        
		//wordDictionary.stopGrowth();
		//tagDictionary.stopGrowth();
		dictionaries.stopGrowth(DEPLABEL);
		dictionaries.stopGrowth(POS);
		dictionaries.stopGrowth(WORD);
				
		synFactory.wordNumBits = Utils.log2(dictionaries.size(WORD) + 1);
		synFactory.tagNumBits = Utils.log2(dictionaries.size(POS) + 1);
		synFactory.depNumBits = Utils.log2(dictionaries.size(DEPLABEL)*2 + 1);
		if (options.learnLabel)
			synFactory.flagBits = synFactory.depNumBits + 4;
		else
			synFactory.flagBits = 0;
		
		types = new String[dictionaries.size(DEPLABEL)];	 
		Dictionary labelDict = dictionaries.get(DEPLABEL);
		Object[] keys = labelDict.toArray();
		for (int i = 0; i < keys.length; ++i) {
			int id = labelDict.lookupIndex(keys[i]);
			types[id-1] = (String)keys[i];
		}
		
		System.out.printf("%d %d%n", numWordFeatBits, numArcFeatBits);
		System.out.printf("Lexical items: %d (%d bits)%n", 
				dictionaries.size(WORD), synFactory.wordNumBits);
		System.out.printf("Tag/label items: %d (%d bits)  %d (%d bits)%n", 
				dictionaries.size(POS), synFactory.tagNumBits,
				dictionaries.size(DEPLABEL), synFactory.depNumBits);
		
		System.out.printf("Creation took [%d ms]%n", System.currentTimeMillis() - start);
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
		int cnt = 0;
		Evaluator eval = new Evaluator(options, this);
		
		while(inst != null) {
			
			for (int i = 0; i < inst.length; ++i) {
				if (inst.postags != null) posTagSet.add(inst.postags[i]);
				if (inst.cpostags != null) cposTagSet.add(inst.cpostags[i]);
			}
			
			inst.setInstIds(dictionaries, coarseMap, conjWord, options.lang);
			
			eval.add(inst, inst, false);
		    synFactory.initFeatureAlphabets(inst);
				
		    inst = reader.nextInstance();
		    cnt++;
	        if (options.maxNumSent != -1 && cnt >= options.maxNumSent) break;
		}
				
		System.out.printf("[%d ms]%n", System.currentTimeMillis() - start);
		
		closeAlphabets();
		reader.close();

		System.out.printf("Num of CONLL fine POS tags: %d%n", posTagSet.size());
		System.out.printf("Num of CONLL coarse POS tags: %d%n", cposTagSet.size());
		System.out.printf("Num of labels: %d%n", types.length);
		System.out.printf("Num of Syntactic Features: %d %d%n", 
				synFactory.numWordFeats, synFactory.numArcFeats);
	    
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
		
		//wordVecDictionary = new Dictionary();
		String line = in.readLine();
		while (line != null) {
			line = line.trim();
			String[] parts = line.split("[ \t]");
			String word = parts[0];
			//wordVecDictionary.lookupIndex(word);
			dictionaries.lookupIndex(WORDVEC, word);
			line = in.readLine();
		}
		in.close();
		dictionaries.stopGrowth(WORDVEC);
		
		in = new BufferedReader(
				new InputStreamReader(new FileInputStream(file),"UTF8"));
		wordVectors = new double[dictionaries.size(WORDVEC)+1][];
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
            	int wordId = dictionaries.lookupIndex(WORDVEC, word);
            	if (wordId > 0) wordVectors[wordId] = v;
            }
            
			line = in.readLine();
		}
		in.close();

        sumL2 /= cnt;
        
        synFactory.unknownWv = unknownWv;
        synFactory.wordVectors = wordVectors;
        
        System.out.printf("Vector norm: Avg: %f  Min: %f  Max: %f%n", 
        		sumL2, minL2, maxL2);     
	}
	
	/***
	 * Close alphabets so the feature set wouldn't grow.
	 */
    public void closeAlphabets() 
    {
		
		//typeAlphabet.stopGrowth();
		//wordAlphabet.stopGrowth();
		//arcAlphabet.stopGrowth();
		synFactory.closeAlphabets();

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
			
			inst.setInstIds(dictionaries, coarseMap, conjWord, options.lang);
			
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
    	
    	inst.setInstIds(dictionaries, coarseMap, conjWord, options.lang);
    			
	    //createFeatures(inst);
	    
	    return inst;
    }
    
    
    
    
	
}
