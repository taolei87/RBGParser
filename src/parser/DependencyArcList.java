package parser;

import utils.Utils;

public class DependencyArcList {
	public int n;
	public int[] st, edges;
	public int[] left, right;		// span
	public int[] nonproj;			// non-proj
	
	public DependencyArcList(int n, boolean useHO)
	{
		this.n = n;
		st = new int[n];
		edges = new int[n];
		if (useHO) {
			left = new int[n];
			right = new int[n];
			//nonproj = new int[n];
		}
	}
	
	public DependencyArcList(int[] heads, boolean useHO)
	{
		n = heads.length;
		st = new int[n];
		edges = new int[n];
		constructDepTreeArcList(heads);
		if (useHO) {
			left = new int[n];
			right = new int[n];
			//nonproj = new int[n];
			constructSpan();
			//constructNonproj(heads);
		}
	}
	
	public void resize(int n, boolean useHO)
	{
		if (n > st.length) {
			st = new int[n];
			edges = new int[n];
			if (useHO) {
				left = new int[n];
				right = new int[n];
				//nonproj = new int[n];
			}
		}
		this.n = n;
		
		edges[n - 1] = 0;
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
	
	public void update(int m, int oldH, int newH, int[] heads) {
		//System.out.println("m: " + m + " oldH: " + oldH + " newH: " + newH);
		updateDepTreeArcList(m, oldH, newH);
		if (left != null && right != null)
			updateDepSpan(m, oldH, newH, heads);
		if (nonproj != null)
			constructNonproj(heads);
	}
	
	public void updateDepTreeArcList(int m, int oldH, int newH) {
		if (oldH == newH)
			return;
		// update the head of m from oldH to newH
		if (oldH < newH) {
			int end = endIndex(oldH);
			int pos = startIndex(oldH);
			for (; pos < end; ++pos)
				if (edges[pos] == m)
					break;
			//Utils.Assert(pos < end);
			// update oldH
			for (; pos < end - 1; ++pos)
				edges[pos] = edges[pos + 1];
			// update oldH + 1 to newH - 1
			for (int i = oldH + 1; i < newH; ++i) {
				--st[i];
				end = endIndex(i);
				for (; pos < end - 1; ++pos)
					edges[pos] = edges[pos + 1];
			}
			// update newH
			st[newH]--;
			end = endIndex(newH);
			while (pos < end - 1 && edges[pos + 1] < m) {
				edges[pos] = edges[pos + 1];
				++pos;
			}
			edges[pos] = m;
		}
		else {
			int start = startIndex(oldH);
			int pos = endIndex(oldH) - 1;
			for (; pos >= start; --pos)
				if (edges[pos] == m)
					break;
			//Utils.Assert(pos >= start);
			// update oldH
			for (; pos > start; --pos)
				edges[pos] = edges[pos - 1];
			++st[oldH];
			// update oldH - 1 to newH + 1
			for (int i = oldH - 1; i > newH; --i) {
				start = startIndex(i);
				for (; pos > start; --pos)
					edges[pos] = edges[pos - 1];
				++st[i];
			}
			// update newH
			start = startIndex(newH);
			while (pos > start && edges[pos - 1] > m) {
				edges[pos] = edges[pos - 1];
				--pos;
			}
			edges[pos] = m;
		}
	}
	
	public void updateDepSpan(int m, int oldH, int newH, int[] heads) {
		if (oldH == newH)
			return;
		
		int tmpH = newH;
		while (tmpH != -1) {
			left[tmpH] = Math.min(left[tmpH], left[m]);
			right[tmpH] = Math.max(right[tmpH], right[m]);
			tmpH = heads[tmpH];
		}

		// assume that child list is updated
		tmpH = oldH;
		while (tmpH != -1) {
			if (left[tmpH] == left[m]) {
				left[tmpH] = tmpH;
				int start = startIndex(tmpH);
				int end = endIndex(tmpH);
				for (int i = start; i < end; ++i)
					left[tmpH] = Math.min(left[tmpH], left[edges[i]]);
			}
			if (right[tmpH] == right[m]) {
				right[tmpH] = tmpH + 1;
				int start = startIndex(tmpH);
				int end = endIndex(tmpH);
				for (int i = start; i < end; ++i)
					right[tmpH] = Math.max(right[tmpH], right[edges[i]]);
			}
			tmpH = heads[tmpH];
		}
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
