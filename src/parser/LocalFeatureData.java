package parser;

import java.util.Arrays;

import parser.Options.LearningMode;
import parser.decoding.DependencyDecoder;
import parser.feature.SyntacticFeatureFactory;
import utils.FeatureVector;
import utils.ScoreCollector;
import utils.Utils;

public class LocalFeatureData {
	
	static double NULL = Double.NEGATIVE_INFINITY;
	
	DependencyInstance inst;
	DependencyPipe pipe;
	SyntacticFeatureFactory synFactory;
	Options options;
	Parameters parameters;
	
	DependencyParser pruner;
	DependencyDecoder prunerDecoder;
	
	final boolean isTrain;
	final int len;					// sentence length
	final int ntypes;				// number of label types
	final int size, sizeL;						
	final int rank;								
	final double gamma;//, gammaLabel;
	
	int numarcs;					// number of un-pruned arcs and gold arcs (if indexGoldArcs == true)
	int[] arc2id;					// map (h->m) arc to an id in [0, numarcs-1]
	boolean[] isPruned;				// whether a (h->m) arc is pruned
	int[] edges, st;
	int numedges;						// number of un-pruned arcs
	
	FeatureVector[] wordFvs;		// word feature vectors
	double[][] wpU, wpV;			// word projections U\phi and V\phi
	
	FeatureVector[] arcFvs;			// 1st order arc feature vectors
	double[] arcScores;				// 1st order arc scores (including tensor)
	
	double[] trips;			// [dep id][sib]
	double[] sib;			// [mod][sib]
	double[] gpc;			// [dep id][child]
	double[] headbi;		// [dep id][head2]
	double[] gpsib;			// grandparent-parent-child-sibling, gp-p is mapped to id [dep id][sib][mod]
	double[] trisib;		// parent-sibling-child-sibling [dep id][in sib][out sib]
	double[] ggpc;			// [dep id (ggp, gp)][dep id (p, mod)]
	double[] psc;			// parent-sib-mod-child, [dep id (p, sib)][dep id (mod, child)]
	
	public LocalFeatureData(DependencyInstance inst,
			DependencyParser parser, boolean indexGoldArcs, boolean isTrain) 
	{
		this.isTrain = isTrain;
		this.inst = inst;
		pipe = parser.pipe;
		synFactory = pipe.synFactory;
		options = parser.options;
		parameters = parser.parameters;
		pruner = parser.pruner;
		prunerDecoder = pruner == null ? null : 
			DependencyDecoder.createDependencyDecoder(pruner.options);
			
		Utils.Assert(pruner == null || pruner.options.learningMode == LearningMode.Basic);
		
		len = inst.length;
		ntypes = pipe.types.length;
		rank = options.R;
		size = synFactory.numArcFeats+1;
		sizeL = synFactory.numLabeledArcFeats+1;
		gamma = options.gamma;
		//gammaLabel = options.gammaLabel;
		
		wordFvs = new FeatureVector[len];
		wpU = new double[len][rank];
		wpV = new double[len][rank];
		
		if (isTrain) arcFvs = new FeatureVector[len*len];
		arcScores = new double[len*len];
	    //arcNtScores = new double[len*len];

		//lbFvs = new FeatureVector[len][ntypes][2][2];
		//lbScores = new double[len][ntypes][2][2];

		if (options.learningMode != LearningMode.Basic) {
			// construct unpruned arc list. All arcs are kept if there is no pruner.
			initArcPruningMap(indexGoldArcs);
			
			// allocate memory for tables of high order features 
			initHighOrderFeatureTables();
		}
		
		// calculate 1st order feature vectors and scores
		initFirstOrderTables();
	}
	
