package parser.decoding;

import java.io.IOException;
import java.util.ArrayList;

import parser.DependencyInstance;
import parser.DependencyParser;
import parser.DependencyPipe;
import parser.GlobalFeatureData;
import parser.LocalFeatureData;
import parser.Options;
import parser.Options.LearningMode;
import parser.io.DependencyReader;
import utils.Utils;

public class BruteForceDecoder extends DependencyDecoder {
	
	public DependencyParser dp;
	
	ArrayList<DependencyInstance> instList;
	double[] maxScore;
	ArrayList<Double> predScore;
	ArrayList<Double> avgScore;
	
	DecodingThread[] dt = null;
	double[] best = null;
	long[] cnt = null;
	
	Integer processID;
	Object output;

	// stats
	int nUCorrect = 0, nLCorrect = 0;
	int nDeps = 0, nWhole = 0, nSents = 0;
	
	public BruteForceDecoder(Options options, DependencyParser dp) {
		this.options = options;
		this.dp = dp;
		
		instList = new ArrayList<DependencyInstance>();
		predScore = new ArrayList<Double>();
		avgScore = new ArrayList<Double>();
		
		processID = 0;
		output = new Object();
	}
	
	public long estimateStat(DependencyInstance inst, LocalFeatureData lfd) {
		long est = 1;
		for (int m = 1; m < inst.length; ++m) {
			long cand = 0;
			for (int h = 0; h < inst.length; ++h) {
				if (h == m || (lfd.hasPrune() && lfd.isPruned(h, m)))
					continue;
				cand++;
			}
			est = est * cand;
			if (est < 0)
				return Long.MAX_VALUE;
		}
		return est;
	}

	@Override
	public DependencyInstance decode(DependencyInstance inst,
			LocalFeatureData lfd, GlobalFeatureData gfd, boolean addLoss) {
		Utils.Assert(false);
		return null;
	}

	private boolean isAncestorOf(int[] heads, int par, int ch) 
	{
		while (ch > 0) {
			if (ch == par) return true;
			ch = heads[ch];
		}
		return false;
	}
	
	private double calcScore(DependencyInstance now, DependencyInstance gold, LocalFeatureData lfd, GlobalFeatureData gfd, boolean addLoss) 
	{
		double score = 0;
		int[] heads = now.heads;
		int n = now.length;
		for (int m = 1; m < n; ++m)
			if (addLoss && heads[m] != gold.heads[m])
				score += 1.0;			 
		score += lfd.getScore(now);
		score += gfd.getScore(now);	
		return score;
	}
	
