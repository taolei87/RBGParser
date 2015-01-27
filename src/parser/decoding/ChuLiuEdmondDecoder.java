package parser.decoding;

import parser.DependencyInstance;
import parser.GlobalFeatureData;
import parser.LocalFeatureData;
import parser.Options;

public class ChuLiuEdmondDecoder extends DependencyDecoder {
	
	final int labelLossType;
	
	public ChuLiuEdmondDecoder(Options options)
	{
		this.options = options;
		this.labelLossType = options.labelLossType;
	}
	
	private static boolean print = false;
	
	@Override
	public DependencyInstance decode(DependencyInstance inst,
			LocalFeatureData lfd, GlobalFeatureData gfd, boolean addLoss) 
	{
		int N = inst.length;
        int M = N << 1;
        
        int[] deps = inst.heads;
        int[] labs = inst.deplbids;
        //int[][] staticTypes = null;
		//if (options.learnLabel)
		//    staticTypes = lfd.getStaticTypes();
        
        double[][] scores = new double[M][M];
        int[][] oldI = new int[M][M];
        int[][] oldO = new int[M][M];
        for (int i = 0; i < N; ++i)
            for (int j = 1; j < N; ++j) 
                if (i != j) {
                    oldI[i][j] = i;
                    oldO[i][j] = j;
                    double va = lfd.getArcScore(i,j);
                    //if (options.learnLabel) {
                    //    int t = staticTypes[i][j];
                    //    va += lfd.getLabeledArcScore(i,j,t);
                    //    if (addLoss) {
                    //    	if (labelLossType == 0) {
                    //    		if (labs[j] != t) va += 0.5;
                    //    		if (deps[j] != i) va += 0.5;
                    //    	} else if (labs[j] != t || deps[j] != i) va += 1.0;
                    //    }                                            
                    //} 
                    //else 
                     if (addLoss && deps[j] != i) va += 1.0;                    
                    scores[i][j] = va;
                }

        boolean[] ok = new boolean[M];
        boolean[] vis = new boolean[M];
        boolean[] stack = new boolean[M];
        for (int i = 0; i < M; ++i) ok[i] = true;

        int[] final_par = new int[M];
        for (int i = 0; i < M; ++i) final_par[i] = -1;

        chuLiuEdmond(N, scores, ok, vis, stack, oldI, oldO, final_par);
        if (print) System.out.println();
        
		DependencyInstance predInst = new DependencyInstance(inst);
		predInst.heads = new int[N];
		predInst.deplbids = new int[N];
		
        for (int i = 1; i < N; ++i) {
            int j = final_par[i];
            //int t = options.learnLabel ? staticTypes[j][i] : 0;
            predInst.heads[i] = j;
            //predInst.deplbids[i] = t;
        }
        //if (options.learnLabel)
        //	lfd.predictLabels(predInst.heads, predInst.deplbids, addLoss);
        
        return predInst;
	}
	
	public void chuLiuEdmond(int N, double[][] scores, boolean[] ok, boolean[] vis,
            boolean[] stack, int[][] oldI, int[][] oldO, int[] final_par) {

        // find best graph
        int[] par = new int[N];
        par[0] = -1;
        for (int i = 0; i < N; ++i) par[i] = -1;
        for (int i = 1; i < N; ++i) if (ok[i]) {
            par[i] = 0;
            double max = scores[0][i];
            for (int j = 1; j < N; ++j) 
                if (i != j && ok[j] && max < scores[j][i]) {
                    par[i] = j;
                    max = scores[j][i]; 
                }
        }

        // find the longest circle
        int maxLen = 0;
        int start = -1;
        for (int i = 0; i < N; ++i) vis[i] = false;
        for (int i = 0; i < N; ++i) stack[i] = false;
        for (int i = 0; i < N; ++i) {
            // if this is not a valid node or
            // it is already visited
            if (vis[i] || !ok[i]) continue;
            int j = i;
            while (j != -1 && !vis[j]) {
                vis[j] = true;
                stack[j] = true;
                j = par[j];
            }

            if (j != -1 && stack[j]) {
                // find a circle j --> ... --> j
                int size = 1, k = par[j];
                while (k != j) {
                    k = par[k];
                    ++size;
                }
                // keep the longest circle
                if (size > maxLen) {
                    maxLen = size;
                    start = j;
                }
            }

            // clear stack
            j = i;
            while (j != -1 && stack[j]) {
                stack[j] = false;
                j = par[j];
            }
        }
        
        // if there's no circle, return the result tree
        if (maxLen == 0) {
            for (int i = 0; i < N; ++i) final_par[i] = par[i];
            if (print) {
                System.out.printf("Tree: ");
                for (int i = 0; i < N; ++i) if (final_par[i] != -1)
                    System.out.printf("%d-->%d ", final_par[i], i);
                System.out.println();
            }
            return;
        }
        
        if (print) {
            System.out.printf("Circle: ");
            for (int i = start; ;) {
                System.out.printf("%d<--", i);
                i = par[i];
                if (i == start) break;
            }
            System.out.println(start);
        }

        // otherwise, contract the circle 
        // and add a virtual node v_N
         
        // get circle cost and mark all nodes on the circle
        double circleCost = scores[par[start]][start];
        stack[start] = true;
        ok[start] = false;
        for (int i = par[start]; i != start; i = par[i]) {
            stack[i] = true;
            ok[i] = false;
            circleCost += scores[par[i]][i];
        }
        
        for (int i = 0; i < N; ++i) {
            if (stack[i] || !ok[i]) continue;
            
            double maxToCircle = Double.NEGATIVE_INFINITY;
            double maxFromCircle = Double.NEGATIVE_INFINITY;
            int toCircle = -1;
            int fromCircle = -1;

            for (int j = start; ;) {
                if (scores[j][i] > maxFromCircle) {
                    maxFromCircle = scores[j][i];
                    fromCircle = j;
                }
                double newScore = circleCost + scores[i][j] - scores[par[j]][j];
                if (newScore > maxToCircle) {
                    maxToCircle = newScore;
                    toCircle = j;
                }
                j = par[j];
                if (j == start) break;
            }

            scores[N][i] = maxFromCircle;
            oldI[N][i] = fromCircle;;
            oldO[N][i] = i;
            scores[i][N] = maxToCircle;
            oldI[i][N] = i;
            oldO[i][N] = toCircle;
        }

        chuLiuEdmond(N+1, scores, ok, vis, stack, oldI, oldO, final_par);

        // construct tree from contracted one
        for (int i = 0; i < N; ++i) 
            if (final_par[i] == N) final_par[i] = oldI[N][i];
        final_par[oldO[final_par[N]][N]] = final_par[N];
        for (int i = start; ;) {
            int j = par[i];
            // j --> i
            if (final_par[i] == -1) final_par[i] = j;
            i = j;
            if (i == start) break;
        }

        if (print) {
            System.out.printf("Tree: ");
            for (int i = 0; i < N; ++i) if (final_par[i] != -1)
                System.out.printf("%d-->%d ", final_par[i], i);
            System.out.println();
        }

    }
}