	private void initFirstOrderTables() 
	{
		for (int i = 0; i < len; ++i) {
			wordFvs[i] = synFactory.createWordFeatures(inst, i);
			//wpU[i] = parameters.projectU(wordFvs[i]);
			//wpV[i] = parameters.projectV(wordFvs[i]);
			parameters.projectU(wordFvs[i], wpU[i]);
			parameters.projectV(wordFvs[i], wpV[i]);
		}
		
		boolean nopruning = !options.pruning || pruner == null || options.learningMode == LearningMode.Basic;
		
		if (isTrain) {
			for (int i = 0; i < len; ++i)
				for (int j = 0; j < len; ++j) 
					if (i != j && (nopruning || arc2id[j*len+i] != -1)) {
							arcFvs[i*len+j] = new FeatureVector(size);
							synFactory.createArcFeatures(arcFvs[i*len+j], inst, i, j);	
							arcScores[i*len+j] = parameters.dotProduct(arcFvs[i*len+j]) * gamma
											+ parameters.dotProduct(wpU[i], wpV[j], i-j) * (1-gamma);
					}
		} else {
			for (int i = 0; i < len; ++i)
				for (int j = 0; j < len; ++j) 
					if (i != j && (nopruning || arc2id[j*len+i] != -1)) {
							ScoreCollector col = new ScoreCollector(parameters);
							synFactory.createArcFeatures(col, inst, i, j);	
							arcScores[i*len+j] = col.score * gamma
											+ parameters.dotProduct(wpU[i], wpV[j], i-j) * (1-gamma);
					}
		}
		
//		if (options.learnLabel) {
//			for (int i = 0; i < len; ++i)
//				for (int t = 0; t < ntypes; ++t)
//					for (int j = 0; j < 2; ++j)
//						for (int k = 0; k < 2; ++k) {
//							lbFvs[i][t][j][k] = pipe.createLabelFeatures(
//									inst, i, t, j == 1, k == 1);
//							lbScores[i][t][j][k] = parameters.dotProduct(
//									lbFvs[i][t][j][k]) * gammaLabel;
//						}
//		}
	}
	
	public void initHighOrderFeatureTables() {
		// init non-first-order feature tables
		
		if (options.useCS) {
			// 2nd order (head, mod, mod_sib) features
			//trips = new FeatureDataItem[nuparcs*len];
			trips = new double[numarcs*len];
			Arrays.fill(trips, NULL);
			
			// 2nd order (mod, mod_sib) features
			//sib = new FeatureDataItem[len*len];
			sib = new double[len*len];
			Arrays.fill(sib, NULL);
		}
		
		if (options.useGP) {
			// 2nd order (head, mod, child) features
			//gpc = new FeatureDataItem[nuparcs*len];
			gpc = new double[numarcs*len];
			Arrays.fill(gpc, NULL);
		}
		
		if (options.useHB) {
			// 2nd order (head, mod, head2) features
			//headbi = new FeatureDataItem[nuparcs*len];
			headbi = new double[numarcs*len];
			Arrays.fill(headbi, NULL);
		}
		
		if (options.useGS) {
			// 3rd order (grand, head, sib, mod) features
			//gpsib = new FeatureDataItem[nuparcs*len*len];
			gpsib = new double[numarcs*len*len];
			Arrays.fill(gpsib, NULL);
		}
		
		if (options.useTS) {
			// 3rd order (head, sib1, mod, sib2) features
			//trisib = new FeatureDataItem[nuparcs*len*len];
			trisib = new double[numarcs*len*len];
			Arrays.fill(trisib, NULL);
		}
		
		if (options.useGGP) {
			// 3rd order (great-grand, grand, head, mod) features
			//ggpc = new FeatureDataItem[nuparcs*nuparcs];
			ggpc = new double[numarcs*numarcs];
			Arrays.fill(ggpc, NULL);
		}

		if (options.usePSC) {
			// 3rd order (head, mod, sib, child) features
			//psc = new FeatureDataItem[nuparcs*nuparcs];
			psc = new double[numarcs*numarcs];
			Arrays.fill(psc, NULL);
		}
	}
	
