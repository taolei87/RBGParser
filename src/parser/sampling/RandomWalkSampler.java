package parser.sampling;

import java.util.*;
import gnu.trove.list.array.*;
import parser.*;
import utils.Utils;

public class RandomWalkSampler {
	
	Random r;
	Options options;
	final int labelLossType;
	
	public RandomWalkSampler(int seed, Options options) {
		r = new Random(seed);
		this.options = options;
		labelLossType = options.labelLossType;
	}
	
	public RandomWalkSampler(Options options) {
		r = new Random(1/*System.currentTimeMillis()*/);
		this.options = options;
		labelLossType = options.labelLossType;
	}
	
	public RandomWalkSampler(Random r, Options options) {
		this.r = r;
		this.options = options;
		labelLossType = options.labelLossType;
	}
	
    public DependencyInstance uniformRandomWalkSampling(DependencyInstance inst,
    		LocalFeatureData lfd, boolean addLoss)
    {
        //int cnt = 0;
    	int len = inst.length;
    	
		DependencyInstance predInst = new DependencyInstance(inst);
		predInst.heads = new int[len];
		predInst.deplbids = new int[len];
        
        //double[] score = new double[len];
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
    				if (candH == curr || lfd.isPruned(candH, curr))
    					continue;
    				
    				//double s = lfd.getArcScore(candH, curr);
    				
    				//if (addLoss) {
    				//	  if (candH != inst.heads[curr])
    				//		s += 1.0;
    				//}
                    //score[size] = s;
                    depList[size] = candH;
                    ++size;
    			}

    			//int sample = samplePoint(score, size, r);
    			int sample = r.nextInt(size);
    			predInst.heads[curr] = depList[sample];
    			curr = predInst.heads[curr];
    		}
    		curr = i;
    		while (!inTree[curr]) {
    			inTree[curr] = true;
    			curr = predInst.heads[curr]; 
    		}
    	}
    	
    	return predInst;
    }
    
    public DependencyInstance randomWalkSampling(DependencyInstance inst,
    		LocalFeatureData lfd, boolean addLoss)
    {
        //int cnt = 0;
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
    				if (candH == curr || lfd.isPruned(candH, curr))
    					continue;
    				
    				double s = lfd.getArcScore(candH, curr);
    				
    				if (addLoss) {
    					  if (candH != inst.heads[curr])
    						s += 1.0;
    				}
                    score[size] = s;
                    depList[size] = candH;
                    ++size;
    			}

    			int sample = samplePoint(score, size, r);
    			predInst.heads[curr] = depList[sample];
    			curr = predInst.heads[curr];
    		}
    		curr = i;
    		while (!inTree[curr]) {
    			inTree[curr] = true;
    			curr = predInst.heads[curr]; 
    		}
    	}
    	
    	return predInst;
    }
    
    private int samplePoint(double[] score, int N, Random r) {
    	double sumScore = Double.NEGATIVE_INFINITY;
    	for (int i = 0; i < N; i++) {
    		sumScore = Utils.logSumExp(sumScore, score[i]);
    	}
    	double logp = Math.log(r.nextDouble() + 1e-60);
    	double cur = Double.NEGATIVE_INFINITY;
    	int ret = 0;
    	for (; ret < N; ret++) {
    		cur = Utils.logSumExp(cur, score[ret]);
    		if (logp + sumScore < cur)
    			break;
    	}
    	return ret;
    }
    
//    private void dumpScoreTable(int len, DependencyInstance inst,
//			LocalFeatureData lfd, int[][] staticTypes, boolean addLoss) {
//    	System.out.println(len);
//        for (int u = 0; u < len; ++u) {
//            for (int v = 0; v < len; ++v) 
//                if (v == 0 || u == v || lfd.isPruned(u, v))
//                    System.out.print("0.00\t");
//                else {
//                    double s = lfd.getArcScore(u, v);
//                    //int l = options.learnLabel ? staticTypes[u][v] : 0;
//                    //s += options.learnLabel ? lfd.getLabeledArcScore(u, v, l) : 0.0;
//                    if (addLoss) {
//                        s += (inst.heads[v] != u ? 1.0 : 0.0);
//                        //   + (options.learnLabel && inst.deplbids[v] != l ? 1.0 : 0.0);
//                    }
//                    System.out.printf("%.2f\t", s);
//                }
//            System.out.println();
//        }
//        System.exit(1);		
//	}

}
