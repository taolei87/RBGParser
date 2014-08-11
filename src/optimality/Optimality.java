package optimality;


import parser.DependencyInstance;
import parser.LocalFeatureData;
import parser.Options;
import utils.Utils;

public class Optimality {
	
	public Options options;
	
	public int maxIter;
	
	class FeatureMode {
		boolean sibAutomaton = false;
		boolean gpSibAutomaton = false;
		boolean triSibAutomaton = false;
	}
	
	FeatureMode mode;

	public Optimality(Options options) {
		this.options = options;
		maxIter = options.optMaxIter;
		
		if (options.learnLabel || options.useGGP || options.useHB || options.useHO || options.usePSC) {
			System.out.println("optimality check not support!");
			System.exit(0);
		}
		
		mode = new FeatureMode();
		if (options.useGP || options.useGS) {
			mode.gpSibAutomaton = true;
		}
		
		if (options.useTS) {
			mode.triSibAutomaton = true;
		}
		
		if (!options.useGP && !options.useGS && !options.useTS && options.useCS) {
			mode.sibAutomaton = true;
		}
	}
	
	public int optimalityCheck(DependencyInstance inst, DependencyInstance gold, LocalFeatureData lfd) {
		// check if the solution is optimum
		
		GpSibAutomaton gpSibAuto = mode.gpSibAutomaton ? new GpSibAutomaton(inst.length, lfd, gold, gold != null, options) : null;
		TreeAutomaton treeAuto = new TreeAutomaton(inst.length, lfd, gpSibAuto, gold, gold != null, options);
		
		// dual decomposition decoding
		DependencyInstance newInst = new DependencyInstance(inst);
		newInst.heads = new int[inst.length];

		boolean[] y = new boolean[treeAuto.y.length];
		for (int m = 1; m < inst.length; ++m)
			y[treeAuto.getIndex(inst.heads[m], m)] = true;

		int eta = 0;
		double delta = Double.NEGATIVE_INFINITY;
		double oldScore = Double.NEGATIVE_INFINITY;
		
		double maxScore = 0.0;
		double decodeScore = 0.0;
		double solScore = 0.0;
		int ret = 0;
		boolean cert = false;
		
		for (int iter = 0; iter < maxIter; ++iter) {
			double treeScore = treeAuto.maximize();
			double gpSibScore = mode.gpSibAutomaton ? gpSibAuto.maximize() : 0.0;
			maxScore = treeScore + gpSibScore;
			
			for (int m = 1; m < newInst.length; ++m) {
				for (int h = 0; h < newInst.length; ++h) {
					if (treeAuto.y[treeAuto.getIndex(h, m)]) {
						Utils.Assert(!lfd.isPruned(h, m));
						newInst.heads[m] = h;
					}
				}
			}
			newInst.heads[0] = -1;
			double gpSibDecodeScore = gpSibAuto.computeScore(newInst);
			decodeScore = treeScore + gpSibDecodeScore;

			if (Math.abs(gpSibScore - gpSibDecodeScore) < 1e-6) {
				cert = true;
			}
			else {
				cert = false;
			}
			
			double treeInstScore = treeAuto.computeScore(inst);
			double gpSibInstScore = mode.gpSibAutomaton ? gpSibAuto.computeScore(inst) : 0.0;
			solScore = treeInstScore + gpSibInstScore;

			double maxDiff = treeScore - treeInstScore;
			Utils.Assert(maxDiff > -1e-6);
			
			if (mode.gpSibAutomaton) {
				double diff = gpSibScore - gpSibInstScore;
				Utils.Assert(diff > -1e-6);
				maxDiff = Math.max(maxDiff, diff);
			}
			
			if (iter == 0) {
				delta = Math.min(0.5, maxDiff);
				oldScore = maxScore;
			}
			else if (maxScore > oldScore) {
				eta++;
			}
			oldScore = maxScore;
			
			if (Math.abs(maxDiff) < 1e-6) {
				//ret = true;		// find certificate
				System.out.println("iter: " + iter);
				break;
			}
			else {
				// update lambda
				double rate = delta / (1 + eta);
				if (Math.abs((treeScore - treeInstScore) - maxDiff) < 1e-6) {
					treeAuto.updateLambda(rate, gpSibAuto, y);
				}
				if (mode.gpSibAutomaton && Math.abs((gpSibScore - gpSibInstScore) - maxDiff) < 1e-6) {
					gpSibAuto.updateLambda(rate, y);
				}
			}
		}
		
		//System.out.println(decodeScore + " " + solScore);
		//System.out.println("cert: " + cert + " ret: " + ret);
		if (cert) {
			if (decodeScore > solScore + 1e-6)
				ret = 0;		// not optimal
			else
				ret = 1;		// optimal
		}
		else {
			System.out.println("iter: " + maxIter);
			if (decodeScore > solScore + 1e-6)
				ret = 2;		// dd is optimal
			else if (decodeScore < solScore - 1e-6)
				ret = 3;		// we are better
			else
				ret = 4;		// same
		}
		
		return ret;
	}
	
