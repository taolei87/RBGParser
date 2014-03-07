package parser;

import parser.Options.LearningMode;
import utils.FeatureVector;
import utils.Utils;

public class LocalFeatureData {
	
	static double pruningGoldHits = 0;
	static double pruningTotGold = 0;
	static double pruningTotUparcs = 0;
	static double pruningTotArcs = 0;
	
	DependencyInstance inst;
	DependencyPipe pipe;
	Options options;
	Parameters parameters;
	
	DependencyParser pruner;
	
	int len;						// sentence length
	int ntypes;						// number of label types
	int size;						
	int rank;								
	double gamma;
	
	int nuparcs;					// number of un-pruned arcs
	int[] arc2id;					// map (h->m) arc to an id in [0, nuparcs-1]
	boolean[] isPruned;				// whether a (h->m) arc is pruned								
	
	FeatureVector[] wordFvs;		// word feature vectors
	double[][] wpU, wpV;			// word projections U\phi and V\phi
	
	FeatureVector[][] arcFvs;		// 1st order arc feature vectors
	double[][] arcScores;			// 1st order arc scores (including tensor)
	
	FeatureVector[][][][] lbFvs;	// labeled-arc feature vectors
	double[][][][] lbScores;		// labeled-arc scores
	
	//TODO: add high order feature vectors and score tables
	
	FeatureVector[][] tripsFvs;		// [dep id][sib]
	double[][] tripsScores;

	FeatureVector[][] sibFvs;		// [mod][sib]
	double[][] sibScores;
	
	
	
	public LocalFeatureData(DependencyInstance inst,
			DependencyParser parser, boolean indexGoldArcs) 
	{
		this.inst = inst;
		this.pipe = parser.pipe;
		this.options = parser.options;
		this.parameters = parser.parameters;
		this.pruner = parser.pruner;
		
		Utils.Assert(pruner == null || pruner.options.learningMode == LearningMode.Basic);
		
		// allocate memory for arrays here
		len = inst.length;
		ntypes = pipe.types.length;
		rank = options.R;
		size = pipe.numArcFeats;
		gamma = options.gamma;
		
		wordFvs = new FeatureVector[len];
		wpU = new double[len][rank];
		wpV = new double[len][rank];
		
		arcFvs = new FeatureVector[len][len];
		arcScores = new double[len][len];
		
		lbFvs = new FeatureVector[len][ntypes][2][2];
		lbScores = new double[len][ntypes][2][2];

		// calculate 1st order feature vectors and scores
		initFirstOrderTables();
		
		if (options.learningMode != LearningMode.Basic) {
			// construct unpruned arc list. All arcs are kept if there is no pruner.
			initArcPruningMap(indexGoldArcs);
			
			// allocate memory for tables of high order features 
			initHighOrderFeatureTables();
		}
				
	}
	
	private void initFirstOrderTables() 
	{
		for (int i = 0; i < len; ++i) {
			wordFvs[i] = pipe.createWordFeatures(inst, i);
			//wpU[i] = parameters.projectU(wordFvs[i]);
			//wpV[i] = parameters.projectV(wordFvs[i]);
			parameters.projectU(wordFvs[i], wpU[i]);
			parameters.projectV(wordFvs[i], wpV[i]);
		}
				
		for (int i = 0; i < len; ++i)
			for (int j = 0; j < len; ++j) 
				if (i != j) {
					arcFvs[i][j] = pipe.createArcFeatures(inst, i, j);
					arcScores[i][j] = parameters.dotProduct(arcFvs[i][j]) * gamma
									+ parameters.dotProduct(wpU[i], wpV[j], i-j) * (1-gamma);
				}
		
		if (options.learnLabel) {
			for (int i = 0; i < len; ++i)
				for (int t = 0; t < ntypes; ++t)
					for (int j = 0; j < 2; ++j)
						for (int k = 0; k < 2; ++k) {
							lbFvs[i][t][j][k] = pipe.createLabelFeatures(
									inst, i, t, j == 1, k == 1);
							lbScores[i][t][j][k] = parameters.dotProduct(
									lbFvs[i][t][j][k]);
						}
		}
	}
	
	public void initHighOrderFeatureTables() {
		// init non-first-order feature tables
		
		// 2nd order (head, mod, mod_sib) features
		tripsFvs = new FeatureVector[nuparcs][len];
		tripsScores = new double[nuparcs][len];		
		
		// 2nd order (mod, mod_sib) features
		sibFvs = new FeatureVector[len][len];
		sibScores = new double[len][len];
	}
	
