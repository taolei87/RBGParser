package parser;

import parser.Options.LearningMode;
import parser.decoding.DependencyDecoder;
import utils.FeatureVector;
import utils.Utils;

public class LocalFeatureData {
		
	DependencyInstance inst;
	DependencyPipe pipe;
	Options options;
	Parameters parameters;
	
	public DependencyParser pruner;
	DependencyDecoder prunerDecoder;
	
	int len;						// sentence length
	int ntypes;						// number of label types
	int size;						
	int rank;								
	double gamma, gammaLabel;
	
	int nuparcs;					// number of un-pruned arcs
	int[] arc2id;					// map (h->m) arc to an id in [0, nuparcs-1]
	public boolean[] isPruned;				// whether a (h->m) arc is pruned								
	
	FeatureVector[] wordFvs;		// word feature vectors
	double[][] wpU, wpV;			// word projections U\phi and V\phi
	
	FeatureVector[] arcFvs;			// 1st order arc feature vectors
	double[] arcScores;				// 1st order arc scores (including tensor)
    double[] arcNtScores;

	FeatureVector[][][][] lbFvs;	// labeled-arc feature vectors
	double[][][][] lbScores;		// labeled-arc scores
	
	//TODO: add high order feature vectors and score tables
	
	FeatureDataItem[] trips;		// [dep id][sib]
	
	FeatureDataItem[] sib;			// [mod][sib]
	
	FeatureDataItem[] gpc;			// [dep id][child]

	FeatureDataItem[] headbi;		// [dep id][head2]

	FeatureDataItem[] gpsib;		// grandparent-parent-child-sibling, gp-p is mapped to id [dep id][sib][mod]

	FeatureDataItem[] trisib;		// parent-sibling-child-sibling [dep id][in sib][out sib]
	
	FeatureDataItem[] ggpc;			// [dep id (ggp, gp)][dep id (p, mod)]
	
