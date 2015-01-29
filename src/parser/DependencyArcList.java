package parser;

import utils.Utils;

public class DependencyArcList {
	public int n;
	public int[] st, edges;
	public int[] left, right;		// span
	public int[] nonproj;			// non-proj
	
	public DependencyArcList(int n)
	{
		this.n = n;
		st = new int[n];
		edges = new int[n];
		left = new int[n];
		right = new int[n];
		nonproj = new int[n];
	}
	
	public DependencyArcList(int[] heads)
	{
		n = heads.length;
		st = new int[n];
		edges = new int[n];
		left = new int[n];
		right = new int[n];
		nonproj = new int[n];
		constructDepTreeArcList(heads);
		constructSpan();
		constructNonproj(heads);
	}
	
	public void resize(int n)
	{
		if (n > st.length) {
			st = new int[n];
			edges = new int[n];
			left = new int[n];
			right = new int[n];
			nonproj = new int[n];
		}
		this.n = n;
	}
	
	public int startIndex(int i)
	{
		return st[i];
	}
	
	public int endIndex(int i) 
	{
		return (i >= n-1) ? n-1 : st[i+1];
	}
	
	public int get(int i)
	{
		return edges[i];
	}
	
	public void constructDepTreeArcList(int[] heads) 
	{
		
		for (int i = 0; i < n; ++i)
			st[i] = 0;
		
		for (int i = 1; i < n; ++i) {
			int j = heads[i];
			++st[j];
		}
				
		for (int i = 1; i < n; ++i)
			st[i] += st[i-1];
		
		//Utils.Assert(st[n-1] == n-1);
		
		for (int i = n-1; i > 0; --i) {
			int j = heads[i];
			--st[j];
			edges[st[j]] = i;
		}
		
//		for (int i = 0; i < n; ++i) {
//			int st = startIndex(i);
//			int ed = endIndex(i);
//			if (i > 0)
//				Utils.Assert(startIndex(i) == endIndex(i-1));
//			if (st < ed)
//				Utils.Assert(heads[get(st)] == i);
//			for (int p = st+1; p < ed; ++p) {
//				Utils.Assert(heads[get(p)] == i);
//				Utils.Assert(get(p-1) < get(p));
//			}
//		}
//		
//		// no loop
//		for (int i = 1; i < n; ++i) {
//			Utils.Assert(!isAncestorOf(heads, i, heads[i]));
//		}
	}
	
	private boolean isAncestorOf(int[] heads, int par, int ch) 
	{
        int cnt = 0;
		while (ch != 0) {
			if (ch == par) return true;
			ch = heads[ch];

            //DEBUG
            //++cnt;
            //if (cnt > 10000) {
                //System.out.println("DEAD LOOP in isAncestorOf !!!!");
                //System.exit(1);
            //}
		}
		return false;
	}
	
	private void constructSpan(int id) {
		left[id] = id;
		right[id] = id + 1;

		int st = startIndex(id);
		int ed = endIndex(id);

		for (int p = st; p < ed; ++p) {
			int cid = get(p);
			if (right[cid] == 0)
				constructSpan(cid);
			if (left[cid] < left[id])
				left[id] = left[cid];
			if (right[cid] > right[id])
				right[id] = right[cid];
		}
	}
	
	public void constructSpan() {
		// assume that child list is constructed 
		for (int i = 0; i < n; ++i) {
			left[i] = 0;
			right[i] = 0;
		}
		
		for (int i = 0; i < n; ++i)
			if (right[i] == 0)
				constructSpan(i);
	}
	
	public void constructNonproj(int[] heads) {
		for (int i = 0; i < n; ++i) {
			nonproj[i] = 0;
		}

		for (int m = 0; m < n; ++m) {
			int h = heads[m];
			int sm = m < h ? m : h;
			int la = m > h ? m : h;
			for (int tm = sm + 1; tm < la; ++tm) {
				// head
				int th = heads[tm];
				if (th < sm || th > la) {
					nonproj[m]++;
				}
			}
		}
	}
}
