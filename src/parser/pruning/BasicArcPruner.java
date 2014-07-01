package parser.pruning;

import java.io.IOException;

import parser.DependencyInstance;
import parser.DependencyParser;
import parser.DependencyPipe;
import parser.GlobalFeatureData;
import parser.LocalFeatureData;
import parser.LowRankParam;
import parser.Options;
import parser.Parameters;
import parser.Options.LearningMode;
import utils.FeatureVector;

public class BasicArcPruner extends DependencyParser {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	
	@Override
	public void train(DependencyInstance[] lstTrain) 
	    	throws IOException, CloneNotSupportedException 
	{
    	long start = 0, end = 0;
    	        
        if (options.R > 0 && options.gamma < 1 && options.initTensorWithPretrain) {

        	Options optionsBak = (Options) options.clone();
        	options.average = false;
        	options.learningMode = LearningMode.Basic;
        	options.R = 0;
        	options.gamma = 1.0;
        	options.gammaLabel = 1.0;
        	options.maxNumIters = options.numPretrainIters;
            options.useHO = false;
        	parameters.gamma = 1.0;
        	parameters.gammaLabel = 1.0;
        	parameters.rank = 0;
    		System.out.println("=============================================");
    		System.out.printf(" Pre-training:%n");
    		System.out.println("=============================================");
    		
    		start = System.currentTimeMillis();

    		System.out.println("Running MIRA ... ");
    		//trainIter(lstTrain, false);
    		trainIter(lstTrain);
    		System.out.println();
    		
    		System.out.println("Init tensor ... ");
    		LowRankParam tensor = new LowRankParam(parameters);
    		pipe.fillParameters(tensor, parameters);
    		tensor.decompose(1, parameters);
            System.out.println();
    		end = System.currentTimeMillis();
    		
    		options.learningMode = optionsBak.learningMode;
    		options.average = optionsBak.average;
    		options.R = optionsBak.R;
    		options.gamma = optionsBak.gamma;
    		options.gammaLabel = optionsBak.gammaLabel;
    		options.maxNumIters = optionsBak.maxNumIters;
            options.useHO = optionsBak.useHO;
    		parameters.rank = optionsBak.R;
    		parameters.gamma = optionsBak.gamma;
    		parameters.gammaLabel = optionsBak.gammaLabel;
    		parameters.clearTheta();
            parameters.printUStat();
            parameters.printVStat();
            parameters.printWStat();
            System.out.println();
            System.out.printf("Pre-training took %d ms.%n", end-start);    		
    		System.out.println("=============================================");
    		System.out.println();	    

        } else {
        	parameters.randomlyInitUVW();
        }

        System.out.println("=============================================");
		System.out.printf(" Training Pruner:%n");
		System.out.println("=============================================");
		
		start = System.currentTimeMillis();

		System.out.println("Running MIRA ... ");
		trainIter(lstTrain);
		System.out.println();
		
		end = System.currentTimeMillis();
		
		System.out.printf("Training took %d ms.%n", end-start);    		
		System.out.println("=============================================");
		System.out.println();		    	
	}
	
	public void trainIter(DependencyInstance[] lstTrain) throws IOException
	{
		int N = lstTrain.length;
		int updCnt = 0;
    	
		for (int iIter = 0; iIter < options.maxNumIters; ++iIter) {
			
			long start = 0;
			double loss = 0;
			int uas = 0, tot = 0;
			start = System.currentTimeMillis();
			
            int offset = (N % 3 == 0) ? iIter : 0;

            for (int i = 0; i < N; ++i) {
    			
				DependencyInstance inst = lstTrain[i];
				DependencyInstance predInst = new DependencyInstance(inst);
				predInst.heads = new int[inst.length];
				predInst.deplbids = new int[inst.length];
				
				for (int m = 0; m < inst.length; ++m) {
					predInst.heads[m] = inst.heads[m];
					predInst.deplbids[m] = inst.deplbids[m];
				}

				LocalFeatureData lfd = new LocalFeatureData(inst, this, true);
    		    int n = inst.length;
    		    
    		    for (int m = 1; m < n; ++m) {
    		    	
    		    	int goldhead = inst.heads[m];
    		    	//FeatureVector goldfv = lfd.getArcFeatureVector(goldhead, m);
    		    	//double goldscore = parameters.dotProduct(goldfv);
    		    	
    		    	int predhead = -1;
    		    	//FeatureVector predfv = null;
    		    	double best = Double.NEGATIVE_INFINITY;
    		    	
    		    	for (int h = 0; h < n; ++h)
    		    		if (h != m) {
    		    			
    		    			double va = parameters.dotProduct(lfd.arcFvs[h*n+m]) * options.gamma
							+ parameters.dotProduct(lfd.wpU[h], lfd.wpV[m], h-m) * (1-options.gamma)
							+ (h!= goldhead ? 1.0 : 0.0);
    		    			
    		    			//double va = parameters.dotProduct(fv)
    		    			//		+ (h != goldhead ? 1.0 : 0.0);
    		    			if (va > best) {
    		    				best = va;
    		    				predhead = h;
    		    				//predfv = fv;
    		    			}
    		    		}
    		    	
    		    	if (goldhead != predhead) {
    		    		++updCnt;
    		    		//loss += best - goldscore;
    		    		//parameters.updateTheta(goldfv, predfv, best - goldscore, updCnt);
    		    		predInst.heads[m] = predhead;
    		    		loss += parameters.updateLocal(inst, predInst, m, lfd, updCnt, i, offset);
    		    	} else ++uas;
    		    	
    		    	predInst.heads[m] = goldhead;
    		    	++tot;
    		    }
			}
			
    		System.out.printf("  Iter %d\tloss=%.4f\tuas=%.4f\t[%ds]%n", iIter+1,
    				loss, uas/(tot+0.0),
    				(System.currentTimeMillis() - start)/1000);
    		
    		{
    			System.out.println();
	  			System.out.println("_____________________________________________");
	  			System.out.println();
	  			System.out.printf(" Evaluation: %s%n", options.testFile);
	  			System.out.println(); 
                if (options.average) 
                	parameters.averageParameters(updCnt);
	  			double res = evaluateSet(false, false);
                System.out.println();
	  			System.out.println("_____________________________________________");
	  			System.out.println();
                if (options.average) 
                	parameters.unaverageParameters();
    		}
    		
    	}
    	
		if (options.average)
			parameters.averageParameters(updCnt);

	}
	 	
}
