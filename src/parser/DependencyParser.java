package parser;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import parser.decoding.DependencyDecoder;
import parser.io.DependencyReader;

public class DependencyParser {
	
	Options options;
	DependencyPipe pipe;
	Parameters parameters;
	
	DependencyParser pruner;
	
	public static void main(String[] args) throws IOException, ClassNotFoundException {
		
		Options options = new Options();
		options.processArguments(args);
		options.printOptions();
		
		if (options.train) {
			DependencyParser parser = new DependencyParser();
			parser.options = options;
			
			DependencyPipe pipe = new DependencyPipe(options);
			parser.pipe = pipe;
			
			pipe.createAlphabets(options.trainFile);
			DependencyInstance[] lstTrain = pipe.createInstances(options.trainFile);
			
			Parameters parameters = new Parameters(pipe, options);
			parser.parameters = parameters;
			
			parser.train(lstTrain);
			parser.saveModel();
		}
		
		if (options.test) {
			DependencyParser parser = new DependencyParser();
			parser.options = options;
			
			parser.loadModel();
			
			if (!options.train && options.wordVectorFile != null)
            	parser.pipe.loadWordVectors(options.wordVectorFile);
			
			System.out.printf(" Evaluating: %s%n", options.testFile);
			parser.evaluateSet(true, false);
		}
		
	}
	
    public void saveModel() throws IOException {
    	ObjectOutputStream out = new ObjectOutputStream(
    			new GZIPOutputStream(new FileOutputStream(options.modelFile)));
    	out.writeObject(pipe);
    	out.writeObject(parameters);
    	out.close();
    }
	
    public void loadModel() throws IOException, ClassNotFoundException {
        ObjectInputStream in = new ObjectInputStream(
                new GZIPInputStream(new FileInputStream(options.modelFile)));    
        pipe = (DependencyPipe) in.readObject();
        parameters = (Parameters) in.readObject();
        //pipe.options = options;
        //parameters.options = options;
        in.close();
        pipe.closeAlphabets();
    }
	
    public void train(DependencyInstance[] lstTrain) throws IOException 
    {
    	long start = 0, end = 0;
    	
//        if (options.R > 0 && initTensorWithPretrain) {
//        	//parameters.randomlyInitUVW();
//        	int backupR = options.R, backupIters = options.maxNumIters;
//        	double backupGamma = options.gamma;
//        	options.R = 0;
//        	options.gamma = 1.0;
//        	options.maxNumIters = options.numPretrainIters;
//        	parameters.gamma = 1.0;
//        	parameters.rank = 0;
//    		System.out.println("=============================================");
//    		System.out.printf(" Pre-training:%n");
//    		System.out.println("=============================================");
//    		
//    		start = System.currentTimeMillis();
//
//    		System.out.println("Running MIRA ... ");
//    		trainIter(lstTrain, parameters, pipe, false);
//    		System.out.println();
//    		
//    		System.out.println("Init tensor ... ");
//    		LowRankParam tensor = new LowRankParam(parameters);
//    		pipe.fillParameters(tensor, parameters);
//    		tensor.decompose(1, parameters);
//            System.out.println();
//    		end = System.currentTimeMillis();
//    		
//    		options.R = backupR;
//    		options.gamma = backupGamma;
//    		options.maxNumIters = backupIters;
//    		parameters.rank = backupR;
//    		parameters.gamma = backupGamma;
//    		parameters.clearTheta();
//            parameters.printUStat();
//            parameters.printVStat();
//            parameters.printWStat();
//            System.out.println();
//            System.out.printf("Pre-training took %d ms.%n", end-start);    		
//    		System.out.println("=============================================");
//    		System.out.println();	    
//
//        } else {
        	parameters.randomlyInitUVW();
//        }
        
		System.out.println("=============================================");
		System.out.printf(" Training:%n");
		System.out.println("=============================================");
		
		start = System.currentTimeMillis();

		System.out.println("Running MIRA ... ");
		trainIter(lstTrain, true);
		System.out.println();
		
		end = System.currentTimeMillis();
		
		System.out.printf("Training took %d ms.%n", end-start);    		
		System.out.println("=============================================");
		System.out.println();		    	
    }
    
