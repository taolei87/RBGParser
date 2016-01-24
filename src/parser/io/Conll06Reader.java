package parser.io;

import java.io.*;
import java.util.*;

import parser.DependencyInstance;
import parser.Options;
import utils.Utils;


public class Conll06Reader extends DependencyReader {
	
	HashMap<String, Boolean> adverb;
	HashMap<String, Integer> rational;

	public Conll06Reader(Options options) {
		this.options = options;
		adverb = new HashMap<String, Boolean>();
		rational = new HashMap<String, Integer>();
/*		
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream("./data/Adverbs.txt"), "UTF8"));
			String str = null;
			while ((str = br.readLine()) != null) {
				String[] data = str.split("\t");
				adverb.put(data[1], data[3].equals("closed"));
				//System.out.println(data[3]);
			}
			br.close();
			
			br = new BufferedReader(new InputStreamReader(new FileInputStream("./data/PluralRationality.txt"), "UTF8"));
			Transliterate t = new Transliterate(true);		// a->b
			str = br.readLine();
			while ((str = br.readLine()) != null) {
				String[] data = str.split("\t");
				String w = t.apply(data[0]);
				String s = w + "\t" + data[2] + "\t" + data[3];
				int r = data[4].isEmpty() ? 0 : Integer.parseInt(data[4]);
				rational.put(s, r);
			}
			br.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		*/
	}
	
	@Override
	public DependencyInstance nextInstance() throws IOException {
		
	    ArrayList<String[]> lstLines = new ArrayList<String[]>();

	    String line = reader.readLine();
	    while (line != null && !line.equals("") && !line.startsWith("*")) {
	    	if (!line.startsWith("# ")) {
	    		String[] data = line.split("\t");
	    		if (!data[0].contains("-")) {
		    		lstLines.add(data);
	    		}
	    	}
	    	line = reader.readLine();
	    }
	    
	    if (lstLines.size() == 0) return null;
	    
	    int length = lstLines.size();
	    String[] forms = new String[length + 1];
	    String[] lemmas = new String[length + 1];
	    String[] cpos = new String[length + 1];
	    String[] pos = new String[length + 1];
	    String[][] feats = new String[length + 1][];
	    String[] deprels = new String[length + 1];
	    int[] heads = new int[length + 1];
	    
	    forms[0] = "<root>";
	    lemmas[0] = "<root-LEMMA>";
	    lemmas[0] = forms[0];
	    cpos[0] = "<root-CPOS>";
	    pos[0] = "<root-POS>";
	    pos[0] = cpos[0];
	    deprels[0] = "<no-type>";
	    heads[0] = -1;
	    
	    boolean hasLemma = false;
	    
	    // 3 eles ele pron pron-pers M|3P|NOM 4 SUBJ _ _
	    // ID FORM LEMMA COURSE-POS FINE-POS FEATURES HEAD DEPREL PHEAD PDEPREL
	    for (int i = 1; i < length + 1; ++i) {
	    	String[] parts = lstLines.get(i-1);
	    	forms[i] = parts[1];
	    	if (!parts[2].equals("_")) { 
	    		lemmas[i] = parts[2];
	    		hasLemma = true;
	    	} //else lemmas[i] = forms[i];
	    	cpos[i] = parts[3];
	    	//pos[i] = parts[4];
	    	pos[i] = cpos[i];
	    	
	    	// fix adverb
	    	/*
	    	if (adverb.containsKey(forms[i])) {
	    		if (adverb.get(forms[i])) {
	    			// closed
		    		cpos[i] = "RB-C";
		    		pos[i] = "RB-C";
	    		}
	    		else {
	    			cpos[i] = "RB-O";
	    			pos[i] = "RB-O";
	    		}
	    		//System.out.println("fix " + forms[i]);
	    	}
	    	*/
	    	
	    	// handle the case when one type of POS is not given
	    	if (pos[i].equals("_")) 
	    		pos[i] = cpos[i];
	    	else if (cpos[i].equals("_"))
	    		cpos[i] = pos[i];

	    	// add rational
//	    	String s = forms[i] + "\t" + pos[i] + "\t" + parts[5];
//	    	if (rational.containsKey(s)) {
//	    		Utils.Assert(!s.equals("_"));
//	    		parts[5] += "|rat=" + rational.get(s);
//	    		//System.out.println("add: " + parts[5]);
//	    	}
//	    	
//	    	if (!parts[5].equals("_")) feats[i] = parts[5].split("\\|");
	    	
	    	heads[i] = Integer.parseInt(parts[6]);
	    	deprels[i] = (/*options.learnLabel &&*/ isLabeled) ? parts[7] : "<no-type>";
	    }
	    if (!hasLemma) lemmas = null;
	    
		return new DependencyInstance(forms, lemmas, cpos, pos, feats, heads, deprels);
	}

	@Override
	public boolean IsLabeledDependencyFile(String file) throws IOException {
		return true;
	}

}
