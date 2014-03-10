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
	    HW_HP,					//CORE_BIGRAM_H,
	    MW_MP,					//CORE_BIGRAM_K,
	    HW_MW,					//CORE_BIGRAM_F,
	    HP_MP,					//CORE_BIGRAM_G,
	    
	    // label feature
	    CORE_LABEL_NTS1,
	    CORE_LABEL_NTH,
	    CORE_LABEL_NTI,
	    CORE_LABEL_NTIA,
	    CORE_LABEL_NTIB,
	    CORE_LABEL_NTIC,
	    CORE_LABEL_NTJ,

	    
		/*************************************************
		 * 2o feature  
		 * ***********************************************/

	    HP_SP_MP,
		HC_SC_MC,

		pHC_HC_SC_MC,
		HC_nHC_SC_MC,
		HC_pSC_SC_MC,
		HC_SC_nSC_MC,
		HC_SC_pMC_MC,
		HC_SC_MC_nMC,

		pHC_HL_SC_MC,
		HL_nHC_SC_MC,
		HL_pSC_SC_MC,
		HL_SC_nSC_MC,
		HL_SC_pMC_MC,
		HL_SC_MC_nMC,

		pHC_HC_SL_MC,
		HC_nHC_SL_MC,
		HC_pSC_SL_MC,
		HC_SL_nSC_MC,
		HC_SL_pMC_MC,
		HC_SL_MC_nMC,

		pHC_HC_SC_ML,
		HC_nHC_SC_ML,
		HC_pSC_SC_ML,
		HC_SC_nSC_ML,
		HC_SC_pMC_ML,
		HC_SC_ML_nMC,

		HC_MC_SC_pHC_pMC,
		HC_MC_SC_pHC_pSC,
		HC_MC_SC_pMC_pSC,
		HC_MC_SC_nHC_nMC,
		HC_MC_SC_nHC_nSC,
		HC_MC_SC_nMC_nSC,
		HC_MC_SC_pHC_nMC,
		HC_MC_SC_pHC_nSC,
		HC_MC_SC_pMC_nSC,
		HC_MC_SC_nHC_pMC,
		HC_MC_SC_nHC_pSC,
		HC_MC_SC_nMC_pSC,

		SP_MP,
		SW_MW,
		SW_MP,
		SP_MW,
		SC_MC,
		SL_ML,
		SL_MC,
		SC_ML,

		// head bigram
		H1P_H2P_M1P_M2P,
		H1P_H2P_M1P_M2P_DIR,
		H1C_H2C_M1C_M2C,
		H1C_H2C_M1C_M2C_DIR,

		// gp-p-c
		GP_HP_MP,
		GC_HC_MC,
		GL_HC_MC,
		GC_HL_MC,
		GC_HC_ML,

		pGC_GC_HC_MC,
		GC_nGC_HC_MC,
		GC_pHC_HC_MC,
		GC_HC_nHC_MC,
		GC_HC_pMC_MC,
		GC_HC_MC_nMC,

		pGC_GL_HC_MC,
		GL_nGC_HC_MC,
		GL_pHC_HC_MC,
		GL_HC_nHC_MC,
		GL_HC_pMC_MC,
		GL_HC_MC_nMC,

		pGC_GC_HL_MC,
		GC_nGC_HL_MC,
		GC_pHC_HL_MC,
		GC_HL_nHC_MC,
		GC_HL_pMC_MC,
		GC_HL_MC_nMC,

		pGC_GC_HC_ML,
		GC_nGC_HC_ML,
		GC_pHC_HC_ML,
		GC_HC_nHC_ML,
		GC_HC_pMC_ML,
		GC_HC_ML_nMC,

		GC_HC_MC_pGC_pHC,
		GC_HC_MC_pGC_pMC,
		GC_HC_MC_pHC_pMC,
		GC_HC_MC_nGC_nHC,
		GC_HC_MC_nGC_nMC,
		GC_HC_MC_nHC_nMC,
		GC_HC_MC_pGC_nHC,
		GC_HC_MC_pGC_nMC,
		GC_HC_MC_pHC_nMC,
		GC_HC_MC_nGC_pHC,
		GC_HC_MC_nGC_pMC,
		GC_HC_MC_nHC_pMC,

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


