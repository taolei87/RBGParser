package parser.decoding;

import gnu.trove.list.array.TIntArrayList;

import java.io.BufferedWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;


import parser.DependencyArcList;
import parser.DependencyInstance;
import parser.DependencyPipe;
import parser.GlobalFeatureData;
import parser.LocalFeatureData;
import parser.Options;
import parser.sampling.RandomWalkSampler;
import utils.Utils;

public class HillClimbingDecoder extends DependencyDecoder {

	DependencyInstance pred, inst;
	LocalFeatureData lfd;
	GlobalFeatureData gfd;
	DependencyPipe pipe;
	boolean addLoss;
	final int labelLossType;

	int[][] staticTypes;

	double bestScore;	
	int unchangedRuns, totRuns;
	volatile boolean stopped;

	ExecutorService executorService;
	ExecutorCompletionService<Object> decodingService;
	HillClimbingTask[] tasks;

	//HashMap<String, Integer[]> totalArcCount;
	HashMap<Integer, Integer> sentArcCount;
	ArrayList<Integer[]> sampleDep;
	//int[] totalScoreDist;
	ArrayList<Double> sentScore;
	//ArrayList<Double> goldScore;
	ArrayList<Double> avgScoreOverBest;
	ArrayList<Double> avgScoreOverGold;
	ArrayList<Double> avgScoreOverMap;

	int totalSample;
	int goldTotalDist;
	double goldScore;

	int totalSent;
	int bestTotalDist;
	
	int[] compare;

	// temporary use for first order MAP
	ChuLiuEdmondDecoder tmpDecoder;
	
	public HillClimbingDecoder(Options options) {
		this.options = options;
		labelLossType = options.labelLossType;
		executorService = Executors.newFixedThreadPool(options.numHcThreads);
		decodingService = new ExecutorCompletionService<Object>(executorService);
		tasks = new HillClimbingTask[options.numHcThreads];

		//totalArcCount =  new HashMap<String, Integer[]>();
		tmpDecoder = new ChuLiuEdmondDecoder(options);
		//totalScoreDist = new int[100];
		//goldScore = new ArrayList<Double>();
		avgScoreOverBest = new ArrayList<Double>();
		avgScoreOverGold = new ArrayList<Double>();
		avgScoreOverMap = new ArrayList<Double>();

		totalSample = 0;
		totalSent = 0;
		goldTotalDist = 0;
		bestTotalDist = 0;
		
		compare = new int[3];

		for (int i = 0; i < tasks.length; ++i) {
			tasks[i] = new HillClimbingTask();
			tasks[i].id = i;
			tasks[i].sampler = new RandomWalkSampler(i, options);
		}
	}

	@Override
	public void resetCount() {
		//totalArcCount = new HashMap<String, Integer[]>();
		//totalScoreDist = new int[100];
		//goldScore = new ArrayList<Double>();
		avgScoreOverBest = new ArrayList<Double>();
		avgScoreOverGold = new ArrayList<Double>();
		avgScoreOverMap = new ArrayList<Double>();

		totalSample = 0;
		totalSent = 0;
		goldTotalDist = 0;
		bestTotalDist = 0;
		
		compare = new int[3];
		
		//tmpDecoder.isOptimal.clear();
		//tmpDecoder.lstNumOpt.clear();
		//tmpDecoder.sentLength.clear();
	}

