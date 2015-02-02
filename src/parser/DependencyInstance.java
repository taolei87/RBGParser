package parser;

import static utils.DictionarySet.DictionaryTypes.DEPLABEL;
import static utils.DictionarySet.DictionaryTypes.POS;
import static utils.DictionarySet.DictionaryTypes.WORD;
import static utils.DictionarySet.DictionaryTypes.WORDVEC;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.io.*;

import parser.Options.PossibleLang;
import utils.Alphabet;
import utils.Dictionary;
import utils.DictionarySet;

public class DependencyInstance implements Serializable {
	
	public enum SpecialPos {
		C, P, PNX, V, AJ, N, OTHER,
	}

	private static final long serialVersionUID = 1L;
	
	public int length;

	// FORM: the forms - usually words, like "thought"
	public String[] forms;

	// LEMMA: the lemmas, or stems, e.g. "think"
	public String[] lemmas;
	
	// COARSE-POS: the coarse part-of-speech tags, e.g."V"
	public String[] cpostags;

	// FINE-POS: the fine-grained part-of-speech tags, e.g."VBD"
	public String[] postags;
	
	// MOST-COARSE-POS: the coarsest part-of-speech tags (about 11 in total)
	public SpecialPos[] specialPos;
	
	// FEATURES: some features associated with the elements separated by "|", e.g. "PAST|3P"
	public String[][] feats;

	// HEAD: the IDs of the heads for each element
	public int[] heads;

	// DEPREL: the dependency relations, e.g. "SUBJ"
	public String[] deprels;
	
	public int[] formids;
	public int[] lemmaids;
	public int[] postagids;
	public int[] cpostagids;
	public int[] deprelids;
	public int[][] featids;
	public int[] wordVecIds;

	public int[] deplbids;

    public DependencyInstance() {}
    
    public DependencyInstance(int length) { this.length = length; }
    
    public DependencyInstance(String[] forms) {
    	length = forms.length;
    	this.forms = forms;
    	this.feats = new String[length][];
    	this.deprels = new String[length];
    }
    
    public DependencyInstance(String[] forms, String[] postags, int[] heads) {
    	this.length = forms.length;
    	this.forms = forms;    	
    	this.heads = heads;
	    this.postags = postags;
    }
    
    public DependencyInstance(String[] forms, String[] postags, int[] heads, String[] deprels) {
    	this(forms, postags, heads);
    	this.deprels = deprels;    	
    }

    public DependencyInstance(String[] forms, String[] lemmas, String[] cpostags, String[] postags,
            String[][] feats, int[] heads, String[] deprels) {
    	this(forms, postags, heads, deprels);
    	this.lemmas = lemmas;    	
    	this.feats = feats;
    	this.cpostags = cpostags;
    }
    
    public DependencyInstance(DependencyInstance a) {
    	//this(a.forms, a.lemmas, a.cpostags, a.postags, a.feats, a.heads, a.deprels);
    	specialPos = a.specialPos;
    	length = a.length;
    	heads = a.heads;
    	formids = a.formids;
    	lemmaids = a.lemmaids;
    	postagids = a.postagids;
    	cpostagids = a.cpostagids;
    	deprelids = a.deprelids;
    	deplbids = a.deplbids;
    	featids = a.featids;
    	wordVecIds = a.wordVecIds;
    }
    
    //public void setDepIds(int[] depids) {
    //	this.depids = depids;
    //}
    
    
    public void setInstIds(DictionarySet dicts, 
    		HashMap<String, String> coarseMap, HashSet<String> conjWord, PossibleLang lang) {
    	    	
    	formids = new int[length];    	
		deplbids = new int[length];
		postagids = new int[length];
		cpostagids = new int[length];
		
    	for (int i = 0; i < length; ++i) {
    		formids[i] = dicts.lookupIndex(WORD, "form="+normalize(forms[i]));
			postagids[i] = dicts.lookupIndex(POS, "pos="+postags[i]);
			cpostagids[i] = dicts.lookupIndex(POS, "cpos="+cpostags[i]);
			deplbids[i] = dicts.lookupIndex(DEPLABEL, deprels[i]) - 1;	// zero-based
    	}
    	
    	if (lemmas != null) {
    		lemmaids = new int[length];
    		for (int i = 0; i < length; ++i)
    			lemmaids[i] = dicts.lookupIndex(WORD, "lemma="+normalize(lemmas[i]));
    	}

		featids = new int[length][];
		for (int i = 0; i < length; ++i) if (feats[i] != null) {
			featids[i] = new int[feats[i].length];
			for (int j = 0; j < feats[i].length; ++j)
				featids[i][j] = dicts.lookupIndex(POS, "feat="+feats[i][j]);
		}
		
		if (dicts.size(WORDVEC) > 0) {
			wordVecIds = new int[length];
			for (int i = 0; i < length; ++i) {
				int wvid = dicts.lookupIndex(WORDVEC, forms[i]);
				if (wvid <= 0) wvid = dicts.lookupIndex(WORDVEC, forms[i].toLowerCase());
				if (wvid > 0) wordVecIds[i] = wvid; else wordVecIds[i] = -1; 
			}
		}
		
		// set special pos
		specialPos = new SpecialPos[length];
		for (int i = 0; i < length; ++i) {
			if (coarseMap.containsKey(postags[i])) {
				String cpos = coarseMap.get(postags[i]);
				if ((cpos.equals("CONJ")
						|| PossibleLang.Japanese == lang) && conjWord.contains(forms[i])) {
					specialPos[i] = SpecialPos.C;
				}
				else if (cpos.equals("ADP"))
					specialPos[i] = SpecialPos.P;
				else if (cpos.equals("."))
					specialPos[i] = SpecialPos.PNX;
				else if (cpos.equals("VERB"))
					specialPos[i] = SpecialPos.V;
				else
					specialPos[i] = SpecialPos.OTHER;
			}
			else {
				//System.out.println("Can't find coarse map: " + postags[i]);
				coarseMap.put(postags[i], "X");
			}
		}
    }

	
    private String normalize(String s) {
		if(s!=null && s.matches("[0-9]+|[0-9]+\\.[0-9]+|[0-9]+[0-9,]+"))
		    return "<num>";
		return s;
    }
}
