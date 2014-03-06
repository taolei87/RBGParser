package parser.sampling;

import java.util.*;

import parser.*;
import utils.Utils;

public class RandomWalkSampler {
	
	Random r;
	
	public RandomWalkSampler(int seed) {
		r = new Random(seed);
	}
	
	public RandomWalkSampler() {
		r = new Random();
	}
	
	public RandomWalkSampler(Random r) {
		this.r = r;
	}
	
	
    public DependencyInstance randomWalkSampling(DependencyInstance inst,
    		LocalFeatureData lfd, boolean addLoss) {
    	int len = inst.length;
    	
		DependencyInstance predInst = new DependencyInstance(inst);
		predInst.heads = new int[len];
		predInst.depids = new int[len];
    	
    	boolean[] inTree = new boolean[len];
    	inTree[0] = true;
    	for (int i = 0; i < len; i++) {
    		predInst.heads[i] = -1;
    	}
    	
    	for (int i = 1; i < len; i++) {
    		int curr = i;
    		while (!inTree[curr]) {
    			// sample new head
    			ArrayList<Double> score = new ArrayList<Double>();
    			ArrayList<Integer> depList = new ArrayList<Integer>();

    			for (int candH = 0; candH < len; candH++) {
    				if (lfd.isPruned(candH, curr))
    					continue;
    				depList.add(candH);
    				double s = lfd.getArcScore(candH, curr);
    				if (addLoss && inst.depids[i] != candH) {
    					// cost augmented
    					s += 1.0;
    				}
    				score.add(s);
    			}

    			int sample = samplePoint(score, r);
    			predInst.depids[curr] = depList.get(sample);
    			curr = predInst.depids[curr];
    			
    			if (predInst.depids[curr] != -1 && !inTree[curr]) {
    				cycleErase(predInst.depids, curr);
    			}
    		}
    		curr = i;
    		while (!inTree[curr]) {
    			inTree[curr] = true;
    			curr = predInst.depids[curr]; 
    		}
    	}
    	
    	return predInst;
    }
    
    private void cycleErase(int[] dep, int i) {
    	while (dep[i] != -1) {
    		int next = dep[i];
    		dep[i] = -1;
    		i = next;
    	}
    }
    
    private int samplePoint(ArrayList<Double> score, Random r) {
    	double sumScore = -1e30;
    	for (int i = 0; i < score.size(); i++) {
    		sumScore = Utils.logSumExp(sumScore, score.get(i));
    	}
    	double[] prob = new double[score.size()];
    	prob[0] = Math.exp(score.get(0) - sumScore);
    	for (int i = 1; i < score.size(); i++)
    		prob[i] = prob[i - 1] + Math.exp(score.get(i) - sumScore);
    	if (Math.abs(prob[prob.length - 1] - 1.0) > 1e-4) {
    		System.out.println("sample point bug");
    		System.exit(0);
    	}
    	double p = r.nextDouble();
    	int ret = 0;
    	for (; ret < prob.length; ret++) {
    		if (p < prob[ret])
    			break;
    	}
    	return ret;
    }
}