    public void trainIter(DependencyInstance[] lstTrain, boolean evalAndSave) throws IOException
    {
    	DependencyDecoder decoder = DependencyDecoder
    			.createDependencyDecoder(options);
    	
    	int N = lstTrain.length;
    	
    	for (int iIter = 0; iIter < options.maxNumIters; ++iIter) {
    	    
            // use this offset to change the udpate ordering of U, V and W
            // when N is a multiple of 3, such that U, V and W get updated
            // on each sentence.
            int offset = (N % 3 == 0) ? iIter : 0;

    		long start = 0;
    		double loss = 0;
    		int uas = 0, tot = 0;
    		start = System.currentTimeMillis();
                		    		
    		for (int i = 0; i < N; ++i) {
    			
    			DependencyInstance inst = new DependencyInstance(lstTrain[i]);
    			LocalFeatureData lfd = new LocalFeatureData(inst, this, true);
    		    GlobalFeatureData gfd = null;
    		    int n = inst.length;
    		    
    		    DependencyInstance predInst = decoder.decode(inst, lfd, gfd, true);

        		int ua = evaluateUnlabelCorrect(inst, predInst), la = 0;
        		if (options.learnLabel)
        			la = evaluateLabelCorrect(inst, predInst);        		
        		uas += ua;
        		tot += n-1;
        		
        		if ((options.learnLabel && la != n-1) ||
        				(!options.learnLabel && ua != n-1)) {
        			loss += parameters.update(inst, predInst, lfd, gfd,
        					iIter * N + i + 1, offset);
                }

    		}
    		System.out.printf("  Iter %d\tloss=%.4f\tuas=%.4f\t[%d ms]%n", iIter+1,
    				loss, uas/(tot+0.0),
    				System.currentTimeMillis() - start);

    		// evaluate on a development set
    		if (evalAndSave && options.test && ((iIter+1) % 100 == 0 || iIter+1 == options.maxNumIters)) {		
    			System.out.println();
	  			System.out.println("_____________________________________________");
	  			System.out.println();
	  			System.out.printf(" Evaluation: %s%n", options.testFile);
	  			System.out.println(); 
                if (options.average) 
                	parameters.averageParameters((iIter+1)*N);
	  			double res = evaluateSet(false, false);
                System.out.println();
	  			System.out.println("_____________________________________________");
	  			System.out.println();
                if (options.average) 
                	parameters.unaverageParameters();
    		} 
    	}
    	
    	if (evalAndSave && options.average) {
            parameters.averageParameters(options.maxNumIters * N);
    	}
    }
    
    public int evaluateUnlabelCorrect(DependencyInstance act, DependencyInstance pred) 
    {
    	int nCorrect = 0;
    	for (int i = 1, N = act.length; i < N; ++i) {
    		if (act.heads[i] == pred.heads[i])
    			++nCorrect;
    	}    		
    	return nCorrect;
    }
    
    public int evaluateLabelCorrect(DependencyInstance act, DependencyInstance pred) 
    {
    	int nCorrect = 0;
    	for (int i = 1, N = act.length; i < N; ++i) {
    		if (act.heads[i] == pred.heads[i] && act.deplbids[i] == pred.deplbids[i])
    			++nCorrect;
    	}    		  		
    	return nCorrect;
    }
    
    public static int evaluateUnlabelCorrect(DependencyInstance inst, 
    		DependencyInstance pred, boolean evalWithPunc) 
    {
    	int nCorrect = 0;    	
    	for (int i = 1, N = inst.length; i < N; ++i) {

            if (!evalWithPunc)
            	if (inst.forms[i].matches("[-!\"#%&'()*,./:;?@\\[\\]_{}、]+")) continue;

    		if (inst.heads[i] == pred.heads[i]) ++nCorrect;
    	}    		
    	return nCorrect;
    }
    