	public void initArcPruningMap(boolean includeGoldArcs) {
		
		arc2id = new int[len*len];
		isPruned = new boolean[len*len];
		edges = new int[len*len];
		st = new int[len];
				
		for (int i = 0, L = arc2id.length; i < L; ++i) {
			arc2id[i] = -1;
			isPruned[i] = true;
		}
		
		if (pruner == null || !options.pruning) {

			//nuparcs = len*len; -> (len-1)*(len-1) actually
			numarcs = 0;
			st[0] = 0;
			for (int m = 1; m < len; ++m) {
				st[m] = numarcs;
				for (int h = 0; h < len; ++h) if (h!=m) {
					isPruned[m*len+h] = false;
					arc2id[m*len+h] = numarcs;
					edges[numarcs] = h;
					++numarcs;
				}
			}
			numedges = numarcs;
		} else {	
			if (includeGoldArcs) pruner.pruningTotGold += len-1;
			pruner.pruningTotArcs += (len-1)*(len-1);
			
			// Use the threshold to prune arcs. 
			double threshold = Math.log(options.pruningCoeff);
			LocalFeatureData lfd2 = new LocalFeatureData(inst, pruner, false, false);
			GlobalFeatureData gfd2 = null;
			DependencyInstance pred = prunerDecoder.decode(inst, lfd2, gfd2, false);
							
			numarcs = 0;
			numedges = 0;
			st[0] = 0;
			for (int m = 1; m < len; ++m) {
				st[m] = numedges;
				double maxv = Double.NEGATIVE_INFINITY;
				for (int h = 0; h < len; ++h)
					if (h != m) {
						double v = lfd2.getArcScore(h, m);
						maxv = Math.max(maxv, v);;
					}

				for (int h = 0; h < len; ++h)
					if (h != m) {
						double v = lfd2.getArcScore(h, m);
						boolean keep = (v >= maxv + threshold || h == pred.heads[m]);
						if ((includeGoldArcs && h == inst.heads[m]) || keep) {
							isPruned[m*len+h] = !keep;
							if (keep) edges[numedges++] = h;
							arc2id[m*len+h] = numarcs;
							numarcs++;							
						}
					}
			}
			
			if (includeGoldArcs)
				for (int m = 1; m < len; ++m)
					if (!isPruned[m*len+inst.heads[m]])
						pruner.pruningGoldHits++;
			pruner.pruningTotUparcs += numarcs;
		}
	}
	
//	private void traverse(int x, boolean[] vis)
//	{
//		vis[x] = false;
//		for (int y = 1; y < len; ++y)
//			if (vis[y] && !isPruned(x, y)) traverse(y, vis);
//	}
	
	public int startIndex(int i)
	{
		return st[i];
	}
	
	public int endIndex(int i) 
	{
		return (i >= len-1) ? numedges : st[i+1];
	}
	
	public int getHead(int id)
	{
		return edges[id];
	}
	
	public boolean isPruned(int h, int m) 
	{
		return isPruned[m*len+h];
	}
	
	public double getArcScore(int h, int m)
	{
		return arcScores[h*len+m];
	}

    //public double getArcNoTensorScore(int h, int m)
    //{
    //    return arcNtScores[h*len+m];
    //}
	
	private final double getTripsScore(int h, int m, int s) 
	{
		int id = arc2id[m*len+h];
		
		Utils.Assert(id >= 0 && arc2id[s*len+h] >= 0);
		
		int pos = id*len+s;
		if (trips[pos] == NULL) {
			ScoreCollector col = new ScoreCollector(parameters);
			synFactory.createTripsFeatureVector(col, inst, h, m, s);
			trips[pos] = col.score * gamma;
			//getTripsFeatureVector(h, m, s);
		}
		
		return trips[pos];
	}
	
	private final double getSibScore(int m, int s)
	{
		int pos = m*len+s;
		if (sib[pos] == NULL) {
			ScoreCollector col = new ScoreCollector(parameters);
			synFactory.createSibFeatureVector(col, inst, m, s);
			sib[pos] = col.score * gamma;
			//getSibFeatureVector(m, s);
		}
		
		return sib[pos];
	}
	
	
	private final double getGPCScore(int gp, int h, int m) {
		int id = arc2id[h*len+gp];
		
		Utils.Assert(id >= 0 && arc2id[m*len+h] >= 0);
		
		int pos = id*len+m;
		if (gpc[pos] == NULL) {
			ScoreCollector col = new ScoreCollector(parameters);
			synFactory.createGPCFeatureVector(col, inst, gp, h, m);
			gpc[pos] = col.score * gamma;
			//getGPCFeatureVector(gp, h, m);
		}
		
		return gpc[pos];
	}
	
	private final double getHeadBiScore(int h, int m, int h2) {
		int id = arc2id[m*len+h];
		
		Utils.Assert(id >= 0 && m + 1 < len 
				&& arc2id[(m + 1)*len+h2] >= 0);
		
		int pos = id*len+h2;
		if (headbi[pos] == NULL) {
			ScoreCollector col = new ScoreCollector(parameters);
			synFactory.createHeadBiFeatureVector(col, inst, m, h, h2);
			headbi[pos] = col.score * gamma;
			//getHeadBiFeatureVector(h, m, h2);
		}

		return headbi[pos];
	}
	
	private final double getGPSibScore(int gp, int h, int m, int s) {
		// m < s
		int id = arc2id[h*len+gp];
		
		Utils.Assert(id >= 0 && arc2id[m*len+h] >= 0 && arc2id[s*len+h] >= 0);
		
		int pos = (id*len+m)*len+s;
		if (gpsib[pos] == NULL) {
			ScoreCollector col = new ScoreCollector(parameters);
			synFactory.createGPSibFeatureVector(col, inst, gp, h, m, s);
			gpsib[pos] = col.score * gamma;
			//getGPSibFeatureVector(gp, h, m, s);
		}
		
		return gpsib[pos];
	}
	
