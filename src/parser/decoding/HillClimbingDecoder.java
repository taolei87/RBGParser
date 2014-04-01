package parser.decoding;

import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;


import parser.DependencyArcList;
import parser.DependencyInstance;
import parser.GlobalFeatureData;
import parser.LocalFeatureData;
import parser.Options;
import parser.sampling.RandomWalkSampler;
import utils.Utils;

public class HillClimbingDecoder extends DependencyDecoder {
	
	DependencyInstance pred, inst;
	LocalFeatureData lfd;
	GlobalFeatureData gfd;
	boolean addLoss;
	
	int[][] staticTypes;
	
	double bestScore;	
	int unchangedRuns, totRuns;
	volatile boolean stopped;
    
    ExecutorService executorService;
	ExecutorCompletionService<Object> decodingService;
	HillClimbingTask[] tasks;
	
	public HillClimbingDecoder(Options options) {
		this.options = options;
		totalLoopCount = 0;
		totalClimbTime = 0;
		totalClimbAndSampleTime = 0;
        executorService = Executors.newFixedThreadPool(options.numHcThreads);
		decodingService = new ExecutorCompletionService<Object>(executorService);
		tasks = new HillClimbingTask[options.numHcThreads];
		for (int i = 0; i < tasks.length; ++i) {
			tasks[i] = new HillClimbingTask();
			tasks[i].sampler = new RandomWalkSampler(i, options);
		}
	}
   
   @Override
    public void shutdown()
    {
        //System.out.println("shutdown");
        executorService.shutdownNow();
    }

	@Override
	public DependencyInstance decode(DependencyInstance inst,
			LocalFeatureData lfd, GlobalFeatureData gfd, boolean addLoss) {
				
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
		
		for (int i = 0; i < tasks.length; ++i) {
			tasks[i].sampler.loopCount = 0;
			decodingService.submit(tasks[i], null);			
		}
		
		for (int i = 0; i < tasks.length; ++i) {
			try {
				decodingService.take();
			} catch (InterruptedException e) {
				System.out.println("Hill climbing thread interupted!!!!");
			}
		}
		
		for (int i = 0; i < tasks.length; ++i) {
			totalLoopCount += tasks[i].sampler.loopCount / (totRuns + 0.0);
			totalClimbTime += tasks[i].climbTime;
			totalClimbAndSampleTime += tasks[i].climbAndSampleTime;
		}
		
//		HillClimbingThread[] lstThreads = new HillClimbingThread[options.numHcThreads];
//		for (int i = 0; i < lstThreads.length; ++i) {
//			lstThreads[i] = new HillClimbingThread();
//			lstThreads[i].sampler = new RandomWalkSampler(i + 10, options);
//			lstThreads[i].start();
//		}
//		
//		for (int i = 0; i < lstThreads.length; ++i)
//			try {
//				lstThreads[i].join();
//				totalLoopCount += lstThreads[i].sampler.loopCount / (totRuns + 0.0);
//				totalClimbTime += lstThreads[i].climbTime;
//				totalClimbAndSampleTime += lstThreads[i].climbAndSampleTime;
//			} catch (InterruptedException e) {
//				System.out.println("Hill climbing thread interupted!!!!");
//			}
		
		return pred;		
	}
	
	public class HillClimbingTask implements Runnable {
		
		RandomWalkSampler sampler;
		int converge;
		
		long climbTime, climbAndSampleTime;
		int n, size;
		int[] dfslis;
		DependencyArcList arcLis;
				
