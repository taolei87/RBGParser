package utils;

import gnu.trove.iterator.TIntIntIterator;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.hash.TIntIntHashMap;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Comparator;

public class DictionarySet implements Serializable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private static final String unseen = "#OTHERS#";
    private static final String token_start = "#TOKEN_START#"; 
    private static final String token_end = "#TOKEN_END#";
    private static final String token_mid = "#TOKEN_MID#";


	public enum DictionaryTypes 
	{
		POS,
		WORD,
		DEPLABEL,
		WORDVEC,
		//AUGLABEL,
		
		TYPE_END;
	}
	
	int tot;
	Dictionary[] dicts;
	
	boolean isCounting;
	TIntIntMap[] counters;
	
	
	public DictionarySet() 
	{	
		isCounting = false;
		dicts = new Dictionary[DictionaryTypes.TYPE_END.ordinal()];
		tot = dicts.length;
		for (int i = 0; i < tot; ++i) {
			dicts[i] = new Dictionary();
			int id = dicts[i].lookupIndex(unseen);	// id=1 means unseen item (pos,word,etc.)
			Utils.Assert(id == 1);
            if (i == DictionaryTypes.POS.ordinal())
                initDict(DictionaryTypes.POS, dicts[i]);
            if (i == DictionaryTypes.WORD.ordinal())
                initDict(DictionaryTypes.WORD, dicts[i]);
		}
	}
    
    private void initDict(DictionaryTypes tag, Dictionary dict)
    {
        int id = dict.lookupIndex(unseen);
        Utils.Assert(id == 1);

        // add special tokens for POS tags and words
        if (tag == DictionaryTypes.POS || tag == DictionaryTypes.WORD) {
            id = dict.lookupIndex(token_start);
            Utils.Assert(id == 2);
            id = dict.lookupIndex(token_end);
            Utils.Assert(id == 3);
            id = dict.lookupIndex(token_mid);
            Utils.Assert(id == 4);
        }
    }

	public int lookupIndex(DictionaryTypes tag, String item) 
	{
		int id = dicts[tag.ordinal()].lookupIndex(item);
		
		if (isCounting && id > 0) {
			counters[tag.ordinal()].putIfAbsent(id, 0);
			counters[tag.ordinal()].increment(id);
		}
		
		return id <= 0 ? 1 : id;
	}
	
	public int size(DictionaryTypes tag)
	{
		return dicts[tag.ordinal()].size();
	}
	
	public void stopGrowth(DictionaryTypes tag)
	{
		dicts[tag.ordinal()].stopGrowth();
	}
	
	public Dictionary get(DictionaryTypes tag)
	{
		return dicts[tag.ordinal()];
	}
	
	public void setCounters()
	{
		isCounting = true;
		counters = new TIntIntHashMap[tot];
		for (int i = 0; i < tot; ++i)
			counters[i] = new TIntIntHashMap();
	}
	
	public void closeCounters()
	{
		isCounting = false;
		counters = null;
	}
	
	public void filterDictionary(DictionaryTypes tag)
	{
		filterDictionary(tag, 0.999f);
	}
	
	public void filterWord() {
		int t = DictionaryTypes.WORD.ordinal();
		int[] values = counters[t].values();
		Arrays.sort(values);
		int thresh = values[values.length - 100];
		Dictionary filtered = new Dictionary();
        initDict(DictionaryTypes.WORD, filtered);
		for (Object obj : dicts[t].toArray()) {
			int id = dicts[t].lookupIndex(obj);
			int value = counters[t].get(id);
			if (value >= thresh) {
				filtered.lookupIndex((String)obj);
			}
		}
		System.out.println("Filtered WORD" + " (" + dicts[t].size() + "-->"
				+ filtered.size() + ")");
		dicts[t] = filtered;
	}
	
	public void filterDictionary(DictionaryTypes tag, float percent)
	{
		int t = tag.ordinal();
		
		int[] values = counters[t].values();
		int n = values.length;
		
		Arrays.sort(values);
		
		float sum = 0.0f;
		for (int i = 0; i < n; ++i) sum += values[i];
		
		int cut = 0;
		float cur = 0.0f;
		for (int i = n-1; i >= 0; --i) {
			//System.out.println(values[i]);
			cur += values[i];
			if (cur >= sum * percent) {
				cut = values[i];
				break;
			}
		}
	
		Dictionary filtered = new Dictionary();
        initDict(tag, filtered);
		for (Object obj : dicts[t].toArray()) {
			int id = dicts[t].lookupIndex(obj);
			int value = counters[t].get(id);
			if (value > cut) {
				//System.out.println(((String)obj) + " " + value);
				filtered.lookupIndex((String)obj);
			}
		}
		System.out.println("Filtered " + tag + " (" + dicts[t].size() + "-->"
				+ filtered.size() + ")");
		dicts[t] = filtered;
	}

}

