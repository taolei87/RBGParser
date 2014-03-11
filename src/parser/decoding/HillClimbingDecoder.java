package parser.decoding;

import parser.DependencyArcList;
import parser.DependencyInstance;
import parser.GlobalFeatureData;
import parser.LocalFeatureData;
import parser.Options;
import parser.sampling.RandomWalkSampler;
import utils.Utils;

public class HillClimbingDecoder extends DependencyDecoder {
	
	RandomWalkSampler sampler;
	
	DependencyInstance pred, inst;
	LocalFeatureData lfd;
	GlobalFeatureData gfd;
	boolean addLoss;
	
	int[][] staticTypes;
	
	double bestScore;	
	int unchangedRuns, totRuns;
	volatile boolean stopped;
	
	public HillClimbingDecoder(Options options) {
		this.options = options;
		sampler = new RandomWalkSampler();
	}

	@Override
	public DependencyInstance decode(DependencyInstance inst,
			LocalFeatureData lfd, GlobalFeatureData gfd, boolean addLoss) {
		
		//lfd.initArcPruningMap(addLoss);
		
		this.inst = inst;
		this.lfd = lfd;
		this.gfd = gfd;
		this.addLoss = addLoss;
		bestScore = Double.NEGATIVE_INFINITY;
		pred = new DependencyInstance(inst);
		totRuns = 0;
		unchangedRuns = 0;
		stopped = false;
		if (options.learnLabel)
			staticTypes = lfd.getStaticTypes();
		
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
		
		int n, converge;
		int size;
		DependencyArcList arcLis;
		int[] dfslis;
		
		public void run()
		{
			n = inst.length;
			converge = options.numHcConverge;
			dfslis = new int[n];
			arcLis = new DependencyArcList(n);

			while (!stopped) {
				DependencyInstance now = sampler.randomWalkSampling(
						inst, lfd, addLoss);
				
				// hill climb
				int[] heads = now.heads;
				int[] deplbids = now.deplbids;
				
				boolean more;
				for (;;) {
					more = false;					
					depthFirstSearch(heads);
					Utils.Assert(size == n-1);
					for (int i = 0; i < size; ++i) {
						int m = dfslis[i];
						
						int bestHead = heads[m];
						double maxScore = calcScore(heads, m);
						//double maxScore = calcScore(now);
						
						for (int h = 0; h < n; ++h)
							if (h != m && h != bestHead && !lfd.isPruned(h, m)
								&& !isAncestorOf(heads, m, h)) {
								heads[m] = h;
								double score = calcScore(heads, m);
								//double score = calcScore(now);
								if (score > maxScore) {
									more = true;
									bestHead = h;
									maxScore = score;									
								}
							}
						heads[m] = bestHead;
					}
					if (!more) break;					
				}
				
				if (options.learnLabel) {
					for (int m = 1; m < n; ++m)
						deplbids[m] = staticTypes[heads[m]][m];
				}
				
				double score = calcScore(now);
				synchronized (pred) {
					++totRuns;
					if (score > bestScore) {
						bestScore = score;
						unchangedRuns = 0;
						pred.heads = heads;
						pred.deplbids = deplbids;
					} else {
						++unchangedRuns;
						if (unchangedRuns >= converge)
							stopped = true;
					}
					
				}
			}
		}
		
		private boolean isAncestorOf(int[] heads, int par, int ch) 
		{
			while (ch != 0) {
				if (ch == par) return true;
				ch = heads[ch];
			}
			return false;
		}
		
		private double calcScore(int[] heads, int m)
		{
			return ((addLoss && heads[m] != inst.heads[m]) ? 1 : 0)
					+ (options.learnLabel ? 
						lfd.getLabeledArcScore(heads[m], m, staticTypes[heads[m]][m])
						: 0.0)
					+ lfd.getPartialScore(heads, m);
			//		+ gfd.getStructureScore(heads, m);
		}
		
		private double calcScore(DependencyInstance now) 
		{
			double score = 0;
			int[] heads = now.heads;
			int[] deplbids = now.deplbids;
			for (int m = 1; m < n; ++m)
				score += lfd.getLabeledArcScore(heads[m], m, deplbids[m])
					   + ((addLoss && heads[m] != inst.heads[m]) ? 1 : 0);
			 
			score += lfd.getScore(now);
			return score;
		}
		
		private void depthFirstSearch(int[] heads)
		{
			arcLis.constructDepTreeArcList(heads);
			size = 0;
			dfs(0);
		}
		
		
		private void dfs(int i)
		{
			int l = arcLis.startIndex(i);
			int r = arcLis.endIndex(i);
			for (int p = l; p < r; ++p) {
				int j = arcLis.get(p);
				dfs(j);
				dfslis[size++] = j;
			}
		}
		
	}

}
