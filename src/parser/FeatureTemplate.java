package parser;

import utils.Utils;

public class FeatureTemplate {
	
	/**
	 * "H"	: head
	 * "M"	: modifier
	 * "B"	: in-between tokens
	 * 
	 * "P"	: pos tag
	 * "W"	: word form or lemma
	 * "EMB": word embedding (word vector)
	 * 
	 * "p": previous token
	 * "n": next token
	 *
	 */
	
	public enum Arc {
		
		FEATURE_TEMPLATE_START,
	    
		/*************************************************
		 * Arc feature inspired by MST parser 
		 * ***********************************************/
		
	    // posL posIn posR
	    HP_BP_MP,			//CORE_POS_PC,	    
	    					//CORE_POS_XPC,
	    
	    // posL-1 posL posR posR+1
	    HPp_HP_MP_MPn,		//CORE_POS_PT0,
	    HP_MP_MPn,			//CORE_POS_PT1,
	    HPp_HP_MP,			//CORE_POS_PT2,
	    HPp_MP_MPn,			//CORE_POS_PT3,
	    HPp_HP_MPn,			//CORE_POS_PT4,
    
	    // posL posL+1 posR-1 posR
	    HP_HPn_MPp_MP,		//CORE_POS_APT0,
	    HP_MPp_MP,			//CORE_POS_APT1,
	    HP_HPn_MP,			//CORE_POS_APT2,
	    HPn_MPp_MP,			//CORE_POS_APT3,
	    HP_HPn_MPp,			//CORE_POS_APT4,
	    
	    // posL-1 posL posR-1 posR
	    // posL posL+1 posR posR+1
	    HPp_HP_MPp_MP,		//CORE_POS_BPT,
	    HP_HPn_MP_MPn,		//CORE_POS_CPT,

	    // unigram (form, lemma, pos, coarse_pos, morphology) 
	    CORE_HEAD_WORD,
	    CORE_HEAD_POS,
	    CORE_MOD_WORD,
	    CORE_MOD_POS,
	    CORE_HEAD_pWORD,
	    CORE_HEAD_nWORD,
	    CORE_MOD_pWORD,
	    CORE_MOD_nWORD,    
	    
	    // bigram  [word|lemma]-cross-[pos|cpos|mophlogy](-cross-distance)
	    HW_MW_HP_MP,			//CORE_BIGRAM_A,
	    MW_HP_MP,				//CORE_BIGRAM_B,
	    HW_HP_MP,				//CORE_BIGRAM_C,
	    MW_HP,					//CORE_BIGRAM_D,
	    HW_MP,					//CORE_BIGRAM_E,
	    HW_HP,					//CORE_BIGRAM_F,
	    MW_MP,					//CORE_BIGRAM_G,
	    HW_MW,					//CORE_BIGRAM_H,
	    HP_MP,					//CORE_BIGRAM_K,
	    
	    // label feature
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
	    
	    HEAD_EMB,
	    MOD_EMB,
	    
	    
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


