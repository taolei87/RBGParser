package utils;

import gnu.trove.iterator.TLongIntIterator;
import gnu.trove.map.hash.TLongIntHashMap;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;


public class Alphabet implements Serializable
{
	TLongIntHashMap map;
    int numEntries;
    boolean growthStopped = false;

    public Alphabet (int capacity)
    {
    	this.map = new TLongIntHashMap(capacity);
		numEntries = 0;
    }
    
    public Alphabet ()
    {
    	this (10000);
    }
    
    public Alphabet(Alphabet a)
    {
    	numEntries = a.numEntries;
    	map = new TLongIntHashMap(numEntries);    	
    	for (TLongIntIterator iter = a.map.iterator(); iter.hasNext();) {
    		iter.advance();
    		map.put(iter.key(), iter.value());
    	}
    }

    /** Return -1 if entry isn't present. */
    public int lookupIndex (long entry, int value)
    {
		int ret = map.get(entry);
		if (ret <= 0 && !growthStopped) {
			numEntries++;
			ret = value + 1;
		    map.put (entry, ret);
		}
		return ret - 1;	// feature id should be 0-based
    }
    
    /** Return -1 if entry isn't present. */
    public int lookupIndex (long entry, boolean addIfNotPresent)
    {
		int ret = map.get(entry);
		if (ret <= 0 && !growthStopped && addIfNotPresent) {
			numEntries++;
			ret = numEntries;
		    map.put (entry, ret);
		}
		return ret - 1;	// feature id should be 0-based
    }
    
    public int lookupIndex (long entry)
    {
    	return lookupIndex (entry, true);
    }
	
    public boolean contains (long entry)
    {
    	return map.contains (entry);
    }

    public int size ()
    {
    	return numEntries;
    }

    public void stopGrowth ()
    {
    	growthStopped = true;
    }

    public void allowGrowth ()
    {
    	growthStopped = false;
    }

    public boolean growthStopped ()
    {
    	return growthStopped;
    }
    
    public long[] toArray () {
    	return map.keys();
    }

    // Serialization 
		
    private static final long serialVersionUID = 1;
    private static final int CURRENT_SERIAL_VERSION = 0;

    private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
		out.writeInt (numEntries);
		out.writeObject(map);
		out.writeBoolean (growthStopped);
	}
	
    private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt ();
		numEntries = in.readInt();
		map = (TLongIntHashMap)in.readObject();
		growthStopped = in.readBoolean();
    }
}
	