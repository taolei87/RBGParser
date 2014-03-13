package parser.io;

import java.io.IOException;
import java.util.ArrayList;

import parser.DependencyInstance;
import parser.Options;


public class CONLLReader extends DependencyReader {

	public CONLLReader(Options options) {
		this.options = options;
	}
	
	@Override
	public DependencyInstance nextInstance() throws IOException {
		
	    ArrayList<String[]> lstLines = new ArrayList<String[]>();

	    String line = reader.readLine();
	    while (line != null && !line.equals("") && !line.startsWith("*")) {
	    	lstLines.add(line.split("\t"));
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
	    cpos[0] = "<root-CPOS>";
	    pos[0] = "<root-POS>";
	    deprels[0] = "<no-type>";
	    heads[0] = -1;
	    
	    boolean hasLemma = false;
	    
	    // 3 eles ele pron pron-pers M|3P|NOM 4 SUBJ _ _
	    // ID FORM LEMMA COURSE-POS FINE-POS FEATURES HEAD DEPREL PHEAD PDEPREL
	    for (int i = 1; i < length + 1; ++i) {
	    	String[] parts = lstLines.get(i-1);
	    	forms[i] = normalize(parts[1]);
	    	if (!parts[2].equals("_")) { 
	    		lemmas[i] = normalize(parts[2]);
	    		hasLemma = true;
	    	} //else lemmas[i] = forms[i];
	    	cpos[i] = parts[3];
	    	pos[i] = parts[4];
	    	if (!parts[5].equals("_")) feats[i] = parts[5].split("\\|");
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