	@Override
	public void outputArcCount(BufferedWriter bw) {
		try {
			/*
			for (String s : totalArcCount.keySet()) {
				bw.write(s);
				Integer[] count = totalArcCount.get(s);  
				for (int i = 0; i < count.length; ++i) {
					bw.write(" " + count[i]);
				}
				bw.newLine();
			}
			bw.newLine();
			 */
			/*
			bw.write("" + totalScoreDist[0]);
			for (int i = 0; i < totalScoreDist.length; ++i) {
				bw.write(" " + totalScoreDist[i]);
			}
			bw.newLine();
			double sumGold = 0;
			for (int i = 0; i < goldScore.size(); ++i)
				sumGold += goldScore.get(i);
			bw.write("" + (sumGold / goldScore.size()));
			bw.newLine();
			bw.newLine();
			 */
			//bw.flush();

			//System.out.println("Find global optimal ratio: " + (double)optimalNum / totalSent);
			tmpDecoder.printLocalOptStats2();
			
			/*
			System.out.println("Avg dist to gold: " + (goldTotalDist + 0.0) / totalSample);
			System.out.println("Avg dist to best: " + (bestTotalDist + 0.0) / totalSample);
		
			System.out.println("vote worse: " + compare[0] + " equal: " + compare[1] + " better: " + compare[2]);
			
			double sum = 0.0;
			for (int i = 0; i < avgScoreOverBest.size(); ++i) {
				sum += avgScoreOverBest.get(i);
			}
			double avg = sum / avgScoreOverBest.size();
			sum = 0.0;
			for (int i = 0; i < avgScoreOverBest.size(); ++i) {
				sum += (avgScoreOverBest.get(i) - avg) * (avgScoreOverBest.get(i) - avg);
			}
			double stdDev = Math.sqrt(sum / (avgScoreOverBest.size() - 1));
			System.out.println("avg score/best: " + avg + " " + stdDev);
			
			sum = 0.0;
			for (int i = 0; i < avgScoreOverGold.size(); ++i) {
				sum += avgScoreOverGold.get(i);
			}
			avg = sum / avgScoreOverGold.size();
			sum = 0.0;
			for (int i = 0; i < avgScoreOverGold.size(); ++i) {
				sum += (avgScoreOverGold.get(i) - avg) * (avgScoreOverGold.get(i) - avg);
			}
			stdDev = Math.sqrt(sum / (avgScoreOverGold.size() - 1));
			System.out.println("avg score/gold: " + avg + " " + stdDev);
			
			sum = 0.0;
			for (int i = 0; i < avgScoreOverMap.size(); ++i) {
				sum += avgScoreOverMap.get(i);
			}
			avg = sum / avgScoreOverMap.size();
			sum = 0.0;
			for (int i = 0; i < avgScoreOverMap.size(); ++i) {
				sum += (avgScoreOverMap.get(i) - avg) * (avgScoreOverMap.get(i) - avg);
			}
			stdDev = Math.sqrt(sum / (avgScoreOverMap.size() - 1));
			System.out.println("avg score/map: " + avg + " " + stdDev);
			*/
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		//System.out.println("finish output arc count");
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
		this.pipe = lfd.getPipe();

		bestScore = Double.NEGATIVE_INFINITY;
		pred = new DependencyInstance(inst);
		totRuns = 0;
		unchangedRuns = 0;
		stopped = false;

		sentArcCount = new HashMap<Integer, Integer>();
		sampleDep = new ArrayList<Integer[]>();
		sentScore = new ArrayList<Double>();
		totalSent++;

		goldScore = calcScore(inst);

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

		/*
		for (Integer i : sentArcCount.keySet()) {
			int m = i % pred.length;
			int h = i / pred.length;

			String mPos = pipe.coarseMap.get(pred.postags[m]);
			String hPos = pipe.coarseMap.get(pred.postags[h]);
			if (mPos == null || hPos == null) {
				System.out.println("can't find cpos: " + mPos + " " + hPos);

				if (mPos == null)
					mPos = "X";
				if (hPos == null)
					hPos = "X";
				//System.exit(0);
			}

			String posStr = hPos + "," + mPos;
			//String posStr = "ALL";
			int count = sentArcCount.get(i) - 1;

			if (!totalArcCount.containsKey(posStr)) {
				totalArcCount.put(posStr, new Integer[options.numHcConverge]);
				Integer[] totalArc = totalArcCount.get(posStr);
				for (int z = 0; z < totalArc.length; ++z)
					totalArc[z] = 0;
			}
			Integer[] totalArc = totalArcCount.get(posStr);
			if (!(count >= 0 && count < totalArc.length)) {
				System.out.println(count);
				System.exit(0);
			}
			totalArc[count]++;
		}

		if (bestScore > 1e-6) {
			for (int i = 0; i < sentScoreDist.size(); ++i) {
				double score = Math.max(0, sentScoreDist.get(i));
				int pos = Math.min(totalScoreDist.length - 1, (int)(score / bestScore * 100));
				totalScoreDist[pos]++;
			}

			double score = lfd.getScore(inst) + gfd.getScore(inst);
			score = Math.max(0, score / bestScore);
			goldScore.add(score);
		}*/

		
		DependencyInstance map = tmpDecoder.decode(inst, lfd, gfd, addLoss);
		double mapScore = calcScore(map);
		
		if (Math.abs(mapScore - bestScore) < 1e-8) {
			//optimalNum++;
			tmpDecoder.isOptimal.add(1);
		}
		else {
			tmpDecoder.isOptimal.add(0);
		}
		
		for (int m = 1; m < pred.length; ++m) {
			for (int h = 0; h < pred.length; ++h) {
				if (h == m || h == pred.heads[m])
					continue;
				
				int code = h * pred.length + m;
				if (sentArcCount.containsKey(code))
					bestTotalDist += sentArcCount.get(code);
			}
		}
		
		/*
		if (!addLoss) {
			for (int m = 1; m < inst.length; ++m) {
				for (int h = 0; h < inst.length; ++h) {
					int code = h * inst.length + m;
					if (sentArcCount.containsKey(code)) {
						System.out.print(h + "/" + m + "/" + sentArcCount.get(code) + "\t");
					}
					else {
						System.out.print(h + "/" + m + "/0\t");
					}
				}
				System.out.println();
			}
			for (int m = 1; m < inst.length; ++m) {
				System.out.print(votePred.heads[m] + "/" + pred.heads[m] + "/" + m + "\t");
			}
			System.out.println();
			System.out.println("" + bestScore + " " + voteScore);
			
			try {
				System.in.read();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}*/
		
		double sum = 0.0;
		for (int i = 0; i < sentScore.size(); ++i)
			sum += sentScore.get(i);
		double avg = sum / sentScore.size();
		
		if (avg > 1e-6 && bestScore > 1e-6 && goldScore > 1e-6) {
			avgScoreOverBest.add(avg / bestScore);
			avgScoreOverGold.add(avg / goldScore);
			avgScoreOverMap.add(avg / mapScore);
			
			{
				
				sentArcCount.clear();
				Utils.Assert(sampleDep.size() == sentScore.size());
				Utils.Assert(sampleDep.size() > 0);
				for (int i = 0; i < sampleDep.size(); ++i) {
					Integer[] dep = sampleDep.get(i);
					Utils.Assert(inst.length == dep.length);
					double score = sentScore.get(i);
					if (score < avg - 1e-6)
						continue;
					for (int m = 1; m < dep.length; ++m) {
						int h = dep[m];
						Utils.Assert(h >= 0);
						int code = h * dep.length + m;
						if (!sentArcCount.containsKey(code)) {
							sentArcCount.put(code, 1);
						}
						else {
							sentArcCount.put(code, sentArcCount.get(code) + 1);
						}
					}
				}
				
			}
			
		}
		
		// get majority vote
		/*
		DependencyInstance votePred = tmpDecoder.majorityVote(inst, sentArcCount);
		double voteScore = calcScore(votePred);
		
		if (voteScore < bestScore - 1e-6) {
			compare[0]++;
		}
		else if (voteScore > bestScore + 1e-6) {
			compare[2]++;
		}
		else {
			compare[1]++;
		}
		
		if (!this.addLoss && options.useMajVote)
			return votePred;
		else
		*/
		return pred;		
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
		int[] heads = now.heads;
		int[] deplbids = now.deplbids;
		for (int m = 1; m < now.length; ++m)
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
			size = dfs(j, arcLis, dfslis, size);
			dfslis[size++] = j;
		}
		return size;
	}

