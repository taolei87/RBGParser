package optimality;

import parser.DependencyArcList;
import parser.DependencyInstance;
import parser.Options;
import parser.LocalFeatureData;
import utils.Utils;

public class GpSibAutomaton {
	// grandparent-sibling automaton, include sib, gpc, gpsib features
	
	public int length;
	
	public LocalFeatureData lfd;
	
	public Options options;
	
	public boolean[] zSib;		// optimal solution for sibling
	
	public boolean[] zHead;	// optimal solution for head (grandparent)
	
	public double[] lSib;	// lambda for sibling automaton
	
	public double[] lHead;	// lambda for head (grandparent)
	
	public boolean[] updated;	// if lambda of z_i is updated
	
	public boolean[][] oldZSib;
	public int[] oldZHead;
	public double[] oldScore;
	
	public double beta;			// weight of first-order score
	
	public DependencyInstance gold;
	
	boolean addLoss;
	
	public GpSibAutomaton(int length, LocalFeatureData lfd, DependencyInstance gold, boolean addLoss, Options options) {
		this.length = length;
		this.lfd = lfd;
		this.options = options;
		this.gold = gold;
		this.addLoss = addLoss;
		
		zSib = new boolean[length * length];
		zHead = new boolean[length * length];
		lSib = new double[length * length];
		lHead = new double[length * length];
		beta = options.optBeta;
	
		updated = new boolean[length];
		for (int i = 0; i < updated.length; ++i)
			updated[i] = true;
		oldZSib = new boolean[length][length];
		oldZHead = new int[length];
		oldScore = new double[length];
	}
	
	private int getIndex(int h, int m) {
		return h * length + m;
	}
	
	public int getHeadIndex(int id) {
		return id / length;
	}
	
	public int getModIndex(int id) {
		return id % length;
	}
	
	private void setSolution(int gp, int h, boolean[] opt) {
		if (gp >= 0)
			zHead[getIndex(gp, h)] = true;
		for (int m = 1; m < length; ++m)
			if (opt[m])
				zSib[getIndex(h, m)] = true;
	}
	
	public double findOpt(int gp, int h, boolean[] opt) {
		double[] optScore = new double[length];		// [last child]
		int[] lastSib = new int[length];
		
		for (int i = 0; i < opt.length; ++i)
			opt[i] = false;
		
		// dp equation depends on the sibling feature definition
		
		for (int s = 1; s < length; ++s) {
			if (s == h || s == gp || lfd.isPruned(h, s))
				continue;
			
			// init, s is the only child
			//double loss = (addLoss && gold.heads[s] != h) ? 1.0 : 0.0;
			optScore[s] = /*beta * (lfd.getArcScore(h, s) + loss)*/ - lSib[getIndex(h, s)]
					+ ((options.useGP && gp >= 0) ? lfd.getGPCScore(gp, h, s) : 0.0);
			lastSib[s] = 0;
			double baseScore = optScore[s];

			// dp
			for (int m = 1; m < s; ++m) {
				if (m == h || m == gp || m == s || lfd.isPruned(h, m))
					continue;
				
				double score = optScore[m] + baseScore
						+ (options.useCS ? (lfd.getSibScore(m, s) + lfd.getTripsScore(h, m, s)) : 0.0)
						+ ((options.useGS && gp >= 0) ? lfd.getGPSibScore(gp, h, m, s) : 0.0);
				
				if (score > optScore[s]) {
					optScore[s] = score;
					lastSib[s] = m;
				}
			}
		}
		
		// compute solution
		double maxScore = 0.0;
		int maxSib = 0;
		
		for (int s = 1; s < length; ++s) {
			if (optScore[s] > maxScore) {
				maxScore = optScore[s];
				maxSib = s;
			}
		}
		
		while (maxSib != 0) {
			opt[maxSib] = true;
			maxSib = lastSib[maxSib];
		}
		
		return maxScore;
	}
	
	public double maximize() {
		// init
		for (int i = 0; i < zSib.length; ++i) {
			zSib[i] = false;
			zHead[i] = false;
		}
		double score = 0.0;
		
		// solve root
		if (updated[0]) {
			boolean[] opt = new boolean[length];
			oldScore[0] = findOpt(-1, 0, opt);
			for (int i = 0; i < opt.length; ++i)
				oldZSib[0][i] = opt[i];
			
			updated[0] = false;
		}
		score += oldScore[0];
		setSolution(-1, 0, oldZSib[0]);
		
		// solve others
		for (int h = 1; h < length; ++h) {
			if (updated[h]) {
				double currMaxScore = Double.NEGATIVE_INFINITY;
				int maxGP = -1;
				boolean[] opt = null;
				
				// enumerate grandparent
				for (int gp = 0; gp < length; ++gp) {
					if (gp == h || lfd.isPruned(gp, h))
						continue;
					
					boolean[] tmpOpt = new boolean[length];
					double currScore = findOpt(gp, h, tmpOpt);
					double loss = (addLoss && gold.heads[h] != gp) ? 1.0 : 0.0;
					currScore += beta * (lfd.getArcScore(gp, h) + loss) - lHead[getIndex(gp, h)];
					
					if (currScore > currMaxScore) {
						currMaxScore = currScore;
						maxGP = gp;
						opt = tmpOpt;
					}
				}
				
				Utils.Assert(opt != null);
				oldScore[h] = currMaxScore;
				oldZHead[h] = maxGP;
				for (int i = 0; i < oldZSib[h].length; ++i)
					oldZSib[h][i] = opt[i];
				
				updated[h] = false;
			}
			
			// store solution
			setSolution(oldZHead[h], h, oldZSib[h]);
			score += oldScore[h];
		}
		
		return score;
	}
	
	public double computeScore(DependencyInstance inst) {
		double score = 0.0;
		
		DependencyArcList arcLis = new DependencyArcList(inst.heads);
		
		for (int h = 0; h < length; ++h) {
			int gp = inst.heads[h];
			
			if (gp >= 0) {
				double loss = (addLoss && gold.heads[h] != gp) ? 1.0 : 0.0;
				score += beta * (lfd.getArcScore(gp, h) + loss) - lHead[getIndex(gp, h)];
			}
			
			int st = arcLis.startIndex(h);
			int ed = arcLis.endIndex(h);
			
			for (int p = st; p < ed; ++p) {
				int m = arcLis.get(p);
				score += - lSib[getIndex(h, m)] + ((options.useGP && gp >= 0) ? lfd.getGPCScore(gp, h, m) : 0.0);
			}
			
			for (int p = st; p+1 < ed; ++p) {
				// mod and sib
				int m = arcLis.get(p);
				int s = arcLis.get(p+1);
				
				score += (options.useCS ? (lfd.getSibScore(m, s) + lfd.getTripsScore(h, m, s)) : 0.0)
						+ ((options.useGS && gp >= 0) ? lfd.getGPSibScore(gp, h, m, s) : 0.0);
			}
		}
		return score;
	}
	
	public void updateLambda(double rate, boolean[] y) {
		for (int i = 0; i < lSib.length; ++i) {
			if (zSib[i] && !y[i]) {
				lSib[i] += rate;
				updated[getHeadIndex(i)] = true;
			}
			else if (!zSib[i] && y[i]) {
				lSib[i] -= rate;
				updated[getHeadIndex(i)] = true;
			}
		}

		for (int i = 0; i < lHead.length; ++i) {
			if (zHead[i] && !y[i]) {
				lHead[i] += rate;
				updated[getModIndex(i)] = true;
			}
			else if (!zHead[i] && y[i]) {
				lHead[i] -= rate;
				updated[getModIndex(i)] = true;
			}
		}
	}
}