	private final double getTriSibScore(int h, int s1, int m, int s2) {
		// s1 < m < s2
		int id = arc2id[m*len+h];
		
		Utils.Assert(id >= 0 && arc2id[s1*len+h] >= 0 && arc2id[s2*len+h] >= 0);
		
		int pos = (id*len+s1)*len+s2;
		if (trisib[pos] == NULL) {
			ScoreCollector col = new ScoreCollector(parameters);
			synFactory.createTriSibFeatureVector(col, inst, h, s1, m, s2);
			trisib[pos] = col.score * gamma;
			//getTriSibFeatureVector(h, s1, m, s2);
		}
		
		return trisib[pos];
	}
	
	private final double getGGPCScore(int ggp, int gp, int h, int m) {
		int id1 = arc2id[gp * len + ggp];
		int id2 = arc2id[m * len + h];
		
		Utils.Assert(id1 >= 0 && id2 >= 0 && arc2id[h * len + gp] >= 0);
		
		int pos = id1 * numarcs + id2;
		if (ggpc[pos] == NULL) {
			ScoreCollector col = new ScoreCollector(parameters);
			synFactory.createGGPCFeatureVector(col, inst, ggp, gp, h, m);
			ggpc[pos] = col.score * gamma;
			//getGGPCFeatureVector(ggp, gp, h, m);
		}
		
		return ggpc[pos];
	}
	
	private final double getPSCScore(int h, int m, int c, int sib) {
		int id1 = arc2id[sib * len + h];
		int id2 = arc2id[c * len + m];
		
		Utils.Assert(id1 >= 0 && id2 >= 0 && arc2id[m * len + h] >= 0);
		
		int pos = id1 * numarcs + id2;
		if (psc[pos] == NULL) {
			ScoreCollector col = new ScoreCollector(parameters);
			synFactory.createPSCFeatureVector(col, inst, h, m, c, sib);
			psc[pos] = col.score * gamma;
			//getPSCFeatureVector(h, m, c, sib);
		}

		return psc[pos];
	}
	
	public double getPartialScore2(int[] heads, int x, DependencyArcList arcLis)
	{
		// 1st order arc
		double score = arcScores[heads[x]*len+x];
		
		if (options.learningMode != LearningMode.Basic) {
			
			//DependencyArcList arcLis = new DependencyArcList(heads);
			
			// 2nd order (h,m,s) & (m,s)
			//for (int h = 0; h < len; ++h) /*if (!isPruned(h, x)) (h != x)*/ {
			int headst = startIndex(x), headed = endIndex(x);
			for (int hid = headst; hid < headed; ++hid) {
				int h = getHead(hid);
				int st = arcLis.startIndex(h);
				int ed = arcLis.endIndex(h);
				
				if (st >= ed || x < arcLis.get(st) || x > arcLis.get(ed-1) /*|| isPruned(h, x)*/) continue;
				
				int gp = heads[h];
				
				Utils.Assert(isPruned(h,x)==false && h!=x);
				
				for (int p = st; p+1 < ed; ++p) {
					// mod and sib
					int m = arcLis.get(p);
					int s = arcLis.get(p+1);
					
					if (x < m) break;
					
					if (options.useCS && x <= s)
						score += getTripsScore(h, m, s) + getSibScore(m, s);
					
					// tri-sibling
					if (options.useTS && p + 2 < ed) {
						int s2 = arcLis.get(p + 2);
						if (x <= s2)
							score += getTriSibScore(h, m, s, s2);
					}	
					
					//if (x < m) break;
					if (x > s) continue;
					
					// gp-sibling
					if (options.useGS && gp >= 0 /*&& m <= x && x <= s*/)
						score += getGPSibScore(gp, h, m, s);
					
					// parent, sibling and child
					if (options.usePSC) {
						// mod's child
						int mst = arcLis.startIndex(m);
						int med = arcLis.endIndex(m);
						
						for (int mp = mst; mp < med; ++mp) {
							int c = arcLis.get(mp);
							//if ((m <= x && x <= s) || x == c)
							if (x != c)
							score += getPSCScore(h, m, c, s);
						}
						
						// sib's child
						int sst = arcLis.startIndex(s);
						int sed = arcLis.endIndex(s);
						
						for (int sp = sst; sp < sed; ++sp) {
							int c = arcLis.get(sp);
							//if ((m <= x && x <= s) || x == c)
							if (x != c)
							score += getPSCScore(h, s, c, m);
						}
					}
				}
			}
			
			// g--x--m&s
			if (options.useGS) {
				int gp = heads[x];
				int st = arcLis.startIndex(x);
				int ed = arcLis.endIndex(x);
				for (int p = st; p+1 < ed; ++p) {
					// mod and sib
					int m = arcLis.get(p);
					int s = arcLis.get(p+1);
					score += getGPSibScore(gp, x, m, s);
				}
			}
			
			// h--m&m2--x
			if (options.usePSC) {
				int m = heads[x];
				int h = heads[m];
				if (h >= 0) {
					int st = arcLis.startIndex(h);
					int ed = arcLis.endIndex(h);
					int p;
					for (p = st; p < ed; ++p) 
						if (arcLis.get(p) == m) break;
					Utils.Assert(p < ed);
					if (p > st)
						score += getPSCScore(h, m, x, arcLis.get(p-1));
					if (p+1 < ed)
						score += getPSCScore(h, m, x, arcLis.get(p+1));
				}
			}
			
			// g--h--m;  gg--h--m;  h--m h'--m+1 
			for (int m = 1; m < len; ++m) {
				int h = heads[m];
				
				Utils.Assert(h >= 0);
				
				// grandparent
				int gp = heads[h];
				if (options.useGP && gp != -1
						&& (x == m || x == h)) {
					score += getGPCScore(gp, h, m);
				}
				
				// head bigram
				if (options.useHB && m + 1 < len
						&& (x == m || x == m + 1)) {
					int h2 = heads[m + 1];
					Utils.Assert(h2 >= 0);
					
					score += getHeadBiScore(h, m, h2);
				}

				// great-grandparent
				if (options.useGGP && gp != -1 && heads[gp] != -1
						&& (x == m || x == h || x == gp)) {
					int ggp = heads[gp];
					score += getGGPCScore(ggp, gp, h, m);
				}
			}
		}
		
		return score;
	}
	
