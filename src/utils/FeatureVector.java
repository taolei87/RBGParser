package utils;


import gnu.trove.map.hash.TLongObjectHashMap;

import java.util.HashMap;

public class FeatureVector {
	
	int nRows = 1;
	int size = 0;
	//MatrixEntry element = null;
	
	int capacity;
	int[] x;
	double[] va;
		
	public FeatureVector() { grow(); };
	
	public FeatureVector(int _nRows) {		
		nRows = _nRows;
		initCapacity(10);
	}
	
	public FeatureVector(int _nRows, int _nCols) {
		nRows = _nRows;
		initCapacity(10);
	}
	
	public FeatureVector(int _nRows, int _nCols, int _capacity) {
		nRows = _nRows;
		initCapacity(_capacity);
	}
	
	private void initCapacity(int capacity) {
		this.capacity = capacity;
		x = new int[capacity];
		va = new double[capacity];
	}
	
	private void grow() {
		
		int cap = 5 > capacity ? 10 : capacity * 2;
		
		int[] x2 = new int[cap];
		double[] va2 = new double[cap];
		
		if (capacity > 0) {
			System.arraycopy(x, 0, x2, 0, capacity);
			System.arraycopy(va, 0, va2, 0, capacity);
		}
		
		x = x2;
		va = va2;
		capacity = cap;
	}
	
	public void addEntry(int _x, double _value) {
		if (_value == 0) return;
		
		if (size == capacity) grow();
		x[size] = _x;
		va[size] = _value;
		++size;
	}
		
	public void addEntries(FeatureVector m) {
		addEntries(m, 1.0);
	}
	
	public void addEntries(FeatureVector m, double coeff) {
		
		assert(m != null && m.nRows == nRows);
		if (coeff == 0 || m.size == 0) return;
		
		for (int i = 0; i < m.size; ++i)
			addEntry(m.x[i], m.va[i] * coeff);
	}
	
	public void addEntriesOffset(FeatureVector m, int offset) {
		addEntriesOffset(m, offset, 1.0);
	}
	
	public void addEntriesOffset(FeatureVector m, int offset, double coeff) {
		
		if (coeff == 0 || m.size == 0) return;
		
		for (int i = 0; i < m.size; ++i)
			addEntry(m.x[i] + offset, m.va[i]*coeff);
	}
	
	public void rescale(double coeff) {
		for (int i = 0; i < size; ++i)
			va[i] *= coeff;
	}
	
	public double l2Norm() {
		double sum = 0;
		for (int i = 0; i < size; ++i)
			sum += va[i]*va[i];
		return Math.sqrt(sum);
	}
	
	private static double[] l2Vec;
	public double Squaredl2NormUnsafe() {

		if (l2Vec == null || l2Vec.length < nRows) l2Vec = new double[nRows];
		
		double sum = 0;
		for (int i = 0; i < size; ++i) l2Vec[x[i]] += va[i];
		for (int i = 0; i < size; ++i) {
			sum += l2Vec[x[i]] * l2Vec[x[i]];
			l2Vec[x[i]] = 0;
		}
		return sum;
		
	}
	
    public double min() {
        double m = Double.POSITIVE_INFINITY;
        for (int i = 0; i < size; ++i)
            if (m > va[i]) m = va[i];
        return m;
    }
    
    public double max() {
        double m = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < size; ++i)
            if (m < va[i]) m = va[i];
        return m;
    }

	public int size() {
		return size;
	}
    
    public int nRows() { return nRows; }
    public void setNumRows(int n) { nRows = n; }
    
	public int[] x() {
		int[] xx = new int[size];
		if (size > 0)
			System.arraycopy(x, 0, xx, 0, size);
		return xx;
	}
	
	public double[] z() {
		double[] va2 = new double[size];
		if (size > 0)
			System.arraycopy(va, 0, va2, 0, size);
		return va2;
	}

    public void setValue(int i, double v) { va[i] = v; }
	public int x(int i) { return x[i]; }
	public double value(int i) { return va[i]; }
	
	public boolean aggregate() {
		
		if (size == 0) return false;
		
		boolean aggregated = false;
		
		//HashMap<Long, MatrixEntry> table = new HashMap<Long, MatrixEntry>();
		TLongObjectHashMap<Entry> table = new TLongObjectHashMap<Entry>();
		for (int i = 0; i < size; ++i) {
			int id = x[i];
			Entry item = table.get(id);
			if (item != null) {
				item.value += va[i];
				aggregated = true;
			} else
				table.put(id, new Entry(id, va[i]));
		}
		
		if (!aggregated) return false;
		
		int p = 0;
		for (Entry e : table.valueCollection()) {
			if (e.value != 0) {
				x[p] = e.x;
				va[p] = e.value;
				++p;
			}
		}
		size = p;
		return true;
	}
	
    public double dotProduct(FeatureVector _y) {
        return dotProduct(this, _y);
    }
        
	public double dotProduct(double[] _y) {
		return dotProduct(this, _y);
	}
	
	public double dotProduct(double[] _y, int offset) {
		return dotProduct(this, _y, offset);
	}
	
	private static double[] dpVec;			 //non-sparse vector repr for vector dot product
	public static double dotProduct(FeatureVector _x, FeatureVector _y) {
		
		assert(_x.nRows == _y.nRows);		
		
		if (dpVec == null || dpVec.length < _y.nRows) dpVec = new double[_y.nRows];
		
		for (int i = 0; i < _y.size; ++i)
			dpVec[_y.x[i]] += _y.va[i];
		
		double sum = 0;
		for (int i = 0; i < _x.size; ++i)
			sum += _x.va[i] * dpVec[_x.x[i]];

		for (int i = 0; i < _y.size; ++i)
			dpVec[_y.x[i]] = 0;
		
		return sum;
	}
	
	public static double dotProduct(FeatureVector _x, double[] _y) {
		
		double sum = 0;
		for (int i = 0; i < _x.size; ++i)
			sum += _x.va[i] * _y[_x.x[i]];
		return sum;
	}
	
	public static double dotProduct(FeatureVector _x, double[] _y, int offset) {
		
		double sum = 0;
		for (int i = 0; i < _x.size; ++i)
			sum += _x.va[i] * _y[offset + _x.x[i]];
		return sum;
	}
	
}

class Entry {
	int x;
	double value;
	
	public Entry(int x, double value) 
	{
		this.x = x;
		this.value = value;
	}
}