	public void initArcPruningMap(boolean includeGoldArcs) {
		
		arc2id = new int[len*len];
		isPruned = new boolean[len*len];
		
		if (pruner == null || !options.pruning) {
			for (int i = 0, L = arc2id.length; i < L; ++i) {
				arc2id[i] = i;
				isPruned[i] = false;
			}
			nuparcs = len*len;
		} else {	
			pruningTotGold += len-1;
			pruningTotArcs += (len-1)*(len-1);
			
			for (int i = 0, L = arc2id.length; i < L; ++i) {
				arc2id[i] = -1;
				isPruned[i] = true;
			}
			
			double threshold = Math.log(options.pruningCoeff);
			//System.out.println(threshold);
			nuparcs = 0;
			LocalFeatureData lfd2 = new LocalFeatureData(inst, pruner, false);
			
			for (int m = 1; m < len; ++m) {								
				double maxv = Double.NEGATIVE_INFINITY;
				for (int h = 0; h < len; ++h)
					if (h != m) {
						double v = lfd2.getArcScore(h, m);
						maxv = Math.max(maxv, v);;
					}

				for (int h = 0; h < len; ++h)
					if (h != m) {
						double v = lfd2.getArcScore(h, m);
						
						if ((includeGoldArcs && h == inst.heads[m]) ||
						 (v >= maxv + threshold)) {
							//isPruned[m*len+h] = false;
							isPruned[m*len+h] = !(v >= maxv + threshold);
							arc2id[m*len+h] = nuparcs;
							nuparcs++;							
						}
					}
			}
			
			if (includeGoldArcs)
				for (int m = 1; m < len; ++m)
					if (!isPruned[m*len+inst.heads[m]])
						pruningGoldHits++;
			pruningTotUparcs += nuparcs;
		}
	}
	
	public static void printPruningStats()
	{
		System.out.printf("Pruning Recall: %.4f\tEffcy: %.4f%n",
				pruningGoldHits / pruningTotGold,
				pruningTotUparcs / pruningTotArcs);
	}
	
	public int[][] getStaticTypes() {
		
		int N = len, T = ntypes;
		int[][] staticTypes = new int[N][N];
		for (int i = 0; i < N; ++i)
			for (int j = 0; j < N; ++j) if (i != j) {
				
				int k = -1;
				double maxv = Double.NEGATIVE_INFINITY;				
				int toRight = i < j ? 1 : 0;	
				
				for (int t = 0; t < T; ++t) {
					double va = lbScores[i][t][toRight][0] + lbScores[j][t][toRight][1];
					if (va > maxv) {
						k = t;
						maxv = va;
					}
				}				
				staticTypes[i][j] = k;
			}
		return staticTypes;
	}
	
	public boolean isPruned(int h, int m) 
	{
		return isPruned[m*len+h];
	}
	
	public double getArcScore(int h, int m)
	{
		return arcScores[h][m];
	}
	
	public double getLabeledArcScore(int h, int m, int t)
	{
		int toR = h < m ? 1 : 0;
		return lbScores[h][t][toR][0] + lbScores[m][t][toR][1];
	}
	
	public double getTripsScore(int h, int m, int s) 
	{
		int id = arc2id[m*len+h];
		
		Utils.Assert(id >= 0 && arc2id[s*len+h] >= 0);
		
		if (tripsFvs[id][s] == null)
			getTripsFeatureVector(h, m, s);
		
		return tripsScores[id][s];
	}
	
	public double getSibScore(int m, int s)
	{
		if (sibFvs[m][s] == null)
			getSibFeatureVector(m, s);
		
		return sibScores[m][s];
	}
	
	public double getPartialScore(int[] heads, int x)
	{
		// 1st order arc
		double score = arcScores[heads[x]][x];
		
		if (options.learningMode != LearningMode.Basic) {
			
			DependencyArcList arcLis = new DependencyArcList(heads);
			
			// 2nd order (h,m,s) & (m,s)
			for (int h = 0; h < len; ++h) if (h != x) {
				
				int st = arcLis.startIndex(h);
				int ed = arcLis.endIndex(h);
				
				for (int p = st; p+1 < ed; ++p) {
					// mod and sib
					int m = arcLis.get(p);
					int s = arcLis.get(p+1);
					if (m <= x && x <= s)
						score += getTripsScore(h, m, s) + getSibScore(m, s);
				}
			}
		}
		
		return score;
	}
	
