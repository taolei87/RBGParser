package optimality;

import parser.DependencyInstance;
import parser.LocalFeatureData;
import parser.Options;
import parser.decoding.ChuLiuEdmondDecoder;

public class TreeAutomaton {
	// first-order decoding
	
	public int length;
	
	public LocalFeatureData lfd;
	
	public Options options;

	public boolean[] y;		// optimal solution
	
	public double beta;

	public DependencyInstance gold;
	
	public boolean addLoss;
	
	public GpSibAutomaton gpSibAuto;
	
	public TreeAutomaton(int length, LocalFeatureData lfd, GpSibAutomaton gpSibAuto, DependencyInstance gold, boolean addLoss, Options options) {
		this.length = length;
		this.lfd = lfd;
		this.options = options;
		this.gold = gold;
		this.addLoss = addLoss;
		this.gpSibAuto = gpSibAuto;
		
		beta = options.optBeta;
		y = new boolean[length * length];
	}
	
	public int getIndex(int h, int m) {
		return h * length + m;
	}
	
	public double maximize() {
		ChuLiuEdmondDecoder decoder = new ChuLiuEdmondDecoder(options);
		
		int N = length;
        int M = N << 1;
        
        double[][] scores = new double[M][M];
        int[][] oldI = new int[M][M];
        int[][] oldO = new int[M][M];
        for (int i = 0; i < N; ++i)
            for (int j = 1; j < N; ++j) 
                if (i != j) {
                    oldI[i][j] = i;
                    oldO[i][j] = j;
                    double va = lfd.getArcScore(i,j);
                    if (addLoss && gold.heads[j] != i) va += 1.0;                    
                    scores[i][j] = (1 - beta) * va;
                    if (gpSibAuto != null) {
                    	scores[i][j] += gpSibAuto.lSib[getIndex(i, j)] + gpSibAuto.lHead[getIndex(i, j)];
                    }
                }

        boolean[] ok = new boolean[M];
        boolean[] vis = new boolean[M];
        boolean[] stack = new boolean[M];
        for (int i = 0; i < M; ++i) 
        	ok[i] = true;

        int[] final_par = new int[M];
        for (int i = 0; i < M; ++i) 
        	final_par[i] = -1;

        decoder.chuLiuEdmond(N, scores, ok, vis, stack, oldI, oldO, final_par);
        
        for (int i = 0; i < y.length; ++i)
        	y[i] = false;
        
        double score = 0.0;
        
        for (int i = 1; i < N; ++i) {
        	y[getIndex(final_par[i], i)] = true;
        	score += scores[final_par[i]][i];
        }
        
        return score;
	}
	
	public double computeScore(DependencyInstance inst) {
		double score = 0.0;
		
        for (int m = 1; m < length; ++m) {
        	int h = inst.heads[m];
        	double va = lfd.getArcScore(h, m);
        	if (addLoss && gold.heads[m] != h) 
        		va += 1.0;                    
        	score += (1 - beta) * va;
        	if (gpSibAuto != null) {
        		score += gpSibAuto.lSib[getIndex(h, m)] + gpSibAuto.lHead[getIndex(h, m)];
        	}
        }

        return score;
	}

	public void updateLambda(double rate, GpSibAutomaton gpSibAuto, boolean[] z) {
		for (int i = 0; i < y.length; ++i) {
			if (z[i] && !y[i]) {
				// encourage i, increase lSib/lHead, +rate
				gpSibAuto.lSib[i] += rate;
				gpSibAuto.lHead[i] += rate;
				gpSibAuto.updated[gpSibAuto.getHeadIndex(i)] = true;
				gpSibAuto.updated[gpSibAuto.getModIndex(i)] = true;
			}
			else if (!z[i] && y[i]) {
				// discourage i, decrease lSib/lHead, -rate
				gpSibAuto.lSib[i] -= rate;
				gpSibAuto.lHead[i] -= rate;
				gpSibAuto.updated[gpSibAuto.getHeadIndex(i)] = true;
				gpSibAuto.updated[gpSibAuto.getModIndex(i)] = true;
			}
		}
	}
}
