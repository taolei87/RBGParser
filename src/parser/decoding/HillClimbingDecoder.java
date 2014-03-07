package parser.decoding;

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
	boolean stopped;
	
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
		int[] st, ed, edges, dfslis;
		
		public void run()
		{
			n = inst.length;
			converge = options.numHcConverge;
			st = new int[n];
			ed = new int[n];
			dfslis = new int[n];
			edges = new int[n*n];
			
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
						
						for (int h = 0; h < n; ++h)
							if (h != m && h != bestHead && !lfd.isPruned(h, m)) {
								heads[m] = h;
								double score = calcScore(heads, m);
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
				
				double score = calcScore(heads, deplbids);
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
		
		private double calcScore(int[] heads, int m)
		{
			return ((addLoss && heads[m] != inst.heads[m]) ? 1 : 0);
			//		+ lfd.getScore(heads, m)
			//		+ gfd.getScore(heads, m);
		}
		
		private double calcScore(int[] heads, int[] deplbids) 
		{
			//TODO
			return 0;
		}
		
		private void depthFirstSearch(int[] heads)
		{
			for (int i = 0; i < n; ++i)
				ed[i] = 0;
			
			for (int i = 1; i < n; ++i) {
				int j = heads[i];
				++ed[j];
			}
			
			st[0] = 0;
			for (int i = 1; i < n; ++i) {
				st[i] = ed[i-1];
				ed[i] += ed[i-1];
			}
			
			Utils.Assert(ed[n-1] == n-1);
			
			for (int i = 1; i < n; ++i) {
				int j = heads[i];
				edges[st[j]] = i;
				++st[j];
			}
			size = 0;
			dfs(0);
		}
		
		private void dfs(int i)
		{
			int l = i == 0 ? 0 : ed[i-1];
			int r = ed[i];
			for (int p = l; p < r; ++p) {
				int j = edges[p];
				dfs(j);
				dfslis[size++] = j;
			}
		}
		
	}

}
