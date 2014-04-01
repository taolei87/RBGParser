package parser;

import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TIntArrayList;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Random;

import utils.SVD;
import utils.Utils;

public class LowRankParam implements Comparator<Integer> {
	

	public int N, M, D;
	public TIntArrayList xlis, ylis, zlis;
	public TDoubleArrayList values;
	
	public Random rnd = new Random(System.currentTimeMillis());
	
	public LowRankParam(Parameters parameters) {
		N = parameters.N;
		M = parameters.M;
		D = parameters.D;
		xlis = new TIntArrayList();
		ylis = new TIntArrayList();
		zlis = new TIntArrayList();
		values = new TDoubleArrayList();
	}
	
	public void putEntry(int x, int y , int z, double value) {
		Utils.Assert(x >= 0 && x < N);
		Utils.Assert(y >= 0 && y < M);
		xlis.add(x);
		ylis.add(y);
		zlis.add(z);
		values.add(value);
	}
	
	public void decompose(int mode, Parameters params) {
		
		int nRows = 0, nCols = 0;
		int maxRank = params.U.length;
		nRows = N;
		nCols = M * D;
		
		int K = xlis.size();		
		Integer[] lis = new Integer[K];
		for (int i = 0; i < K; ++i) lis[i] = i;
		Arrays.sort(lis, this);
		
		int[] x = new int[K], y = new int[K];
		double[] z = new double[K];
		for (int i = 0; i < K; ++i) {
			int j = lis[i];
			x[i] = xlis.get(j);
			y[i] = ylis.get(j)*D + zlis.get(j);
			z[i] = values.get(j);
		}
		
		double ratio = (K+0.0)/nRows/nCols;
		System.out.printf("  Unfolding matrix: %d / (%d*%d)  %.2f%% entries.%n",
				K, nRows, nCols, ratio*100);
		
		double[] S = new double[maxRank];
		double[] Ut = new double[maxRank*nRows];
		double[] Vt = new double[maxRank*nCols];
		int rank = SVD.svd(nRows, nCols, maxRank, x, y, z, S, Ut, Vt);
		System.out.printf("  Rank: %d (max:%d)  Sigma: max=%f cut=%f%n",
				rank, maxRank, S[0], S[rank-1]);
		
		for (int i = 0; i < rank; ++i) {
			params.U[i] = new double[N];
			
//			double invSqrtU = 1.0/Math.sqrt(N) * 0.01;
//			double invSqrtV = 1.0/Math.sqrt(M) * 0.01;
//			double invSqrtW = 1.0/Math.sqrt(D) * 0.01;
			double invSqrtU = 0;
			double invSqrtV = 0;
			double invSqrtW = 0;
			
			for (int j = 0; j < N; ++j)
				params.U[i][j] = (Ut[i*N+j] + rnd.nextGaussian() * invSqrtU);//*1.527525;
			//Utils.normalize(params.U[i]);
			
			double[] A2 = new double[nCols];
			for (int j = 0; j < nCols; ++j)
				A2[j] = Vt[i*nCols+j];
			Utils.Assert(nCols == M * D);
			
			double[] S2 = new double[1];
			double[] Ut2 = new double[M];
			double[] Vt2 = new double[D];
			int rank2 = SVD.svd(A2, M, D, S2, Ut2, Vt2);
			Utils.Assert(rank2 == 1);
			
			//params.V[i] = Ut2;
			//params.W[i] = Vt2;
			for (int j = 0; j < M; ++j)
				params.V[i][j] = (Ut2[j] + invSqrtV * rnd.nextGaussian());//*1.527525;
			//Utils.normalize(params.V[i]);
			
			for (int j = 0; j < D; ++j)
				params.W[i][j] = (Vt2[j] + invSqrtW * rnd.nextGaussian());//*1.527525;
			//Utils.normalize(params.W[i]);
		    
            double coeff = Math.pow(S[i]*S2[0], 1.0/3);
            for (int j = 0; j < N; ++j)
                params.U[i][j] *= coeff;
            for (int j = 0; j < M; ++j)
                params.V[i][j] *= coeff;
			for (int j = 0; j < D; ++j)
				params.W[i][j] *= coeff;//S[i] * S2[0];
		}
		
		for (int i = 0; i < maxRank; ++i) {
			params.totalU[i] = params.U[i].clone();
			params.totalV[i] = params.V[i].clone();
			params.totalW[i] = params.W[i].clone();
		}
	}

	@Override
	public int compare(Integer u, Integer v) {
		int yu = ylis.get(u)*D + zlis.get(u);
		int yv = ylis.get(v)*D + zlis.get(v);
		if (yu != yv)
			return yu - yv;
		else
			return zlis.get(u) - zlis.get(v);
	}
}
