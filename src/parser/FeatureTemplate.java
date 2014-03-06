package parser;

import utils.Utils;

public class FeatureTemplate {
	
	public enum Arc {
		
		FEATURE_TEMPLATE_START,
	    
		/*************************************************
		 * Arc feature inspired by MST parser 
		 * ***********************************************/
		
	    // feature posL posIn posR
	    CORE_POS_PC,	    
	    CORE_POS_XPC,

	    
	    // feature posL-1 posL posR posR+1
	    CORE_POS_PT0,
	    CORE_POS_PT1,
	    CORE_POS_PT2,
	    CORE_POS_PT3,
	    CORE_POS_PT4,
    
	    // feature posL posL+1 posR-1 posR
	    CORE_POS_APT0,
	    CORE_POS_APT1,
	    CORE_POS_APT2,
	    CORE_POS_APT3,
	    CORE_POS_APT4,
	    
	    // feature posL-1 posL posR-1 posR
	    // feature posL posL+1 posR posR+1
	    CORE_POS_BPT,
	    CORE_POS_CPT,

	    // feature  unigram (form, lemma, pos, coarse_pos, morphology) 
	    CORE_HEAD_WORD,
	    CORE_HEAD_POS,
	    CORE_MOD_WORD,
	    CORE_MOD_POS,
	    CORE_HEAD_pWORD,
	    CORE_HEAD_nWORD,
	    CORE_MOD_pWORD,
	    CORE_MOD_nWORD,    
	    
	    // feature  bigram word-cross-word(-cross-distance)
	    CORE_BIGRAM_A,
	    CORE_BIGRAM_B,
	    CORE_BIGRAM_C,
	    CORE_BIGRAM_D,
	    CORE_BIGRAM_E,
	    CORE_BIGRAM_F,
	    CORE_BIGRAM_G,
	    CORE_BIGRAM_H,
	    CORE_BIGRAM_K,
	    
	    // feature  label feature
	    CORE_LABEL_NTS1,
	    CORE_LABEL_NTH,
	    CORE_LABEL_NTI,
	    CORE_LABEL_NTIA,
	    CORE_LABEL_NTIB,
	    CORE_LABEL_NTIC,
	    CORE_LABEL_NTJ,

	    
		/*************************************************
		 * word embedding feature  
		 * ***********************************************/
	    
	    CORE_HEAD_EMB,
	    CORE_MOD_EMB,
	    
	    
	    FEATURE_TEMPLATE_END;
		
		static int numArcFeatBits = Utils.log2(FEATURE_TEMPLATE_END.ordinal());
	}

	public enum Word {

		FEATURE_TEMPLATE_START,
		
		/*************************************************
		 * Word features for matrix/tensor 
		 * ***********************************************/
		
		WORDFV_BIAS,
		
	    WORDFV_W0,
	    WORDFV_Wp,
	    WORDFV_Wn,
	    WORDFV_W0P0,
	    
	    WORDFV_F,
	    WORDFV_L0F,
	    WORDFV_P0F,
	    
	    WORDFV_P0,
	    WORDFV_Pp,
	    WORDFV_Pn,
	    WORDFV_PpP0,
	    WORDFV_P0Pn,
	    WORDFV_PpP0Pn,
	    
	    WORDFV_EMB,
	    
	    FEATURE_TEMPLATE_END;
	    
		static int numWordFeatBits = Utils.log2(FEATURE_TEMPLATE_END.ordinal());
	}
}