	public double getPartialScore(int[] heads, int x, DependencyArcList arcLis)
	{
		// 1st order arc
		double score = arcScores[heads[x]*len+x];
		
		if (options.learningMode != LearningMode.Basic) {
			
			//DependencyArcList arcLis = new DependencyArcList(heads);
			
			// 2nd order (h,m,s) & (m,s)
			for (int h = 0; h < len; ++h) if /*(!isPruned(h, x))*/ (h != x) {
				
				int st = arcLis.startIndex(h);
				int ed = arcLis.endIndex(h);
				
				if (st >= ed || x < arcLis.get(st) || x > arcLis.get(ed-1)) continue;
				
				for (int p = st; p+1 < ed; ++p) {
					// mod and sib
					int m = arcLis.get(p);
					int s = arcLis.get(p+1);
					
					if (options.useCS) {
						if (m <= x && x <= s)
							score += getTripsScore(h, m, s) + getSibScore(m, s);
					}
					
					// tri-sibling
					if (options.useTS && p + 2 < ed) {
						int s2 = arcLis.get(p + 2);
						if (m <= x && x <= s2)
							score += getTriSibScore(h, m, s, s2);
					}
					
					if (x < m) break;
					
				}
			}

			for (int h = 0; h < len; ++h) {
				
				int st = arcLis.startIndex(h);
				int ed = arcLis.endIndex(h);
				
				for (int p = st; p+1 < ed; ++p) {
					// mod and sib
					int m = arcLis.get(p);
					int s = arcLis.get(p+1);
					
					// gp-sibling
					int gp = heads[h];
					if (options.useGS && gp >= 0) {
						if (x == h || (m <= x && x <= s))
							score += getGPSibScore(gp, h, m, s);
					}
					
					// parent, sibling and child
					if (options.usePSC) {
						// mod's child
						int mst = arcLis.startIndex(m);
						int med = arcLis.endIndex(m);
						
						for (int mp = mst; mp < med; ++mp) {
							int c = arcLis.get(mp);
							if ((m <= x && x <= s) || x == c)
								score += getPSCScore(h, m, c, s);
						}
						
						// sib's child
						int sst = arcLis.startIndex(s);
						int sed = arcLis.endIndex(s);
						
						for (int sp = sst; sp < sed; ++sp) {
							int c = arcLis.get(sp);
							if ((m <= x && x <= s) || x == c)
								score += getPSCScore(h, s, c, m);
						}
					}
				}
			}
			
			for (int m = 1; m < len; ++m) {
				int h = heads[m];
				
				Utils.Assert(h >= 0);
				
				// grandparent
				int gp = heads[h];
				if (options.useGP && gp != -1
						&& (x == m || x == h)) {
					score += getGPCScore(gp, h, m);
				}
				
				// head bigram
				if (options.useHB && m + 1 < len
						&& (x == m || x == m + 1)) {
					int h2 = heads[m + 1];
					Utils.Assert(h2 >= 0);
					
					score += getHeadBiScore(h, m, h2);
				}

				// great-grandparent
				if (options.useGGP && gp != -1 && heads[gp] != -1
						&& (x == m || x == h || x == gp)) {
					int ggp = heads[gp];
					score += getGGPCScore(ggp, gp, h, m);
				}
			}
		}
		
		return score;
	}
	