	public class HillClimbingTask implements Runnable {

		public int id;

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

			// get map
			// DependencyInstance map = tmpDecoder.decode(inst, lfd, gfd, addLoss);

			while (!stopped) {

				DependencyInstance now = sampler.randomWalkSampling(
						inst, lfd, staticTypes, addLoss);

				int[] sampleHead = new int[now.length];
				for (int i = 0; i < now.length; ++i)
					sampleHead[i] = now.heads[i];

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
					
					//if (addLoss && options.firstViolation && bestScore > goldScore + 1e-6) {
					//	stopped = true;
					//}
					//++unchangedRuns;
					//if (unchangedRuns >= converge)
					//	stopped = true;

					// add edge to sent count
					if (unchangedRuns <= converge) {
					//if (!stopped) {

						totalSample++;
						sentScore.add(score);
						Integer[] dep = new Integer[now.length];
						for (int m = 0; m < now.length; ++m)
							dep[m] = now.heads[m];
						sampleDep.add(dep);
						//Utils.Assert(now.length == pred.length);
						
						for (int m = 1; m < now.length; ++m) {
							int h = now.heads[m];
							int code = h * now.length + m;

							if (!sentArcCount.containsKey(code)) {
								sentArcCount.put(code, 1);
							}
							else {
								sentArcCount.put(code, sentArcCount.get(code) + 1);
							}
						}
						
						// compare to gold
						for (int m = 1; m < inst.length; ++m) {
							if (now.heads[m] != inst.heads[m]) {
								goldTotalDist++;
							}
						}
						
					}
				}
			}
		}

	}

}
