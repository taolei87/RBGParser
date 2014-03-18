package parser.sampling;

import java.util.*;
import gnu.trove.list.array.*;
import parser.*;
import utils.Utils;

public class RandomWalkSampler {
	
	Random r;
	Options options;
	
	public int loopCount;
	
	public RandomWalkSampler(int seed, Options options) {
		r = new Random(seed);
		this.options = options;
    	loopCount = 0;
	}
	
	public RandomWalkSampler(Options options) {
		r = new Random(1/*System.currentTimeMillis()*/);
		this.options = options;
    	loopCount = 0;
	}
	
	public RandomWalkSampler(Random r, Options options) {
		this.r = r;
		this.options = options;
    	loopCount = 0;
	}
	
	
    public DependencyInstance randomWalkSampling(DependencyInstance inst,
    		LocalFeatureData lfd, int[][] staticTypes, boolean addLoss)
    {
        int cnt = 0;
    	int len = inst.length;
    	
		DependencyInstance predInst = new DependencyInstance(inst);
		predInst.heads = new int[len];
		predInst.deplbids = new int[len];
        
        double[] score = new double[len];
        int[] depList = new int[len];
        int size = 0;

    	boolean[] inTree = new boolean[len];
    	inTree[0] = true;
    	for (int i = 0; i < len; i++) {
    		predInst.heads[i] = -1;
    	}
    	
    	for (int i = 1; i < len; i++) {
    		int curr = i;
    		while (!inTree[curr]) {
    			// sample new head 
                size = 0;

    			for (int candH = 0; candH < len; candH++) {
    				if (lfd.isPruned(candH, curr))
    					continue;
                    
    				int candLab = options.learnLabel ? staticTypes[candH][curr] : 0;
    				
    				double s = lfd.getArcScore(candH, curr);
                    //double s = lfd.getArcNoTensorScore(candH, curr);
    				s += options.learnLabel ? lfd.getLabeledArcScore(candH, curr, candLab) : 0.0;
    				
    				if (addLoss) {
    					// cost augmented
    					s += (inst.heads[curr] != candH ? 1.0 : 0.0)
    							+ (options.learnLabel && inst.deplbids[curr] != candLab ? 1.0 : 0.0);
    				}
                    score[size] = s;
                    depList[size] = candH;
                    ++size;
    			}

    			int sample = samplePoint(score, size, r);
    			predInst.heads[curr] = depList[sample];
    			predInst.deplbids[curr] = options.learnLabel ? staticTypes[predInst.heads[curr]][curr] : 0;
    			curr = predInst.heads[curr];
    			
    			if (predInst.heads[curr] != -1 && !inTree[curr]) {
    				cycleErase(predInst.heads, predInst.deplbids, curr);
    				++loopCount;
                    ++cnt;
    				if (cnt % 1000000 == 0) {
    					System.out.println("\tRndWalk Loop " + cnt);
                        System.out.println(len);
                        for (int u = 0; u < len; ++u) {
                            for (int v = 0; v < len; ++v) 
                                if (v == 0 || u == v || lfd.isPruned(u, v))
                                    System.out.print("0.00\t");
                                else {
                                    double s = lfd.getArcScore(u, v);
                                    int l = options.learnLabel ? staticTypes[u][v] : 0;
                                    s += options.learnLabel ? lfd.getLabeledArcScore(u, v, l) : 0.0;
                                    if (addLoss) {
                                        s += (inst.heads[v] != u ? 1.0 : 0.0)
                                           + (options.learnLabel && inst.deplbids[v] != l ? 1.0 : 0.0);
                                    }
                                    System.out.printf("%.2f\t", s);
                                }
                            System.out.println();
                        }
                        System.exit(1);
                    }
    			}
    		}
    		curr = i;
    		while (!inTree[curr]) {
    			inTree[curr] = true;
    			curr = predInst.heads[curr]; 
    		}
    	}
    	
    	return predInst;
    }
    
    private void cycleErase(int[] dep, int[] lab, int i) {
    	while (dep[i] != -1) {
    		int next = dep[i];
    		dep[i] = -1;
    		lab[i] = 0;
    		i = next;
    	}
    }
    
    private int samplePoint(double[] score, int N, Random r) {
    	double sumScore = Double.NEGATIVE_INFINITY;
    	for (int i = 0; i < N; i++) {
    		sumScore = Utils.logSumExp(sumScore, score[i]);
    	}
    	double logp = Math.log(r.nextDouble() + 1e-30);
    	double cur = Double.NEGATIVE_INFINITY;
    	int ret = 0;
    	for (; ret < N; ret++) {
    		cur = Utils.logSumExp(cur, score[ret]);
    		if (logp + sumScore < cur)
    			break;
    	}
    	return ret;
    }
}