	public double getScore(DependencyInstance now, DependencyArcList arcLis)
	{
		double score = 0;		
		int[] heads = now.heads;
		
		// 1st order arc
		for (int m = 1; m < len; ++m)
			score += arcScores[heads[m]*len+m];
		
		if (options.learningMode != LearningMode.Basic) {
			
			//DependencyArcList arcLis = new DependencyArcList(heads, options.useHO);
			
			// 2nd order (h,m,s) & (m,s)
			for (int h = 0; h < len; ++h) {
				
				int st = arcLis.startIndex(h);
				int ed = arcLis.endIndex(h);
				
				for (int p = st; p+1 < ed; ++p) {
					// mod and sib
					int m = arcLis.get(p);
					int s = arcLis.get(p+1);
					
					if (options.useCS) {
						score += getTripsScore(h, m, s);
						score += getSibScore(m, s);
					}
					
					// gp-sibling
					int gp = heads[h];
					if (options.useGS && gp >= 0) {
						score += getGPSibScore(gp, h, m, s);
					}
					
					// tri-sibling
					if (options.useTS && p + 2 < ed) {
						int s2 = arcLis.get(p + 2);
						score += getTriSibScore(h, m, s, s2);
					}
					
					// parent, sibling and child
					if (options.usePSC) {
						// mod's child
						int mst = arcLis.startIndex(m);
						int med = arcLis.endIndex(m);
						
						for (int mp = mst; mp < med; ++mp) {
							int c = arcLis.get(mp);
							score += getPSCScore(h, m, c, s);
						}
						
						// sib's child
						int sst = arcLis.startIndex(s);
						int sed = arcLis.endIndex(s);
						
						for (int sp = sst; sp < sed; ++sp) {
							int c = arcLis.get(sp);
							score += getPSCScore(h, s, c, m);
						}
					}
				}
			}
			
			for (int m = 1; m < len; ++m) {
				int h = heads[m];
				
				Utils.Assert(h >= 0);
				
				// grandparent
				int gp = heads[h];
				if (options.useGP && gp != -1) {
					score += getGPCScore(gp, h, m);
				}
				
				// head bigram
				if (options.useHB && m + 1 < len) {
					int h2 = heads[m + 1];
					Utils.Assert(h2 >= 0);
					
					score += getHeadBiScore(h, m, h2);
				}
				
				// great-grandparent
				if (options.useGGP && gp != -1 && heads[gp] != -1) {
					int ggp = heads[gp];
					score += getGGPCScore(ggp, gp, h, m);
				}
			}
		}
		
		return score;
	}
	
	public FeatureVector getArcFeatureVector(int h, int m)
	{
		return arcFvs[h*len+m];
	}
	
