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
	
	public boolean optimalityCheck(DependencyInstance inst, DependencyInstance gold, LocalFeatureData lfd) {
		// check if the solution is optimum
		
		GpSibAutomaton gpSibAuto = mode.gpSibAutomaton ? new GpSibAutomaton(inst.length, lfd, gold, gold != null, options) : null;
		TreeAutomaton treeAuto = new TreeAutomaton(inst.length, lfd, gpSibAuto, gold, gold != null, options);
		
		boolean[] y = new boolean[treeAuto.y.length];
		for (int m = 1; m < inst.length; ++m)
			y[treeAuto.getIndex(inst.heads[m], m)] = true;

		int eta = 0;
		double delta = Double.NEGATIVE_INFINITY;
		double oldMaxDiff = Double.NEGATIVE_INFINITY;
		
		double treeInstScore = treeAuto.computeScore(inst);
		double gpSibInstScore = mode.gpSibAutomaton ? gpSibAuto.computeScore(inst) : 0.0;

		for (int iter = 0; iter < maxIter; ++iter) {
			double treeScore = treeAuto.maximize();
			
			double gpSibScore = mode.gpSibAutomaton ? gpSibAuto.maximize() : 0.0;
			
			double maxDiff = treeScore - treeInstScore;
			Utils.Assert(maxDiff > -1e-6);
			
			if (mode.gpSibAutomaton) {
				double diff = gpSibScore - gpSibInstScore;
				Utils.Assert(diff > -1e-6);
				maxDiff = Math.max(maxDiff, diff);
			}
			
			if (iter == 0) {
				delta = maxDiff;
				oldMaxDiff = maxDiff;
			}
			else if (maxDiff > oldMaxDiff) {
				eta++;
			}
			oldMaxDiff = maxDiff;
			
			if (Math.abs(maxDiff) < 1e-6) {
				return true;		// find certificate
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
		
		return false;
	}
	
	public int dualDecodingCheck(DependencyInstance inst, DependencyInstance gold, LocalFeatureData lfd) {
		// check if the solution is optimum
		
		GpSibAutomaton gpSibAuto = mode.gpSibAutomaton ? new GpSibAutomaton(inst.length, lfd, gold, gold != null, options) : null;
		TreeAutomaton treeAuto = new TreeAutomaton(inst.length, lfd, gpSibAuto, gold, gold != null, options);
		
		// compute the score of the solution
		double solScore = treeAuto.computeScore(inst);
		if (mode.gpSibAutomaton)
			solScore += gpSibAuto.computeScore(inst);
		
		// dual decomposition decoding
		DependencyInstance newInst = new DependencyInstance(inst);
		newInst.heads = new int[inst.length];
		
		int eta = 0;
		double delta = Double.NEGATIVE_INFINITY;
		
		double oldScore = Double.NEGATIVE_INFINITY;
		double maxScore = Double.NEGATIVE_INFINITY;
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
			
			double gpSibScore = mode.gpSibAutomaton ? gpSibAuto.maximize() : 0.0;
			double gpSibInstScore = mode.gpSibAutomaton ? gpSibAuto.computeScore(newInst) : 0.0;
			
			double currScore = gpSibScore;
			
			double diff = gpSibScore - gpSibInstScore;
			Utils.Assert(diff > -1e-6);
			
			if (iter == 0) {
				delta = diff;
				oldScore = currScore;
			}
			
			if (currScore > oldScore) {
				eta++;
			}
			oldScore = currScore;
			
			double decodeScore = treeScore + currScore;
			if (decodeScore > maxScore)
				maxScore = decodeScore;
			
			if (Math.abs(diff) < 1e-6) {
				cert = true;
				break;
			}
			else {
				// update lambda
				double rate = delta / (1 + eta);
				gpSibAuto.updateLambda(rate, treeAuto.y);
			}
		}
		
		if (cert) {
			if (maxScore < solScore - 1e-6) {
				System.out.println("dual decode bug!!!: " + maxScore + " " + solScore);
				System.exit(0);
			}
			
			if (maxScore > solScore + 1e-6)
				return 0;		// not optimal
			else
				return 1;		// optimal
		}
		else {
			if (maxScore > solScore + 1e-6)
				return 2;		// not optimal
			else
				return 3;		// optimal
		}
		
	}
}
