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

public class BasicArcPruner extends DependencyParser {
	
	Options options;
	DependencyPipe pipe;
	Parameters parameters;
	
	double pruningGoldHits = 0;
	double pruningTotGold = 1e-30;
	double pruningTotUparcs = 0;
	double pruningTotArcs = 1e-30;
	
	@Override
	public void train(DependencyInstance[] lstTrain) 
	    	throws IOException, CloneNotSupportedException 
	{
    	long start = 0, end = 0;
    	        
		System.out.println("=============================================");
		System.out.printf(" Training:%n");
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
    	
    	for (int iIter = 0; iIter < options.maxNumIters; ++iIter) {
			
			long start = 0;
			double loss = 0;
			int uas = 0, tot = 0;
			start = System.currentTimeMillis();
			
			for (int i = 0; i < N; ++i) {
    			
				DependencyInstance inst = lstTrain[i];
    			LocalFeatureData lfd = new LocalFeatureData(inst, this, true);
    		    int n = inst.length;
    		    
    		    for (int m = 1; m < n; ++m) {
    		    	
    		    }
			}
    		
    	}

	}
	 	
}