		@Override
		public void run() {
			
			n = inst.length;
			converge = options.numHcConverge;
			
			if (dfslis == null || dfslis.length < n) {
				dfslis = new int[n];				
			}
			if (arcLis == null)
				arcLis = new DependencyArcList(n);
			else
				arcLis.resize(n);
            
			climbTime = 0;
			climbAndSampleTime = 0;
			
			while (!stopped) {
				
				long startClimbAndSample = System.currentTimeMillis();

				DependencyInstance now = sampler.randomWalkSampling(
						inst, lfd, staticTypes, addLoss);
				
				long startClimb = System.currentTimeMillis();
				// hill climb
				int[] heads = now.heads;
				int[] deplbids = now.deplbids;
			    
                int cnt = 0;
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

                    //DEBUG
                    //++cnt;
                    //if (cnt % 10000 == 0) {
                    //    System.out.println(cnt);
                    //}
				}
				
				long end = System.currentTimeMillis();
				climbTime += end - startClimb;
				climbAndSampleTime += end - startClimbAndSample;
				
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
            //int cnt = 0;
			while (ch != 0) {
				if (ch == par) return true;
				ch = heads[ch];

                //DEBUG
                //++cnt;
                //if (cnt > 10000) {
                //    System.out.println("DEAD LOOP in isAncestorOf !!!!");
                //    System.exit(1);
                //}
			}
			return false;
		}
		
		private double calcScore(int[] heads, int m)
		{
			return ((addLoss && heads[m] != inst.heads[m]) ? 1 : 0)
					+ (options.learnLabel ? 
						lfd.getLabeledArcScore(heads[m], m, staticTypes[heads[m]][m])
						: 0.0)
                    + (addLoss && options.learnLabel && staticTypes[heads[m]][m]
                        != inst.deplbids[m] ? 1 : 0)
					+ lfd.getPartialScore(heads, m)
					+ gfd.getScore(inst);
		}
		
		private double calcScore(DependencyInstance now) 
		{
			double score = 0;
			int[] heads = now.heads;
			int[] deplbids = now.deplbids;
			for (int m = 1; m < n; ++m) 
				score += (options.learnLabel ? 
                            lfd.getLabeledArcScore(heads[m], m, deplbids[m])
                            : 0)
					   + ((addLoss && heads[m] != inst.heads[m]) ? 1 : 0)
                       + ((addLoss && options.learnLabel && 
                            staticTypes[heads[m]][m] != inst.deplbids[m]) ?
                            1 : 0);
			 
			score += lfd.getScore(now);
			score += gfd.getScore(now);	
			return score;
		}
		
