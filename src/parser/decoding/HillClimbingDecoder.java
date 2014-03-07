package parser.decoding;

import parser.DependencyInstance;
import parser.GlobalFeatureData;
import parser.LocalFeatureData;
import parser.Options;
import parser.sampling.RandomWalkSampler;

public class HillClimbingDecoder extends DependencyDecoder {
	
	RandomWalkSampler sampler;
	
	DependencyInstance pred, inst;
	LocalFeatureData lfd;
	GlobalFeatureData gfd;
	boolean addLoss;
	
	double bestScore;	
	int unchangedRuns, totRuns;
	
	public HillClimbingDecoder(Options options) {
		this.options = options;
		sampler = new RandomWalkSampler();
	}

	@Override
	public DependencyInstance decode(DependencyInstance inst,
			LocalFeatureData lfd, GlobalFeatureData gfd, boolean addLoss) {
		
		lfd.initArcPruningMap(addLoss);
		
		this.inst = inst;
		this.lfd = lfd;
		this.gfd = gfd;
		this.addLoss = addLoss;
		bestScore = Double.NEGATIVE_INFINITY;
		pred = null;
		totRuns = 0;
		unchangedRuns = 0;
		
		HillClimbingThread[] lstThreads = new HillClimbingThread[options.numHcThreads];
		for (int i = 0; i < lstThreads.length; ++i) {
			lstThreads[i] = new HillClimbingThread();
			lstThreads[i].start();
		}
		
		for (int i = 0; i < lstThreads.length; ++i)
			try {
				lstThreads[i].join();
			} catch (InterruptedException e) {
				System.out.println("Hill climbing thread interupted!!!!");
			}
		
		return pred;		
	}
	
	public class HillClimbingThread extends Thread {
		
		public void run()
		{
			int n = inst.length;
			int converge = options.numHcConverge;
			
			while (unchangedRuns < converge) {
				DependencyInstance now = sampler.randomWalkSampling(
						inst, lfd, addLoss);
				
				// hill climb
				int[] heads = now.heads;				
				boolean more = false;
				for (;;) {
					
				}
				
			}
		}
		
	}

}
