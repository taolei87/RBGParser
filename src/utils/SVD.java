package utils;

public class SVD {

    public static native double powerMethod(int[] x, int[] y, double[] z,
                double[] u, double[] v);
    
    public static native int lowRankSvd(double[] At, double[] Bt,
    	int n, int m, int r, double[] S, double[] Ut, double[] Vt);
    
    public static native int svd(double[] A, int n, int m, 
    		double[] S, double[] Ut, double[] Vt);
    
    public static native int svd(int n, int m, int r, int[] x, int [] y, double[] z,
    		double[] S, double[] Ut, double[] Vt);
    
    static {
        System.loadLibrary("SVDImp");
    }
    
//	public static double epsilon = 1e-8;
//	public static int maxNumIters = 1000;
//	public static Random rnd = new Random(System.currentTimeMillis());
//	
//	public static double runIterations(SparseMatrix M, double[] u, double[] v) {
//        
//        System.out.println("N=" + v.length);
//        System.out.println("M=" + M.size);
//
//		int N = v.length, iter;
//		double sigma = 0, prevSigma = -1;
//		
//		for (int i = 0; i < N; ++i) v[i] = rnd.nextDouble() - 0.5;
//		norm(v);
//				
//		for (iter = 1; iter <= maxNumIters; ++iter) {
//			// u = Mv
//			for (int i = 0; i < N; ++i) u[i] = 0;
//			for (MatrixEntry e = M.element; e != null; e = e.next) 
//				u[e.x] += e.value * v[e.y];
//			sigma = norm(u);
//			
//			if (prevSigma != -1 && Math.abs(sigma - prevSigma) < epsilon) {
//				break;
//			} else if (iter % 10000 == 1) System.out.println("\tIter " + iter + " " + sigma);
//			prevSigma = sigma;
//			
//			// v = M^Tu
//			for (int i = 0; i < N; ++i) v[i] = 0;
//			for (MatrixEntry e = M.element; e != null; e = e.next) 
//				v[e.y] += e.value * u[e.x];
//			norm(v);
//		}
//		System.out.println("\tIter " + iter + " " + sigma);
//		assert(iter <= maxNumIters);
//		return sigma;
//	}
//	
//	private static double norm(double[] x) {
//		double s = 0;		
//		for (int N = x.length, i = 0; i < N; ++i)
//			s += x[i]*x[i];
//		s = Math.sqrt(s);
//		for (int N = x.length, i = 0; i < N; ++i)
//			x[i] /= s;
//		return s;
//	}
}