	public double getScore(DependencyInstance now)
	{
		double score = 0;		
		int[] heads = now.heads;
		
		// 1st order arc
		for (int m = 1; m < len; ++m)
			score += arcScores[heads[m]][m];
		
		if (options.learningMode != LearningMode.Basic) {
			
			DependencyArcList arcLis = new DependencyArcList(heads);
			
			// 2nd order (h,m,s) & (m,s)
			for (int h = 0; h < len; ++h) {
				
				int st = arcLis.startIndex(h);
				int ed = arcLis.endIndex(h);
				
				for (int p = st; p+1 < ed; ++p) {
					// mod and sib
					int m = arcLis.get(p);
					int s = arcLis.get(p+1);
					score += getTripsScore(h, m, s);
					score += getSibScore(m, s);
				}
			}
			
		}
		
		return score;
	}
	
	public FeatureVector getFeatureVector(DependencyInstance now)
	{
		FeatureVector fv = new FeatureVector(size);
		
		int[] heads = now.heads;
		
		// 1st order arc
		for (int m = 1; m < len; ++m)
			fv.addEntries(arcFvs[heads[m]][m]);
		
		if (options.learningMode != LearningMode.Basic) {
			
			DependencyArcList arcLis = new DependencyArcList(heads);
			
			// 2nd order (h,m,s) & (m,s)
			for (int h = 0; h < len; ++h) {
				
				int st = arcLis.startIndex(h);
				int ed = arcLis.endIndex(h);
				
				for (int p = st; p+1 < ed; ++p) {
					// mod and sib
					int m = arcLis.get(p);
					int s = arcLis.get(p+1);
					fv.addEntries(getTripsFeatureVector(h,m,s));
					fv.addEntries(getSibFeatureVector(m,s));
				}
			}
			
		}
		
		return fv;
	}
	
	public FeatureVector getTripsFeatureVector(int h, int m, int s)
	{
		int id = arc2id[m*len+h];
		
		Utils.Assert(id >= 0 && arc2id[s*len+h] >= 0);
		
		FeatureVector fv = tripsFvs[id][s];		
		if (fv == null) {
			fv = pipe.createTripsFeatureVector(inst, h, m, s);
			tripsFvs[id][s] = fv;
			tripsScores[id][s] = parameters.dotProduct(fv) * gamma;
		}
		return fv;
	}
	
	public FeatureVector getSibFeatureVector(int m, int s)
	{
		FeatureVector fv = sibFvs[m][s];		
		if (fv == null) {			
			fv = pipe.createSibFeatureVector(inst, m, s, false);
			sibFvs[m][s] = fv;
			sibScores[m][s] = parameters.dotProduct(fv) * gamma;
		}
		return fv;
	}
	
	public FeatureVector getFeatureDifference(DependencyInstance gold, 
						DependencyInstance pred)
	{
//		FeatureVector dfv = new FeatureVector(size);
//		
//		int N = gold.length;
//    	int[] actDeps = gold.heads;
//    	int[] predDeps = pred.heads;
//    	
//    	// 1st order arc
//    	for (int mod = 1; mod < N; ++mod) {
//    		int head  = actDeps[mod];
//    		int head2 = predDeps[mod];
//    		if (head != head2) {
//    			dfv.addEntries(arcFvs[head][mod]);
//    			dfv.addEntries(arcFvs[head2][mod], -1.0);
//    		}
//    	}
//    	
//    	//TODO: handle high order features
		
		FeatureVector dfv = getFeatureVector(gold);
		dfv.addEntries(getFeatureVector(pred), -1.0);
    	
    	return dfv;
	}
	
	
	public FeatureVector getLabeledFeatureDifference(DependencyInstance gold, 
			DependencyInstance pred)
	{
		if (!options.learnLabel) return null;
		
		FeatureVector dlfv = new FeatureVector(size);
		
    	int N = inst.length;
    	int[] actDeps = gold.heads;
    	int[] actLabs = gold.deplbids;
    	int[] predDeps = pred.heads;
    	int[] predLabs = pred.deplbids;
		
    	for (int mod = 1; mod < N; ++mod) {
    		int type = actLabs[mod];
    		int type2 = predLabs[mod];
    		int head  = actDeps[mod];
    		int head2 = predDeps[mod];
    		if (head != head2 || type != type2) {
    			int toR = head < mod ? 1 : 0;        		
    			int toR2 = head2 < mod ? 1 : 0;        		
    			dlfv.addEntries(lbFvs[head][type][toR][0]);
    			dlfv.addEntries(lbFvs[mod][type][toR][1]);
    			dlfv.addEntries(lbFvs[head2][type2][toR2][0], -1.0);
    			dlfv.addEntries(lbFvs[mod][type2][toR2][1], -1.0);
    		}
    	}
		
		return dlfv;
	}
	
}
