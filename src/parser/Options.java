package parser;


public class Options {
	
	public enum LearningMode {
		Basic,			// 1st order arc factored model
		Standard,		// 2nd order model (3rd order?)
		Full			// full model with global features
	}
	
	public String trainFile = null;
	public String testFile = null;
	public String outFile = null;
	public boolean train = false;
	public boolean test = false;	
	public String wordVectorFile = null;
	public String modelFile = "model.out";
    public String format = "CONLL";
    
    
    //public boolean evalWithPunc = true;
	public int maxNumSent = -1;
    public int numPretrainIters = 1;
	public int maxNumIters = 15;
	
	public LearningMode learningMode = LearningMode.Basic;
	public boolean projective = false;
	public boolean learnLabel = false;
	public boolean pruning = false;
	public double pruningCoeff = 0.005;
	
	public boolean average = false;
	public double C = 1;
	public double gamma = 1;
	public int R = 50;
    
	public Options() {
		
	}
	
    public void processArguments(String[] args) {
    	
    	for (String arg : args) {
    		if (arg.equals("train")) {
    			train = true;
    		}
    		else if (arg.equals("test")) {
    			test = true;
    		}
    		else if (arg.equals("label")) {
    			learnLabel = true;
    		}
            else if (arg.equals("non-proj")) {
                projective = false;
            }
            else if (arg.equals("average")) {
            	average = true;
            }
    		else if (arg.startsWith("train-file:")) {
    			trainFile = arg.split(":")[1];
    		}
    		else if (arg.startsWith("test-file:")) {
    			testFile = arg.split(":")[1];
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
            }
            else if (arg.startsWith("R:")) {
                R = Integer.parseInt(arg.split(":")[1]);
                //Parameters.rank = R;
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

    	}    	

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
        System.out.println("gamma: " + gamma);
        System.out.println("R: " + R);
        System.out.println("word-vector:" + wordVectorFile);
        System.out.println("projective: " + projective);

    	System.out.println("------\n");
    }
    
}