	FeatureDataItem[] psc;			// parent-sib-mod-child, [dep id (p, sib)][dep id (mod, child)]
	
	
	public LocalFeatureData(DependencyInstance inst,
			DependencyParser parser, boolean indexGoldArcs) 
	{
		this.inst = inst;
		pipe = parser.pipe;
		options = parser.options;
		parameters = parser.parameters;
		pruner = parser.pruner;
		prunerDecoder = pruner == null ? null : 
			DependencyDecoder.createDependencyDecoder(pruner.options);
			
		Utils.Assert(pruner == null || pruner.options.learningMode == LearningMode.Basic);
		
		len = inst.length;
		ntypes = pipe.types.length;
		rank = options.R;
		size = pipe.numArcFeats;
		gamma = options.gamma;
		gammaLabel = options.gammaLabel;
		
		wordFvs = new FeatureVector[len];
		wpU = new double[len][rank];
		wpV = new double[len][rank];
		
		arcFvs = new FeatureVector[len*len];
		arcScores = new double[len*len];
	    arcNtScores = new double[len*len];

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
					arcFvs[i*len+j] = pipe.createArcFeatures(inst, i, j);
                    arcNtScores[i*len+j] = parameters.dotProduct(arcFvs[i*len+j]) * gamma;
					arcScores[i*len+j] = parameters.dotProduct(arcFvs[i*len+j]) * gamma
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
									lbFvs[i][t][j][k]) * gammaLabel;
						}
		}
	}
	
	public void initHighOrderFeatureTables() {
		// init non-first-order feature tables
		
		if (options.useCS) {
			// 2nd order (head, mod, mod_sib) features
			trips = new FeatureDataItem[nuparcs*len];	
			
			// 2nd order (mod, mod_sib) features
			sib = new FeatureDataItem[len*len];
		}
		
		if (options.useGP) {
			// 2nd order (head, mod, child) features
			gpc = new FeatureDataItem[nuparcs*len];
		}
		
		if (options.useHB) {
			// 2nd order (head, mod, head2) features
			headbi = new FeatureDataItem[nuparcs*len];
		}
		
		if (options.useGS) {
			// 3rd order (grand, head, sib, mod) features
			gpsib = new FeatureDataItem[nuparcs*len*len];
		}
		
		if (options.useTS) {
			// 3rd order (head, sib1, mod, sib2) features
			trisib = new FeatureDataItem[nuparcs*len*len];
		}
		
		if (options.useGGP) {
			// 3rd order (great-grand, grand, head, mod) features
			ggpc = new FeatureDataItem[nuparcs*nuparcs];
		}

		if (options.usePSC) {
			// 3rd order (head, mod, sib, child) features
			psc = new FeatureDataItem[nuparcs*nuparcs];
		}
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
			if (includeGoldArcs) pruner.pruningTotGold += len-1;
			pruner.pruningTotArcs += (len-1)*(len-1);
			
			for (int i = 0, L = arc2id.length; i < L; ++i) {
				arc2id[i] = -1;
				isPruned[i] = true;
			}
			
			// Use the threshold to prune arcs. 
			double threshold = Math.log(options.pruningCoeff);
			LocalFeatureData lfd2 = new LocalFeatureData(inst, pruner, false);
			GlobalFeatureData gfd2 = null;
			DependencyInstance pred = prunerDecoder.decode(inst, lfd2, gfd2, false);
							
			nuparcs = 0;
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
						 (v >= maxv + threshold || h == pred.heads[m])) {
							isPruned[m*len+h] = !(v >= maxv + threshold || h == pred.heads[m]);
							arc2id[m*len+h] = nuparcs;
							nuparcs++;							
						}
					}
			}
			
			if (includeGoldArcs)
				for (int m = 1; m < len; ++m)
					if (!isPruned[m*len+inst.heads[m]])
						pruner.pruningGoldHits++;
			pruner.pruningTotUparcs += nuparcs;
		}
	}
	
	private void traverse(int x, boolean[] vis)
	{
		vis[x] = false;
		for (int y = 1; y < len; ++y)
			if (vis[y] && !isPruned(x, y)) traverse(y, vis);
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
    
    public boolean hasPruning()
    {
        return isPruned != null;
    }

	public boolean isPruned(int h, int m) 
	{
		return isPruned[m*len+h];
	}
	
	public double getArcScore(int h, int m)
	{
		return arcScores[h*len+m];
	}

    public double getArcNoTensorScore(int h, int m)
    {
        return arcNtScores[h*len+m];
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
		
		int pos = id*len+s;
		if (trips[pos] == null)
			getTripsFeatureVector(h, m, s);
		
		return trips[pos].score;
	}
	
	public double getSibScore(int m, int s)
	{
		int pos = m*len+s;
		if (sib[pos] == null)
			getSibFeatureVector(m, s);
		
		return sib[pos].score;
	}
	
	
	public double getGPCScore(int gp, int h, int m) {
		int id = arc2id[h*len+gp];
		
		Utils.Assert(id >= 0 && arc2id[m*len+h] >= 0);
		
		int pos = id*len+m;
		if (gpc[pos] == null)
			getGPCFeatureVector(gp, h, m);
		
		return gpc[pos].score;
	}
	
	public double getHeadBiScore(int h, int m, int h2) {
		int id = arc2id[m*len+h];
		
		Utils.Assert(id >= 0 && m + 1 < len 
				&& arc2id[(m + 1)*len+h2] >= 0);
		
		int pos = id*len+h2;
		if (headbi[pos] == null)
			getHeadBiFeatureVector(h, m, h2);

		return headbi[pos].score;
	}
	
	public double getGPSibScore(int gp, int h, int m, int s) {
		// m < s
		int id = arc2id[h*len+gp];
	    
        //System.out.println(gp + " " + h + " " + m + " " + s + " " + isPruned(gp, h) + " " + isPruned(h,s) + " " + isPruned(h,m));
		Utils.Assert(id >= 0 && arc2id[m*len+h] >= 0 && arc2id[s*len+h] >= 0);
		
		int pos = (id*len+m)*len+s;
		if (gpsib[pos] == null)
			getGPSibFeatureVector(gp, h, m, s);
		
		return gpsib[pos].score;
	}
	
	public double getTriSibScore(int h, int s1, int m, int s2) {
		// s1 < m < s2
		int id = arc2id[m*len+h];
		
		Utils.Assert(id >= 0 && arc2id[s1*len+h] >= 0 && arc2id[s2*len+h] >= 0);
		
		int pos = (id*len+s1)*len+s2;
		if (trisib[pos] == null)
			getTriSibFeatureVector(h, s1, m, s2);
		
		return trisib[pos].score;
	}
	
	public double getGGPCScore(int ggp, int gp, int h, int m) {
		int id1 = arc2id[gp * len + ggp];
		int id2 = arc2id[m * len + h];
		
		Utils.Assert(id1 >= 0 && id2 >= 0 && arc2id[h * len + gp] >= 0);
		
		int pos = id1 * nuparcs + id2;
		if (ggpc[pos] == null)
			getGGPCFeatureVector(ggp, gp, h, m);

		return ggpc[pos].score;
	}
	
	public double getPSCScore(int h, int m, int c, int sib) {
		int id1 = arc2id[sib * len + h];
		int id2 = arc2id[c * len + m];
		
		Utils.Assert(id1 >= 0 && id2 >= 0 && arc2id[m * len + h] >= 0);
		
		int pos = id1 * nuparcs + id2;
		if (psc[pos] == null)
			getPSCFeatureVector(h, m, c, sib);

		return psc[pos].score;
	}
	
	public double getPartialScore(int[] heads, int x)
	{
		// 1st order arc
		double score = arcScores[heads[x]*len+x];
		
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
	
	public double getScore(DependencyInstance now)
	{
		double score = 0;		
		int[] heads = now.heads;
		
		// 1st order arc
		for (int m = 1; m < len; ++m)
			score += arcScores[heads[m]*len+m];
		
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
			
			DependencyArcList arcLis = new DependencyArcList(heads);
			
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
		
		int pos = id*len+s;
		FeatureDataItem item = trips[pos];
		if (item == null) {
			FeatureVector fv = pipe.createTripsFeatureVector(inst, h, m, s);
			double score = parameters.dotProduct(fv) * gamma;
			item = new FeatureDataItem(fv, score);
			trips[pos] = item;			
		}
		return item.fv;
	}
	
	public FeatureVector getSibFeatureVector(int m, int s)
	{
		int pos = m*len+s;
		FeatureDataItem item = sib[pos];
		if (item == null) {					
			FeatureVector fv = pipe.createSibFeatureVector(inst, m, s/*, false*/);
			double score = parameters.dotProduct(fv) * gamma;
			item = new FeatureDataItem(fv, score);
			sib[pos] = item;
		}
		return item.fv;
	}
	
	public FeatureVector getGPCFeatureVector(int gp, int h, int m) {
		int id = arc2id[h*len+gp];
		
		Utils.Assert(id >= 0 && arc2id[m*len+h] >= 0);
		
		int pos = id*len+m;
		FeatureDataItem item = gpc[pos];
		if (item == null) {
			FeatureVector fv = pipe.createGPCFeatureVector(inst, gp, h, m);
			double score = parameters.dotProduct(fv) * gamma;
			item = new FeatureDataItem(fv, score);
			gpc[pos] = item;			
		}
		return item.fv;
	}
	
	public FeatureVector getHeadBiFeatureVector(int h, int m, int h2) {
		int id = arc2id[m*len+h];
		
		Utils.Assert(id >= 0 && m + 1 < len 
				&& arc2id[(m + 1)*len+h2] >= 0);
		
		int pos = id*len+h2;
		FeatureDataItem item = headbi[pos];
		if (item == null) {
			FeatureVector fv = pipe.createHeadBiFeatureVector(inst, m, h, h2);
			double score = parameters.dotProduct(fv) * gamma;
			item = new FeatureDataItem(fv, score);
			headbi[pos] = item;			
		}
		return item.fv;
	}
	
	public FeatureVector getGPSibFeatureVector(int gp, int h, int m, int s) {
		// m < s
		int id = arc2id[h*len+gp];
		
		Utils.Assert(id >= 0 && arc2id[m*len+h] >= 0 && arc2id[s*len+h] >= 0);
		
		int pos = (id*len+m)*len+s;
		FeatureDataItem item = gpsib[pos];
		if (item == null) {
			FeatureVector fv = pipe.createGPSibFeatureVector(inst, gp, h, m, s);
			double score = parameters.dotProduct(fv) * gamma;
			item = new FeatureDataItem(fv, score);
			gpsib[pos] = item;			
		}
		return item.fv;
	}
	
	public FeatureVector getTriSibFeatureVector(int h, int s1, int m, int s2) {
		// s1 < m < s2
		int id = arc2id[m*len+h];
		
		Utils.Assert(id >= 0 && arc2id[s1*len+h] >= 0 && arc2id[s2*len+h] >= 0);
		
		int pos = (id*len+s1)*len+s2;
		FeatureDataItem item = trisib[pos];
		if (item == null) {
			FeatureVector fv = pipe.createTriSibFeatureVector(inst, h, s1, m, s2);
			double score = parameters.dotProduct(fv) * gamma;
			item = new FeatureDataItem(fv, score);
			trisib[pos] = item;			
		}
		return item.fv;
	}
	
	public FeatureVector getGGPCFeatureVector(int ggp, int gp, int h, int m) {
		int id1 = arc2id[gp * len + ggp];
		int id2 = arc2id[m * len + h];
		
		Utils.Assert(id1 >= 0 && id2 >= 0 && arc2id[h * len + gp] >= 0);
		
		int pos = id1 * nuparcs + id2;
		FeatureDataItem item = ggpc[pos];
		if (item == null) {
			FeatureVector fv = pipe.createGGPCFeatureVector(inst, ggp, gp, h, m);
			double score = parameters.dotProduct(fv) * gamma;
			item = new FeatureDataItem(fv, score);
			ggpc[pos] = item;
		}
		return item.fv;
	}
	
	public FeatureVector getPSCFeatureVector(int h, int m, int c, int sib) {
		int id1 = arc2id[sib * len + h];
		int id2 = arc2id[c * len + m];
		
		Utils.Assert(id1 >= 0 && id2 >= 0 && arc2id[m * len + h] >= 0);
		
		int pos = id1 * nuparcs + id2;
		FeatureDataItem item = psc[pos];
		if (item == null) {
			FeatureVector fv = pipe.createPSCFeatureVector(inst, h, m, c, sib);
			double score = parameters.dotProduct(fv) * gamma;
			item = new FeatureDataItem(fv, score);
			psc[pos] = item;
		}
		return item.fv;
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

class FeatureDataItem {
	final FeatureVector fv;
	final double score;
	
	public FeatureDataItem(FeatureVector fv, double score)
	{
		this.fv = fv;
		this.score = score;
	}
}