		private void depthFirstSearch(int[] heads)
		{
			arcLis.constructDepTreeArcList(heads);
			size = 0;
            //dfscnt = 0;
			dfs(0);
		}
		
		
		private void dfs(int i)
		{
            //DEBUG
            //++dfscnt;
            //if (dfscnt > 10000) {
            //    System.out.println("DEAD LOOP in dfs!!!!");
            //    System.exit(1);
            //}

			int l = arcLis.startIndex(i);
			int r = arcLis.endIndex(i);
			for (int p = l; p < r; ++p) {
				int j = arcLis.get(p);
				dfs(j);
				dfslis[size++] = j;
			}
		}
		
	}
	
//	public class HillClimbingThread extends Thread {
//		
//		RandomWalkSampler sampler;
//
//		int n, converge;
//		int size;
//		DependencyArcList arcLis;
//		int[] dfslis;
//	    int dfscnt;
//
//		public long climbTime;
//		public long climbAndSampleTime;
//
//		public void run()
//		{
//			n = inst.length;
//			converge = options.numHcConverge;
//			dfslis = new int[n];
//			arcLis = new DependencyArcList(n);
//            
//			climbTime = 0;
//			climbAndSampleTime = 0;
//
//			while (!stopped) {
//				
//				long startClimbAndSample = System.currentTimeMillis();
//
//				DependencyInstance now = sampler.randomWalkSampling(
//						inst, lfd, staticTypes, addLoss);
//				
//				long startClimb = System.currentTimeMillis();
//				// hill climb
//				int[] heads = now.heads;
//				int[] deplbids = now.deplbids;
//			    
//                int cnt = 0;
//				boolean more;
//				for (;;) {
//					more = false;					
//					depthFirstSearch(heads);
//					Utils.Assert(size == n-1);
//					for (int i = 0; i < size; ++i) {
//						int m = dfslis[i];
//						
//						int bestHead = heads[m];
//						double maxScore = calcScore(heads, m);
//						//double maxScore = calcScore(now);
//						
//						for (int h = 0; h < n; ++h)
//							if (h != m && h != bestHead && !lfd.isPruned(h, m)
//								&& !isAncestorOf(heads, m, h)) {
//								heads[m] = h;
//								double score = calcScore(heads, m);
//								//double score = calcScore(now);
//								if (score > maxScore) {
//									more = true;
//									bestHead = h;
//									maxScore = score;									
//								}
//							}
//						heads[m] = bestHead;
//					}
//					if (!more) break;					
//
//                    //DEBUG
//                    //++cnt;
//                    //if (cnt % 10000 == 0) {
//                    //    System.out.println(cnt);
//                    //}
//				}
//				
//				long end = System.currentTimeMillis();
//				climbTime += end - startClimb;
//				climbAndSampleTime += end - startClimbAndSample;
//				
//				if (options.learnLabel) {
//					for (int m = 1; m < n; ++m)
//						deplbids[m] = staticTypes[heads[m]][m];
//				}
//				
//				double score = calcScore(now);
//				synchronized (pred) {
//					++totRuns;
//					if (score > bestScore) {
//						bestScore = score;
//						unchangedRuns = 0;
//						pred.heads = heads;
//						pred.deplbids = deplbids;
//					} else {
//						++unchangedRuns;
//						if (unchangedRuns >= converge)
//							stopped = true;
//					}
//					
//				}
//			}
//		}
//		
//		private boolean isAncestorOf(int[] heads, int par, int ch) 
//		{
//            //int cnt = 0;
//			while (ch != 0) {
//				if (ch == par) return true;
//				ch = heads[ch];
//
//                //DEBUG
//                //++cnt;
//                //if (cnt > 10000) {
//                //    System.out.println("DEAD LOOP in isAncestorOf !!!!");
//                //    System.exit(1);
//                //}
//			}
//			return false;
//		}
//		
//		private double calcScore(int[] heads, int m)
//		{
//			return ((addLoss && heads[m] != inst.heads[m]) ? 1 : 0)
//					+ (options.learnLabel ? 
//						lfd.getLabeledArcScore(heads[m], m, staticTypes[heads[m]][m])
//						: 0.0)
//                    + (addLoss && options.learnLabel && staticTypes[heads[m]][m]
//                        != inst.deplbids[m] ? 1 : 0)
//					+ lfd.getPartialScore(heads, m);
//			//		+ gfd.getStructureScore(heads, m);
//		}
//		
//		private double calcScore(DependencyInstance now) 
//		{
//			double score = 0;
//			int[] heads = now.heads;
//			int[] deplbids = now.deplbids;
//			for (int m = 1; m < n; ++m)
//				score += (options.learnLabel ? 
//                            lfd.getLabeledArcScore(heads[m], m, deplbids[m])
//                            : 0)
//					   + ((addLoss && heads[m] != inst.heads[m]) ? 1 : 0)
//                       + ((addLoss && options.learnLabel && 
//                            staticTypes[heads[m]][m] != inst.deplbids[m]) ?
//                            1 : 0);
//			 
//			score += lfd.getScore(now);
//			return score;
//		}
//		
//		private void depthFirstSearch(int[] heads)
//		{
//			arcLis.constructDepTreeArcList(heads);
//			size = 0;
//            //dfscnt = 0;
//			dfs(0);
//		}
//		
//		
//		private void dfs(int i)
//		{
//            //DEBUG
//            //++dfscnt;
//            //if (dfscnt > 10000) {
//            //    System.out.println("DEAD LOOP in dfs!!!!");
//            //    System.exit(1);
//            //}
//
//			int l = arcLis.startIndex(i);
//			int r = arcLis.endIndex(i);
//			for (int p = l; p < r; ++p) {
//				int j = arcLis.get(p);
//				dfs(j);
//				dfslis[size++] = j;
//			}
//		}
//		
//	}

}