	public FeatureVector getFeatureVector(DependencyInstance now)
	{
		FeatureVector fv = new FeatureVector(size);
		
		int[] heads = now.heads;
		
		// 1st order arc
		for (int m = 1; m < len; ++m)
			fv.addEntries(arcFvs[heads[m]*len+m]);
		
		if (options.learningMode != LearningMode.Basic) {
			
			DependencyArcList arcLis = new DependencyArcList(heads, options.useHO);
			
			// 2nd order (h,m,s) & (m,s)
			for (int h = 0; h < len; ++h) {
				
				int st = arcLis.startIndex(h);
				int ed = arcLis.endIndex(h);
				
				for (int p = st; p+1 < ed; ++p) {
					// mod and sib
					int m = arcLis.get(p);
					int s = arcLis.get(p+1);
					
					if (options.useCS) {
						fv.addEntries(getTripsFeatureVector(h,m,s));
						fv.addEntries(getSibFeatureVector(m,s));
					}
					
					// gp-sibling
					int gp = heads[h];
					if (options.useGS && gp >= 0) {
						fv.addEntries(getGPSibFeatureVector(gp, h, m, s));
					}
					
					// tri-sibling
					if (options.useTS && p + 2 < ed) {
						int s2 = arcLis.get(p + 2);
						fv.addEntries(getTriSibFeatureVector(h, m, s, s2));
					}
					
					// parent, sibling and child
					if (options.usePSC) {
						// mod's child
						int mst = arcLis.startIndex(m);
						int med = arcLis.endIndex(m);
						
						for (int mp = mst; mp < med; ++mp) {
							int c = arcLis.get(mp);
							fv.addEntries(getPSCFeatureVector(h, m, c, s));
						}
						
						// sib's child
						int sst = arcLis.startIndex(s);
						int sed = arcLis.endIndex(s);
						
						for (int sp = sst; sp < sed; ++sp) {
							int c = arcLis.get(sp);
							fv.addEntries(getPSCFeatureVector(h, s, c, m));
						}
					}
				}
			}
			
			for (int m = 1; m < len; ++m) {
				int h = heads[m];
				
				Utils.Assert(h >= 0);
				
				// grandparent
				int gp = heads[h];
				if (options.useGP && gp != -1) {
					fv.addEntries(getGPCFeatureVector(gp, h, m));
				}
				
				// head bigram
				if (options.useHB && m + 1 < len) {
					int h2 = heads[m + 1];
					Utils.Assert(h2 >= 0);
					
					fv.addEntries(getHeadBiFeatureVector(h, m, h2));
				}
				
				// great-grandparent
				if (options.useGGP && gp != -1 && heads[gp] != -1) {
					int ggp = heads[gp];
					fv.addEntries(getGGPCFeatureVector(ggp, gp, h, m));
				}
			}
			
		}
		
		return fv;
	}
	
	public FeatureVector getTripsFeatureVector(int h, int m, int s)
	{
		int id = arc2id[m*len+h];
		
		Utils.Assert(id >= 0 && arc2id[s*len+h] >= 0);
		
		//int pos = id*len+s;
		FeatureVector fv = new FeatureVector(size);
		synFactory.createTripsFeatureVector(fv, inst, h, m, s);
		//trips[pos] = parameters.dotProduct(fv) * gamma;			
		return fv;
	}
	
	public FeatureVector getSibFeatureVector(int m, int s)
	{
		//int pos = m*len+s;				
		FeatureVector fv = new FeatureVector(size);
		synFactory.createSibFeatureVector(fv, inst, m, s/*, false*/);
		//sib[pos] = parameters.dotProduct(fv) * gamma;
		return fv;
	}
	
	public FeatureVector getGPCFeatureVector(int gp, int h, int m) {
		int id = arc2id[h*len+gp];
		
		Utils.Assert(id >= 0 && arc2id[m*len+h] >= 0);
		
		//int pos = id*len+m;
		FeatureVector fv = new FeatureVector(size);
		synFactory.createGPCFeatureVector(fv, inst, gp, h, m);
		//gpc[pos] = parameters.dotProduct(fv) * gamma;
		return fv;
	}
	
	public FeatureVector getHeadBiFeatureVector(int h, int m, int h2) {
		int id = arc2id[m*len+h];
		
		Utils.Assert(id >= 0 && m + 1 < len 
				&& arc2id[(m + 1)*len+h2] >= 0);
		
		//int pos = id*len+h2;

		FeatureVector fv = new FeatureVector(size);
		synFactory.createHeadBiFeatureVector(fv, inst, m, h, h2);
		//headbi[pos] = parameters.dotProduct(fv) * gamma;
		return fv;
	}
	
	public FeatureVector getGPSibFeatureVector(int gp, int h, int m, int s) {
		// m < s
		int id = arc2id[h*len+gp];
		
		Utils.Assert(id >= 0 && arc2id[m*len+h] >= 0 && arc2id[s*len+h] >= 0);
		
		//int pos = (id*len+m)*len+s;
		FeatureVector fv = new FeatureVector(size);
		synFactory.createGPSibFeatureVector(fv, inst, gp, h, m, s);
		//gpsib[pos] = parameters.dotProduct(fv) * gamma;
		return fv;
	}
	
	public FeatureVector getTriSibFeatureVector(int h, int s1, int m, int s2) {
		// s1 < m < s2
		int id = arc2id[m*len+h];
		
		Utils.Assert(id >= 0 && arc2id[s1*len+h] >= 0 && arc2id[s2*len+h] >= 0);
		
		//int pos = (id*len+s1)*len+s2;
		FeatureVector fv = new FeatureVector(size);
		synFactory.createTriSibFeatureVector(fv, inst, h, s1, m, s2);
		//trisib[pos] = parameters.dotProduct(fv) * gamma;
		return fv;
	}
	