	public int dualDecodingCheck(DependencyInstance inst, DependencyInstance gold, LocalFeatureData lfd) {
		// check if the solution is optimum
		
		GpSibAutomaton gpSibAuto = mode.gpSibAutomaton ? new GpSibAutomaton(inst.length, lfd, gold, gold != null, options) : null;
		TreeAutomaton treeAuto = new TreeAutomaton(inst.length, lfd, gpSibAuto, gold, gold != null, options);
		
		// dual decomposition decoding
		DependencyInstance newInst = new DependencyInstance(inst);
		newInst.heads = new int[inst.length];
		
		int eta = 0;
		double delta = Double.NEGATIVE_INFINITY;
		
		double oldScore = Double.NEGATIVE_INFINITY;
		double decodeScore = Double.NEGATIVE_INFINITY;
		boolean cert = false;
		
		for (int iter = 0; iter < maxIter; ++iter) {
			double treeScore = treeAuto.maximize();
			for (int m = 1; m < newInst.length; ++m) {
				for (int h = 0; h < newInst.length; ++h) {
					if (treeAuto.y[treeAuto.getIndex(h, m)]) {
						Utils.Assert(!lfd.isPruned(h, m));
						newInst.heads[m] = h;
					}
				}
			}
			newInst.heads[0] = -1;
			
			double gpSibScore = mode.gpSibAutomaton ? gpSibAuto.maximize() : 0.0;
			double gpSibInstScore = mode.gpSibAutomaton ? gpSibAuto.computeScore(newInst) : 0.0;
			
			double diff = gpSibScore - gpSibInstScore;
			Utils.Assert(diff > -1e-6);
			
			decodeScore = treeScore + gpSibScore;
			
			if (iter == 0) {
				delta = Math.min(0.5, diff);
				oldScore = decodeScore;
			}
			
			if (decodeScore > oldScore) {
				eta++;
			}
			oldScore = decodeScore;
			
			if (Math.abs(diff) < 1e-6) {
				cert = true;
				System.out.println("iter: " + iter);
				break;
			}
			else {
				// update lambda
				double rate = delta / (1 + eta);
				gpSibAuto.updateLambda(rate, treeAuto.y);
			}
		}
		
		// compute the score of the solution
		double solScore = treeAuto.computeScore(inst);
		if (mode.gpSibAutomaton)
			solScore += gpSibAuto.computeScore(inst);
		
		decodeScore = treeAuto.computeScore(newInst);
		if (mode.gpSibAutomaton)
			decodeScore += gpSibAuto.computeScore(newInst);
		
		/*
		double decodeScore2 = lfd.getScore(newInst);
		double solScore2 = lfd.getScore(inst);		
		
		System.out.println(decodeScore + " " + decodeScore2 + " " + solScore + " " + solScore2);
		for (int m = 1; m < inst.heads.length; ++m)
			System.out.print(inst.heads[m] + "/" + m + " ");
		System.out.println();
		for (int m = 1; m < newInst.heads.length; ++m)
			System.out.print(newInst.heads[m] + "/" + m + " ");
		System.out.println();
		*/
		
		int ret = 0;

		if (cert) {
			if (decodeScore < solScore - 1e-6) {
				System.out.println("dual decode bug!!!: " + decodeScore + " " + solScore);
				System.exit(0);
			}
			
			if (decodeScore > solScore + 1e-6)
				ret = 0;		// not optimal
			else
				ret = 1;		// optimal
		}
		else {
			System.out.println("iter: " + maxIter);
			if (decodeScore > solScore + 1e-6)
				ret = 2;		// dd is optimal
			else if (decodeScore < solScore - 1e-6)
				ret = 3;		// we are better
			else
				ret = 4;		// same
		}
		
		//System.out.println("ret : " + ret); 
		return ret;
	}
}
