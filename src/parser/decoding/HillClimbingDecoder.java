package parser.decoding;

import java.util.ArrayList;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;


import parser.DependencyArcList;
import parser.DependencyInstance;
import parser.DependencyParser;
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
	final int labelLossType;
	
	int[][] staticTypes;
	
	double bestScore;	
	double bestAt300;
	int unchangedRuns, totRuns;
	volatile boolean stopped;
	
	public ArrayList<Double> scoreList;
	double avgScore;
	
	ChuLiuEdmondDecoder tmpDecoder;
    
    ExecutorService executorService;
	ExecutorCompletionService<Object> decodingService;
	HillClimbingTask[] tasks;
	
	public HillClimbingDecoder(Options options) {
		this.options = options;
		labelLossType = options.labelLossType;
        executorService = Executors.newFixedThreadPool(options.numHcThreads);
		decodingService = new ExecutorCompletionService<Object>(executorService);
		tasks = new HillClimbingTask[options.numHcThreads];
		for (int i = 0; i < tasks.length; ++i) {
			tasks[i] = new HillClimbingTask();
			tasks[i].id = i;
			tasks[i].sampler = new RandomWalkSampler(i, options);
		}
		
		tmpDecoder = new ChuLiuEdmondDecoder(options);
	}
	
	public void outputSampleUAS() {
		int totToken = 0;
		double totCorrect = 0.0;
		
		for (int i = 0; i < tasks.length; ++i) {
			totToken += tasks[i].totToken;
			totCorrect += tasks[i].totCorrect;
		}
		
		System.out.println("Sample UAS: " + (totCorrect / totToken));
	}
   
   @Override
    public void shutdown()
    {
        //System.out.println("shutdown");
        executorService.shutdownNow();
        tmpDecoder.shutdown();
    }
   
	public int depthFirstSearch(int[] heads, DependencyArcList arcLis, int[] dfslis)
	{
		arcLis.constructDepTreeArcList(heads);
		arcLis.constructSpan();
		arcLis.constructNonproj(heads);
		int size = dfs(0, arcLis, dfslis, 0);
		
		return size;
	}
	
	
	public int dfs(int i, DependencyArcList arcLis, int[] dfslis, int size)
	{
		int l = arcLis.startIndex(i);
		int r = arcLis.endIndex(i);
		for (int p = l; p < r; ++p) {
			int j = arcLis.get(p);
			size = dfs(j, arcLis, dfslis, size);
			dfslis[size++] = j;
		}
		return size;
	}
	
	public boolean isAncestorOf(int[] heads, int par, int ch) 
	{
		while (ch != 0) {
			if (ch == par) return true;
			ch = heads[ch];
		}
		return false;
	}
	
	
	public double calcScore(int[] heads, int m)
	{
		double score = lfd.getPartialScore(heads, m)
					 + gfd.getScore(heads);
		if (options.learnLabel) {
			int t = staticTypes[heads[m]][m];
			score += lfd.getLabeledArcScore(heads[m], m, t);
			if (addLoss) {
				if (labelLossType == 0) {
					if (heads[m] != inst.heads[m]) score += 0.5;
					if (t != inst.deplbids[m]) score += 0.5;
				} else if (heads[m] != inst.heads[m] || t != inst.deplbids[m])
					score += 1.0;
			}				
		} 
		else if (addLoss && heads[m] != inst.heads[m])
			score += 1.0;
		
		return score;
	}
	
	public double calcScore(DependencyInstance now) 
	{
		double score = 0;
		int n = now.length;
		int[] heads = now.heads;
		int[] deplbids = now.deplbids;
		for (int m = 1; m < n; ++m)
			if (options.learnLabel) {
				int t = deplbids[m];
				score += lfd.getLabeledArcScore(heads[m], m, t);
				if (addLoss) {
					if (labelLossType == 0) {
						if (heads[m] != inst.heads[m]) score += 0.5;
						if (t != inst.deplbids[m]) score += 0.5;
					} else if (heads[m] != inst.heads[m] || t != inst.deplbids[m])
						score += 1.0;
				}
			} else if (addLoss && heads[m] != inst.heads[m])
				score += 1.0;			 
		score += lfd.getScore(now);
		score += gfd.getScore(now);	
		return score;
	}
	
	public DependencyInstance decodeByMap(DependencyInstance inst,
			LocalFeatureData lfd, GlobalFeatureData gfd, boolean addLoss) {
		this.inst = inst;
		this.lfd = lfd;
		this.gfd = gfd;
		this.addLoss = addLoss;
		bestScore = Double.NEGATIVE_INFINITY;
		totRuns = 0;
		unchangedRuns = 0;
		stopped = false;
		
		scoreList = new ArrayList<Double>();
   
		pred = tmpDecoder.decode(inst, lfd, gfd, addLoss);
		
		// hill climb
		int[] heads = pred.heads;
		int[] deplbids = pred.deplbids;
	    
		int n = inst.length;
		int[] dfslis = new int[n];				
		DependencyArcList arcLis = new DependencyArcList(n);

		int cnt = 0;
		boolean more;
		for (;;) {
			more = false;
			int size = depthFirstSearch(heads, arcLis, dfslis);
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
		
		double score = calcScore(pred);
		scoreList.add(score);
		bestScore = score;
		
		double sum = 0.0;
		for (int i = 0; i < scoreList.size(); ++i)
			sum += scoreList.get(i);
		avgScore = sum / scoreList.size();
		
		return pred;		
   }

	@Override
	public DependencyInstance decode(DependencyInstance inst,
			LocalFeatureData lfd, GlobalFeatureData gfd, boolean addLoss) {
		
		this.inst = inst;
		this.lfd = lfd;
		this.gfd = gfd;
		this.addLoss = addLoss;
		bestScore = Double.NEGATIVE_INFINITY;
		bestAt300 = Double.NEGATIVE_INFINITY;
		pred = new DependencyInstance(inst);
		totRuns = 0;
		unchangedRuns = 0;
		stopped = false;
		
		scoreList = new ArrayList<Double>();
		
		if (options.learnLabel)
			staticTypes = lfd.getStaticTypes();
		
		for (int i = 0; i < tasks.length; ++i) {
			decodingService.submit(tasks[i], null);			
		}
		
		for (int i = 0; i < tasks.length; ++i) {
			try {
				decodingService.take();
			} catch (InterruptedException e) {
				System.out.println("Hill climbing thread interupted!!!!");
			}
		}

		//double sum = 0.0;
		//for (int i = 0; i < scoreList.size(); ++i)
		//	sum += scoreList.get(i);
		//avgScore = sum / scoreList.size();
		
		/*
		boolean good = true;
		if (bestScore < 0)
			good = false;
		for (int i = 0; i < scoreList.size(); ++i)
			if (scoreList.get(i) < 0)
				good = false;
		if (good) {
			for (int i = 0; i < scoreList.size(); ++i)
				scoreList.set(i, scoreList.get(i) / bestScore);
		}
		else {
			scoreList.clear();
		}
		*/
		
		//for (int i = 0; i < scoreList.size(); ++i)
		//	scoreList.set(i, scoreList.get(i) - bestScore);
		
		//if (bestScore > bestAt300) {
		//	System.out.println(inst.length + " " + bestScore + " " + bestAt300);
		//}
		
		return pred;	
		
		//return decodeByMap(inst, lfd, gfd, addLoss);
	}
	
	public class HillClimbingTask implements Runnable {
		
		public int id;
		
		public int totToken = 0;
		public double totCorrect = 0.0;
		
		RandomWalkSampler sampler;
		int converge;
	
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
			
			while (!stopped) {
				
				DependencyInstance now = sampler.randomWalkSampling(
						inst, lfd, staticTypes, addLoss);
				
	    		int ua = DependencyParser.evaluateUnlabelCorrect(inst, now, false);
	    		totCorrect += ua;
    			for (int i = 1; i < inst.length; ++i) {
    				if (inst.forms[i].matches("[-!\"#%&'()*,./:;?@\\[\\]_{}、]+")) continue;
    				++totToken;
    			}

	    		// hill climb
				int[] heads = now.heads;
				int[] deplbids = now.deplbids;
			    
                int cnt = 0;
				boolean more;
				for (;;) {
					more = false;
					size = depthFirstSearch(heads, arcLis, dfslis);
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
                    //if (cnt % 100 == 0) {
                        //System.out.println(cnt);
                    //}
				}
				
				if (options.learnLabel) {
					for (int m = 1; m < n; ++m)
						deplbids[m] = staticTypes[heads[m]][m];
				}
				
				double score = calcScore(now);
				synchronized (pred) {
					if (!stopped) {
						++totRuns;
						//scoreList.add(score);
						
						if (!stopped && score > bestScore) {
							bestScore = score;
							unchangedRuns = 0;
							pred.heads = heads;
							pred.deplbids = deplbids;
						} else {
							++unchangedRuns;
						}
						//++unchangedRuns;
						//scoreList.add(bestScore);
						
						//if (unchangedRuns == 299)
						//	bestAt300 = bestScore;
						
						if (unchangedRuns >= converge)
							stopped = true;
					}
					
				}
			}
		}
		
	}
	
}