	public void evaluateSet() throws IOException {
		DependencyParser pruner = dp.pruner;
		DependencyPipe pipe = dp.getPipe();
		
    	if (pruner != null) pruner.resetPruningStats();
    	
    	DependencyReader reader = DependencyReader.createDependencyReader(options);
    	reader.startReading(options.testFile);
    	
    	instList.clear();
    	ArrayList<Long> estList = new ArrayList<Long>();
    	
    	//long maxSpace = 1000000000000l;
    	int lenLowerBound = 1;
    	int lenUpperBound = 40;
    	
    	// read instance
    	System.out.print("Reading sentences...");
    	DependencyInstance inst = pipe.createInstance(reader);    	
    	while (inst != null) {
    		if (inst.length - 1 >= lenLowerBound && inst.length - 1 <= lenUpperBound) {
        		LocalFeatureData lfd = new LocalFeatureData(inst, dp, true);
    			long est = estimateStat(inst, lfd);
    			//if (maxSpace < 0 || est < maxSpace) {
    			instList.add(inst);
    			//}
    			estList.add(est);
    		}
    		inst = pipe.createInstance(reader);
    	}
    	reader.close();
    	System.out.println("Done.");
    	System.out.println("Sent: " + instList.size());
    	
    	// sort
    	for (int i = 0; i < instList.size(); ++i)
    		for (int j = i + 1; j < instList.size(); ++j) {
    			if ((estList.get(i) < 0 && estList.get(j) > 0)
    					|| estList.get(i) > estList.get(j)) {
    				long est = estList.get(i);
    				estList.set(i, estList.get(j));
    				estList.set(j, est);
    				
    				DependencyInstance tmp = instList.get(i);
    				instList.set(i, instList.get(j));
    				instList.set(j, tmp);
    			}
    		}
    	
    	// run greedy method
    	HillClimbingDecoder hcDecoder = new HillClimbingDecoder(options);

    	System.out.print("Hill climbing decoding...");
    	{

        	nUCorrect = 0;
        	nLCorrect = 0;
        	nDeps = 0;
        	nWhole = 0;
        	nSents = 0;
        	
    		long start = System.currentTimeMillis();

    		for (int z = 0; z < instList.size(); ++z) {
    			if ((z + 1) % 1 == 0)
    				System.out.print(" " + (z + 1));
    			
        		inst = instList.get(z);

        		LocalFeatureData lfd = new LocalFeatureData(inst, dp, true);
        		GlobalFeatureData gfd = new GlobalFeatureData(lfd); 
        		
        		++nSents;
                
        		int nToks = 0;
                for (int i = 1; i < inst.length; ++i) {
                	if (inst.forms[i].matches("[-!\"#%&'()*,./:;?@\\[\\]_{}、]+")) continue;
                    ++nToks;
                }
                nDeps += nToks;

                DependencyInstance predInst = hcDecoder.decode(inst, lfd, gfd, false);
            	predScore.add(hcDecoder.bestScore);
            	avgScore.add(hcDecoder.avgScore);

        		int ua = DependencyParser.evaluateUnlabelCorrect(inst, predInst, false), la = 0;
        		if (options.learnLabel)
        			la = DependencyParser.evaluateLabelCorrect(inst, predInst, false);
        		nUCorrect += ua;
        		nLCorrect += la;
        		if ((options.learnLabel && la == nToks) ||
        				(!options.learnLabel && ua == nToks)) 
        			++nWhole;
    		}
        	System.out.println("Done.");

        	System.out.printf("  Tokens: %d%n", nDeps);
        	System.out.printf("  Sentences: %d%n", nSents);
        	System.out.printf("  UAS=%.6f\tLAS=%.6f\tCAS=%.6f\t[%ds]%n",
        			(nUCorrect+0.0)/nDeps,
        			(nLCorrect+0.0)/nDeps,
        			(nWhole + 0.0)/nSents,
        			(System.currentTimeMillis() - start)/1000);
        	if (options.pruning && options.learningMode != LearningMode.Basic && pruner != null)
        		pruner.printPruningStats();
    	}
    	
    	hcDecoder.shutdown();
    	maxScore = new double[predScore.size()];
    	
    	// brute force search
    	{
        	nUCorrect = 0;
        	nLCorrect = 0;
        	nDeps = 0;
        	nWhole = 0;
        	nSents = 0;
        	
    		long start = System.currentTimeMillis();

			dt = new DecodingThread[options.bruteForceThread];
			best = new double[options.bruteForceThread];
			cnt = new long[options.bruteForceThread];
			
			for (int i = 0; i < dt.length; i++) {
				dt[i] = new DecodingThread(i);
			}
			
			try {
				for (int i = 0; i < dt.length; i++) {
					dt[i].join();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

			System.out.printf("  Tokens: %d%n", nDeps);
        	System.out.printf("  Sentences: %d%n", nSents);
        	System.out.printf("  UAS=%.6f\tLAS=%.6f\tCAS=%.6f\t[%ds]%n",
        			(nUCorrect+0.0)/nDeps,
        			(nLCorrect+0.0)/nDeps,
        			(nWhole + 0.0)/nSents,
        			(System.currentTimeMillis() - start)/1000);
        	if (options.pruning && options.learningMode != LearningMode.Basic && pruner != null)
        		pruner.printPruningStats();
    	}

		// stats
		double sum = 0.0;
		for (int i = 0; i < avgScore.size(); ++i)
			if (avgScore.get(i) > 1e-6 && predScore.get(i) > 1e-6)
				sum += avgScore.get(i) / predScore.get(i);
		System.out.println("E[avg score / pred score]: " + (sum / avgScore.size()));
		
		sum = 0.0;
		for (int i = 0; i < avgScore.size(); ++i)
			if (avgScore.get(i) > 1e-6 && maxScore[i] > 1e-6)
				sum += avgScore.get(i) / maxScore[i];
		System.out.println("E[avg score / maximum score]: " + (sum / avgScore.size()));
		
		sum = 0.0;
		for (int i = 0; i < avgScore.size(); ++i) {
			if (Math.abs(predScore.get(i) - maxScore[i]) < 1e-6)
				sum += 1.0;
		}
		System.out.println("find global optimal: " + sum + "/" + avgScore.size() + "=" + (sum / avgScore.size()));
	}
	
	class DecodingThread implements Runnable {

		private Thread t = null;
		public int id = -1;
		
		public DecodingThread(int id) {
			t = new Thread(this, "decoding thread");
			this.id = id;
			t.start();
		}
		
		public void join() throws Exception {
			t.join();
		}

		public DependencyInstance decode(DependencyInstance inst,
				LocalFeatureData lfd, GlobalFeatureData gfd, boolean addLoss) {
			Utils.Assert(!options.learnLabel);
			
			int N = inst.length;

			DependencyInstance predInst = new DependencyInstance(inst);
			predInst.heads = new int[N];
			predInst.deplbids = new int[N];
			
            double bestScore = Double.NEGATIVE_INFINITY;
			DependencyInstance bestInst = new DependencyInstance(inst);
			bestInst.heads = new int[N];
			bestInst.deplbids = new int[N];

			for (int i = 0; i < N; ++i)
				predInst.heads[i] = -1;
			
			int p = 1;
			long count = 0;
			while (p > 0) {
				if (p == N) {
					// finish search
					double score = calcScore(predInst, inst, lfd, gfd, addLoss);
					count++;
					if (score > bestScore + 1e-6) {
						bestScore = score;
						for (int i = 0; i < N; ++i) {
							bestInst.heads[i] = predInst.heads[i];
						}
					}
					--p;
				}
				else {
					++predInst.heads[p];
					while (predInst.heads[p] < N && 
							(predInst.heads[p] == p 
								|| (lfd.hasPrune() && lfd.isPruned(predInst.heads[p], p))
								|| isAncestorOf(predInst.heads, p, predInst.heads[p])))
						++predInst.heads[p];
					if (predInst.heads[p] >= N) {
						// finish search
						predInst.heads[p] = -1;
						--p;
					}
					else {
						// go to next node
						++p;
					}
				}
			}
			
			best[id] = bestScore;
			cnt[id] = count;
			
			return bestInst;
		}

		@Override
		public void run() {
			while(true) {
				DependencyInstance inst = null;
				int currProcessID = 0;

				synchronized (processID) {
					currProcessID = processID.intValue();
					processID++;
					
					if (currProcessID < instList.size()) {
						inst = instList.get(currProcessID);
					}
				}
				
				if (inst == null) {
					// finish;
					break;
				}

				LocalFeatureData lfd = new LocalFeatureData(inst, dp, true);
        		GlobalFeatureData gfd = new GlobalFeatureData(lfd); 
        		
        		DependencyInstance predInst = decode(inst, lfd, gfd, false);

        		long est = estimateStat(inst, lfd);

    			synchronized (output) {
    	    		++nSents;
    	            
    	    		int nToks = 0;
    	            for (int i = 1; i < inst.length; ++i) {
    	            	if (inst.forms[i].matches("[-!\"#%&'()*,./:;?@\\[\\]_{}、]+")) continue;
    	                ++nToks;
    	            }
    	            nDeps += nToks;

            		int ua = DependencyParser.evaluateUnlabelCorrect(inst, predInst, false), la = 0;
            		if (options.learnLabel)
            			la = DependencyParser.evaluateLabelCorrect(inst, predInst, false);
            		nUCorrect += ua;
            		nLCorrect += la;
            		if ((options.learnLabel && la == nToks) ||
            				(!options.learnLabel && ua == nToks)) 
            			++nWhole;

            		System.out.print("len: " + (inst.length - 1));
    				System.out.print(" est: " + est);
    				System.out.print(" cnt: " + cnt[id]);
    				System.out.printf(" score: %.4f", best[id]);
    				System.out.printf(" pred: %.4f", predScore.get(currProcessID));
    				System.out.printf(" avg: %.4f", avgScore.get(currProcessID));
    				System.out.println( " same: " + (Math.abs(best[id] - predScore.get(currProcessID)) < 1e-6 ? "1" : "0"));
    				maxScore[currProcessID] = best[id];
    			}
    			
			}
		}
		
	}
}