    public static int evaluateLabelCorrect(DependencyInstance inst, 
    		DependencyInstance pred, boolean evalWithPunc) 
    {
    	int nCorrect = 0;    	
    	for (int i = 1, N = inst.length; i < N; ++i) {

            if (!evalWithPunc)
            	if (inst.forms[i].matches("[-!\"#%&'()*,./:;?@\\[\\]_{}、]+")) continue;

    		if (inst.heads[i] == pred.heads[i] && inst.deplbids[i] == pred.deplbids[i]) ++nCorrect;
    	}    		
    	return nCorrect;
    }
    
    public double evaluateSet(boolean output, boolean evalWithPunc)
    		throws IOException {
    	
    	//pruner = this;
    	
    	DependencyReader reader = DependencyReader.createDependencyReader(options);
    	reader.startReading(options.testFile);
    	
    	BufferedWriter out = null;
    	if (output && options.outFile != null) {
    		out = new BufferedWriter(
    			new OutputStreamWriter(new FileOutputStream(options.outFile), "UTF8"));
    	}
    	
    	DependencyDecoder decoder = DependencyDecoder.createDependencyDecoder(options);   	
    	int nUCorrect = 0, nLCorrect = 0;
    	int nDeps = 0, nWhole = 0, nSents = 0;
    	
    	DependencyInstance inst = pipe.createInstance(reader);    	
    	while (inst != null) {
    		LocalFeatureData lfd = new LocalFeatureData(inst, this, false);
    		GlobalFeatureData gfd = null; 
    		//lfd.initArcPruningMap(true);
    		
    		++nSents;
            
            int nToks = 0;
            if (evalWithPunc)
    		    nToks = (inst.length - 1);
            else {
                for (int i = 1; i < inst.length; ++i) {
                	if (inst.forms[i].matches("[-!\"#%&'()*,./:;?@\\[\\]_{}、]+")) continue;
                    ++nToks;
                }
            }
            nDeps += nToks;
    		    		
            DependencyInstance predInst = decoder.decode(inst, lfd, gfd, false);

    		int ua = evaluateUnlabelCorrect(inst, predInst, evalWithPunc), la = 0;
    		if (options.learnLabel)
    			la = evaluateLabelCorrect(inst, predInst, evalWithPunc);
    		nUCorrect += ua;
    		nLCorrect += la;
    		if ((options.learnLabel && la == nToks) ||
    				(!options.learnLabel && ua == nToks)) 
    			++nWhole;
    		
    		if (out != null) {
    			int[] deps = predInst.heads, labs = predInst.deplbids;
    			String line1 = "", line2 = "", line3 = "", line4 = "";
    			for (int i = 1; i < inst.length; ++i) {
    				line1 += inst.forms[i] + "\t";
    				line2 += inst.postags[i] + "\t";
    				line3 += (options.learnLabel ? pipe.types[labs[i]] : labs[i]) + "\t";
    				line4 += deps[i] + "\t";
    			}
    			out.write(line1.trim() + "\n" + line2.trim() + "\n" + line3.trim() + "\n" + line4.trim() + "\n\n");
    		}
    		
    		inst = pipe.createInstance(reader);
    	}
    	
    	reader.close();
    	if (out != null) out.close();
    	
    	//LocalFeatureData.printPruningStats();
    	System.out.printf(" Tokens: %d%n", nDeps);
    	System.out.printf(" Sentences: %d%n", nSents);
    	System.out.printf(" UAS=%.6f\tLAS=%.6f\tCAS=%.6f%n",
    			(nUCorrect+0.0)/nDeps,
    			(nLCorrect+0.0)/nDeps,
    			(nWhole + 0.0)/nSents);
    	
    	return (nUCorrect+0.0)/nDeps;
    }
}
