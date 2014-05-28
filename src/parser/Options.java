package parser;

import java.io.Serializable;


public class Options implements Cloneable, Serializable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public enum LearningMode {
		Basic,			// 1st order arc factored model
		Standard,		// 3rd order using similar features as TurboParser
		Full			// full model with two additional 3rd order features and global features
	}
		
	public String trainFile = null;
	public String testFile = null;
	public String unimapFile = null;
	public String outFile = null;
	public boolean train = false;
	public boolean test = false;	
	public String wordVectorFile = null;
	public String modelFile = "model.out";
    public String format = "CONLL";
    
	public int maxNumSent = -1;
    public int numPretrainIters = 1;
	public int maxNumIters = 10;
	public boolean initTensorWithPretrain = true;
	
	//public LearningMode learningMode = LearningMode.Basic;
	public LearningMode learningMode = LearningMode.Standard;
	public boolean projective = false;
	public boolean learnLabel = false;
	public boolean pruning = true;
	public double pruningCoeff = 0.1;
	public int labelLossType = 0;
	
	public int numHcThreads = 10;		// hill climbing: number of threads
	public int numHcConverge = 300;		// hill climbing: number of restarts to converge 
	
	public boolean average = true;
	public double C = 0.01;
	public double gamma = 1, gammaLabel = 1;
	public int R = 50;
	
	public int bruteForceThread = 20;
	
	// feature set
	public boolean useCS = true;		// use consecutive siblings
	public boolean useGP = true;		// use grandparent
	public boolean useHB = true;		// use head bigram
	public boolean useGS = true;		// use grand sibling
	public boolean useTS = true;		// use tri-sibling
	public boolean useGGP = true;		// use great-grandparent
	public boolean usePSC = true;		// use parent-sibling-child
	public boolean useHO = true;		// use global feature
	
	// CoNLL language specific info
	// used only in Full learning mode
	public enum PossibleLang {
		Arabic,
		Bulgarian,
		Chinese,
		Czech,
		Danish,
		Dutch,
		English08,
		German,
		Japanese,
		Portuguese,
		Slovene,
		Spanish,
		Swedish,
		Turkish,
		Unknown,
	}
	PossibleLang lang;
	
	final static String langString[] = {"arabic", "bulgarian", "chinese", "czech", "danish", "dutch",
			"english08", "german", "japanese", "portuguese", "slovene", "spanish",
			"swedish", "turkish"};
	
	
	public Options() {
		
	}
	
	@Override
	public Object clone() throws CloneNotSupportedException 
	{
		return super.clone();
	}
	
    public void processArguments(String[] args) {
    	
    	for (String arg : args) {
    		if (arg.equals("train")) {
    			train = true;
    		}
    		else if (arg.equals("test")) {
    			test = true;
    		}
    		else if (arg.startsWith("label")) {
    			learnLabel = Boolean.parseBoolean(arg.split(":")[1]);
    		}
            else if (arg.startsWith("proj")) {
                projective = Boolean.parseBoolean(arg.split(":")[1]);
            }
            else if (arg.startsWith("average:")) {
            	average = Boolean.parseBoolean(arg.split(":")[1]);
            }
    		else if (arg.startsWith("train-file:")) {
    			trainFile = arg.split(":")[1];
    		}
    		else if (arg.startsWith("test-file:")) {
    			testFile = arg.split(":")[1];
    		}
    		else if (arg.startsWith("unimap-file:")) {
    			unimapFile = arg.split(":")[1];
    		}
    		else if (arg.startsWith("output-file:")) {
    			outFile = arg.split(":")[1];
    		}
    		else if (arg.startsWith("model-file:")) {
    			modelFile = arg.split(":")[1];
    		}
            else if (arg.startsWith("max-sent:")) {
                maxNumSent = Integer.parseInt(arg.split(":")[1]);
            }
            else if (arg.startsWith("C:")) {
            	C = Double.parseDouble(arg.split(":")[1]);
            }
            else if (arg.startsWith("gamma:")) {
            	gamma = Double.parseDouble(arg.split(":")[1]);
            	//gammaLabel = gamma;
            }
            else if (arg.startsWith("R:")) {
                R = Integer.parseInt(arg.split(":")[1]);
            }
            else if (arg.startsWith("word-vector:")) {
            	wordVectorFile = arg.split(":")[1];
            }
            else if (arg.startsWith("iters:")) {
                maxNumIters = Integer.parseInt(arg.split(":")[1]);
            }
            else if (arg.startsWith("pre-iters:")) {
                numPretrainIters = Integer.parseInt(arg.split(":")[1]);
            }
            else if (arg.startsWith("pruning:")) {
                pruning = Boolean.parseBoolean(arg.split(":")[1]);
            }
            else if (arg.startsWith("thread:")) {
            	numHcThreads = Integer.parseInt(arg.split(":")[1]);
            }
            else if (arg.startsWith("converge:")) {
            	numHcConverge = Integer.parseInt(arg.split(":")[1]);
            }
            else if (arg.startsWith("bfthread:")) {
            	bruteForceThread = Integer.parseInt(arg.split(":")[1]);
            }
            else if (arg.startsWith("model:")) {
            	String str = arg.split(":")[1];
            	if (str.equals("basic"))
            		learningMode = LearningMode.Basic;
            	else if (str.equals("standard"))
            		learningMode = LearningMode.Standard;
            	else if (str.equals("full"))
            		learningMode = LearningMode.Full;
            }

    	}    	
        
        //gammaLabel = 1.0;

    	switch (learningMode) {
    		case Basic:
    			useCS = false;
    			useGP = false;
    			useHB = false;
    			useGS = false;
    			useTS = false;
    			useGGP = false;
    			usePSC = false;
    			useHO = false;
    			break;
    		case Standard:
    			useGGP = false;
    			usePSC = false;
    			useHO = false;
    			break;
    		case Full:
    			break;
    		default:
    			break;
    	}
    	
    	lang = findLang(trainFile != null ? trainFile : testFile);
    }
    
    public void printOptions() {
    	System.out.println("------\nFLAGS\n------");
    	System.out.println("train-file: " + trainFile);
    	System.out.println("test-file: " + testFile);
    	System.out.println("model-name: " + modelFile);
        System.out.println("output-file: " + outFile);
    	System.out.println("train: " + train);
    	System.out.println("test: " + test);
        System.out.println("iters: " + maxNumIters);
    	System.out.println("label: " + learnLabel);
        System.out.println("max-sent: " + maxNumSent);      
        System.out.println("C: " + C);
        System.out.println("label-loss-type: " + labelLossType);
        System.out.println("gamma: " + gamma + " " + gammaLabel);
        System.out.println("R: " + R);
        System.out.println("word-vector:" + wordVectorFile);
        System.out.println("projective: " + projective);
        System.out.println("pruning: " + pruning);
        System.out.println("converge iter: " + numHcConverge);
        System.out.println("brute force thread: " + bruteForceThread);
        
        System.out.println();
        System.out.println("use consecutive siblings: " + useCS);
        System.out.println("use grandparent: " + useGP);
        System.out.println("use head bigram: " + useHB);
        System.out.println("use grand siblings: " + useGS);
        System.out.println("use tri-siblings: " + useTS);
        System.out.println("use great-grandparent: " + useGGP);
        System.out.println("use parent-sibling-child: " + usePSC);
        System.out.println("use high-order: " + useHO);
        System.out.println("model: " + learningMode.name());

    	System.out.println("------\n");
    }
    
    PossibleLang findLang(String file) {
    	for (PossibleLang lang : PossibleLang.values())
    		if (lang != PossibleLang.Unknown && file.indexOf(langString[lang.ordinal()]) != -1) {
    			return lang;
    		}
    	System.out.println("Warning: unknow language");
    	return PossibleLang.Unknown;
    }
    
}