	public FeatureVector getGGPCFeatureVector(int ggp, int gp, int h, int m) {
		int id1 = arc2id[gp * len + ggp];
		int id2 = arc2id[m * len + h];
		
		Utils.Assert(id1 >= 0 && id2 >= 0 && arc2id[h * len + gp] >= 0);
		
		//int pos = id1 * numarcs + id2;
		FeatureVector fv = new FeatureVector(size);
		synFactory.createGGPCFeatureVector(fv, inst, ggp, gp, h, m);
		//ggpc[pos] = parameters.dotProduct(fv) * gamma;
		return fv;
	}
	
	public FeatureVector getPSCFeatureVector(int h, int m, int c, int sib) {
		int id1 = arc2id[sib * len + h];
		int id2 = arc2id[c * len + m];
		
		Utils.Assert(id1 >= 0 && id2 >= 0 && arc2id[m * len + h] >= 0);
		
		//int pos = id1 * numarcs + id2;
		FeatureVector fv = new FeatureVector(size);
		synFactory.createPSCFeatureVector(fv, inst, h, m, c, sib);
		//psc[pos] = parameters.dotProduct(fv) * gamma;
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
	
	private FeatureVector getLabelFeature(DependencyArcList arcLis, int[] heads, int mod, int type)
	{
		FeatureVector fv = new FeatureVector(sizeL);
		synFactory.createLabelFeatures(fv, inst, arcLis, heads, mod, type);
		return fv;
	}
	
	private double getLabelScore(DependencyArcList arcLis, int[] heads, int mod, int type)
	{
		ScoreCollector col = new ScoreCollector(parameters, true);
		synFactory.createLabelFeatures(col, inst, arcLis, heads, mod, type);
		return col.score;
	}
	
	public void predictLabels(int[] heads, int[] deplbids, boolean addLoss)
	{
		assert(heads.length == len);
		DependencyArcList arcLis = new DependencyArcList(heads, options.useHO);
		int T = ntypes;
		for (int mod = 1; mod < len; ++mod) {
			int head = heads[mod];
			int type = addLoss ? 0 : 1;
			double best = getLabelScore(arcLis, heads, mod, type) +
				(addLoss && inst.deplbids[mod] != 0 ? 1.0 : 0.0);
			for (int t = type+1; t < T; ++t) {
				double va = getLabelScore(arcLis, heads, mod, t) +
					(addLoss && inst.deplbids[mod] != t ? 1.0 : 0.0);
				if (va > best) {
					best = va;
					type = t;
				}
			}
			deplbids[mod] = type;
		}
	}
	
	public FeatureVector getLabeledFeatureDifference(DependencyInstance gold, 
			DependencyInstance pred)
	{
		assert(gold.heads == pred.heads);
		
		if (!options.learnLabel) return null;
		
		FeatureVector dlfv = new FeatureVector(sizeL);

		
    	int N = inst.length;
    	int[] actDeps = gold.heads;
    	int[] actLabs = gold.deplbids;
    	int[] predDeps = pred.heads;
    	int[] predLabs = pred.deplbids;
    	DependencyArcList arcLis = new DependencyArcList(gold.heads, options.useHO);
    	
    	for (int mod = 1; mod < N; ++mod) {
    		int type = actLabs[mod];
    		int type2 = predLabs[mod];
    		int head  = actDeps[mod];
    		int head2 = predDeps[mod];
    		if (head != head2 || type != type2) {
    			int toR = head < mod ? 1 : 0;        		
    			int toR2 = head2 < mod ? 1 : 0;   
    			dlfv.addEntries(getLabelFeature(arcLis, actDeps, mod, type));
    			dlfv.addEntries(getLabelFeature(arcLis, predDeps, mod, type2), -1.0);
    			
    			//dlfv.addEntries(lbFvs[head][type][toR][0]);
    			//dlfv.addEntries(lbFvs[mod][type][toR][1]);
    			//dlfv.addEntries(lbFvs[head2][type2][toR2][0], -1.0);
    			//dlfv.addEntries(lbFvs[mod][type2][toR2][1], -1.0);
    		}
    	}
		
		return dlfv;
	}
	
}

//class FeatureDataItem {
//	final FeatureVector fv;
//	final double score;
//	
//	public FeatureDataItem(FeatureVector fv, double score)
//	{
//		this.fv = fv;
//		this.score = score;
//	}
//}
