package parser;

import java.util.Arrays;

import parser.DependencyInstance.SpecialPos;
import parser.feature.FeatureTemplate.Arc;
import parser.feature.SyntacticFeatureFactory;
import utils.FeatureVector;
import utils.Utils;
import static utils.DictionarySet.DictionaryTypes.*;
import static parser.LocalFeatureData.NULL;

public class GlobalFeatureData {

	public final static int BINNED_BUCKET = 8;
	public final static int MAX_CHILD_NUM = 5;
	public final static int MAX_SPAN_LENGTH = 5;
	public final static int MAX_FEATURE_NUM = 7;

	LocalFeatureData lfd;
	DependencyPipe pipe;
	SyntacticFeatureFactory synFactory;

	//FeatureDataItem[] cn;	// [len][leftNum][rightNum]
	double[] cn;		// [len][leftNum][rightNum]

	//FeatureDataItem[] span;	// [len][end][punc][bin]
	double[] span;	// [len][end][punc][bin]

	//FeatureDataItem[] nb;		// [maxid][cpos][cpos]
	double[] nb;		// [maxid][cpos][cpos]

	//FeatureDataItem[] ppcc1;	// pp attachment, punc head and part of conjunction
	double[] ppcc1;	// pp attachment, punc head and part of conjunction

	//FeatureDataItem[] cc2;		// [arg][head][left/right]
	double[] cc2;		// [arg][head][left/right]

	//FeatureDataItem[] nonproj;	// nonproj arc, [dep id][nonproj binned num]
	double[] nonproj;	// nonproj arc, [dep id][nonproj binned num]

	public GlobalFeatureData(LocalFeatureData lfd) 
	{
		this.lfd = lfd;
		pipe = lfd.pipe;
		synFactory = pipe.synFactory;
		
		// init array
		if (lfd.options.useHO) {
			//cn = new FeatureDataItem[lfd.len * (MAX_CHILD_NUM + 1) * (MAX_CHILD_NUM + 1)];
			cn = new double[lfd.len * (MAX_CHILD_NUM + 1) * (MAX_CHILD_NUM + 1)];
			Arrays.fill(cn, NULL);
			
			//span = new FeatureDataItem[lfd.len * 2 * 2 * (MAX_SPAN_LENGTH + 1)];
			span = new double[lfd.len * 2 * 2 * (MAX_SPAN_LENGTH + 1)];
			Arrays.fill(span, NULL);

			//nb = new FeatureDataItem[lfd.nuparcs * pipe.dictionaries.size(POS) * pipe.dictionaries.size(POS)];
			nb = new double[lfd.numarcs * pipe.dictionaries.size(POS) * pipe.dictionaries.size(POS)];
			Arrays.fill(nb, NULL);

			//ppcc1 = new FeatureDataItem[lfd.len * lfd.len * lfd.len];	// pp attachment, punc head and part of conjunction
			ppcc1 = new double[lfd.len * lfd.len * lfd.len];	// pp attachment, punc head and part of conjunction
			Arrays.fill(ppcc1, NULL);

			//cc2 = new FeatureDataItem[lfd.len * lfd.len * lfd.len];		// arg, head, left/right
			cc2 = new double[lfd.len * lfd.len * lfd.len];
			Arrays.fill(cc2, NULL);

			//nonproj = new FeatureDataItem[lfd.nuparcs * BINNED_BUCKET];	// nonproj arc, [dep id][nonproj binned num]
		}
	}

	public FeatureVector getPPFeatureVector(int gp, int h, int m) {
		// (h,m) may not be an arc

		Utils.Assert(lfd.arc2id[h * lfd.len + gp] >= 0);
		
		int pos = (h * lfd.len + gp) * lfd.len + m;		// h is preposition, different from conj/punc
		FeatureVector fv = synFactory.createPPFeatureVector(lfd.inst, gp, h, m);
		ppcc1[pos] = lfd.parameters.dotProduct(fv) * lfd.gamma;
		return fv;
	}
	
	public FeatureVector getCC1FeatureVector(int left, int arg, int right) {
		// dependency relation is not known, cannot check
		
		int pos = (arg * lfd.len + left) * lfd.len + right;		// arg is conj, different from prep/punc
		FeatureVector fv = synFactory.createCC1FeatureVector(lfd.inst, left, arg, right);
		ppcc1[pos] = lfd.parameters.dotProduct(fv) * lfd.gamma;
		return fv;
	}
	
	public FeatureVector getCC2FeatureVector(int arg, int head, int child) {
		// dependency relation is not known, cannot check
		
		int pos = (arg * lfd.len + head) * lfd.len + child;	
		FeatureVector fv = synFactory.createCC2FeatureVector(lfd.inst, arg, head, child);
		cc2[pos] = lfd.parameters.dotProduct(fv) * lfd.gamma;
		return fv;
	}
	
	public FeatureVector getPNXFeatureVector(int head, int arg, int pair) {
		// dependency relation is not known, cannot check
		
		int pos = (arg * lfd.len + head) * lfd.len + pair;		// arg is punc, different from prep/conj
		FeatureVector fv = synFactory.createPNXFeatureVector(lfd.inst, head, arg, pair);
		ppcc1[pos] = lfd.parameters.dotProduct(fv) * lfd.gamma;
		return fv;
	}
	
	public FeatureVector getSpanFeatureVector(int h, int end, int punc, int bin) {
		Utils.Assert(bin <= MAX_SPAN_LENGTH);
		
		int pos = ((h * 2 + end) * 2 + punc) * (MAX_SPAN_LENGTH + 1) + bin;
		FeatureVector fv = synFactory.createSpanFeatureVector(lfd.inst, h, end, punc, bin);
		span[pos] = lfd.parameters.dotProduct(fv) * lfd.gamma;
		return fv;
	}
	
	public FeatureVector getNeighborFeatureVector(int par, int h, int left, int right) {
		int id = lfd.arc2id[h * lfd.len + par];
		int size = pipe.dictionaries.size(POS);
		
		Utils.Assert(id >= 0 && left < size && right < size);
		
		int pos = (id * size + left) * size + right;		
		FeatureVector fv = synFactory.createNeighborFeatureVector(lfd.inst, par, h, left, right);
		nb[pos] = lfd.parameters.dotProduct(fv) * lfd.gamma;
		return fv;
	}
	
	public FeatureVector getChildNumFeatureVector(int h, int leftNum, int rightNum) {
		Utils.Assert(leftNum <= MAX_CHILD_NUM && rightNum <= MAX_CHILD_NUM);
		
		int pos = (h * (MAX_CHILD_NUM + 1) + leftNum) * (MAX_CHILD_NUM + 1) + rightNum;		
		FeatureVector fv = synFactory.createChildNumFeatureVector(lfd.inst, h, leftNum, rightNum);
		cn[pos] = lfd.parameters.dotProduct(fv) * lfd.gamma;
		return fv;
	}
	
	public FeatureVector getNonprojFeatureVector(DependencyArcList arclis, int h, int m) {
		int id = lfd.arc2id[m * lfd.len + h];
		int num = synFactory.getBinnedDistance(arclis.nonproj[m]);
		
		Utils.Assert(id >= 0 && num >= 0 && num < BINNED_BUCKET);
		
		int pos = id * BINNED_BUCKET + num;		
		FeatureVector fv = synFactory.createNonprojFeatureVector(lfd.inst, num, h, m);
		nonproj[pos] = lfd.parameters.dotProduct(fv) * lfd.gamma;
		return fv;
	}
	
	public double getPPScore(int gp, int h, int m) {
		// (h,m) may not be an arc
		
		Utils.Assert(lfd.arc2id[h * lfd.len + gp] >= 0);
		
		int pos = (h * lfd.len + gp) * lfd.len + m;		// h is preposition, different from conj/punc
		if (ppcc1[pos] == NULL)
			getPPFeatureVector(gp, h, m);

		return ppcc1[pos];
	}
	
	public double getCC1Score(int left, int arg, int right) {
		// dependency relation is not known, cannot check
		
		int pos = (arg * lfd.len + left) * lfd.len + right;		// arg is conj, different from prep/punc
		if (ppcc1[pos] == NULL)
			getCC1FeatureVector(left, arg, right);

		return ppcc1[pos];
	}
	
	public double getCC2Score(int arg, int head, int child) {
		// dependency relation is not known, cannot check
		
		int pos = (arg * lfd.len + head) * lfd.len + child;	
		if (cc2[pos] == NULL)
			getCC2FeatureVector(arg, head, child);

		return cc2[pos];
	}
	
	public double getPNXScore(int head, int arg, int pair) {
		// dependency relation is not known, cannot check
		
		int pos = (arg * lfd.len + head) * lfd.len + pair;		// arg is punc, different from prep/conj
		if (ppcc1[pos] == NULL)
			getPNXFeatureVector(head, arg, pair);

		return ppcc1[pos];
	}
	
	public double getSpanScore(int h, int end, int punc, int bin) {
		Utils.Assert(bin <= MAX_SPAN_LENGTH);
		
		int pos = ((h * 2 + end) * 2 + punc) * (MAX_SPAN_LENGTH + 1) + bin;	
		if (span[pos] == NULL)
			getSpanFeatureVector(h, end, punc, bin);

		return span[pos];
	}
	
	public double getNeighborScore(int par, int h, int left, int right) {
		int id = lfd.arc2id[h * lfd.len + par];
		int size = pipe.dictionaries.size(POS);
		
		Utils.Assert(id >= 0 && left < size && right < size);
		
		int pos = (id * size + left) * size + right;		
		if (nb[pos] == NULL)
			getNeighborFeatureVector(par, h, left, right);

		return nb[pos];
	}
	
	public double getChildNumScore(int h, int leftNum, int rightNum) {
		Utils.Assert(leftNum <= MAX_CHILD_NUM && rightNum <= MAX_CHILD_NUM);
		
		int pos = (h * (MAX_CHILD_NUM + 1) + leftNum) * (MAX_CHILD_NUM + 1) + rightNum;		
		if (cn[pos] == NULL) {
			getChildNumFeatureVector(h, leftNum, rightNum);
		}
		return cn[pos];
	}
	
	public double getNonprojScore(DependencyArcList arclis, int h, int m) {
		int id = lfd.arc2id[m * lfd.len + h];
		int num = synFactory.getBinnedDistance(arclis.nonproj[m]);
		
		Utils.Assert(id >= 0 && num >= 0 && num < BINNED_BUCKET);
		
		int pos = id * BINNED_BUCKET + num;		
		if (nonproj[pos] == NULL)
			getNonprojFeatureVector(arclis, h, m);

		return nonproj[pos];
	}
	
	public FeatureVector getFeatureVector(DependencyInstance now) {
		FeatureVector fv = new FeatureVector(lfd.size);
		
		if (!lfd.options.useHO)
			return fv;
		
		int[] heads = now.heads;
		int[] toks = now.formids;
		int len = now.length;
		DependencyArcList arcLis = new DependencyArcList(heads, lfd.options.useHO);
		
		// non-proj
		//for (int i = 0; i < len; ++i) {
		//	if (heads[i] == -1)
		//		continue;
		//	fv.addEntries(getNonprojFeatureVector(arcLis, heads[i], i));
		//}

		int[] pos = now.postagids;
		int[] posA = now.cpostagids;
		SpecialPos[] specialPos = now.specialPos;
		int[] spanLeft = arcLis.left;
		int[] spanRight = arcLis.right;

		long code = 0;

		for (int i = 0; i < len; ++i) {
			// pp attachment
			if (SpecialPos.P == specialPos[i]) {
				int par = heads[i];
				int[] c = synFactory.findPPArg(heads, specialPos, arcLis, i);
				for (int z = 0; z < c.length; ++z) {
					if (par != -1 && c[z] != -1) {
						fv.addEntries(getPPFeatureVector(par, i, c[z]));
					}
				}
			}

			// conjunction pos
			if (SpecialPos.C == specialPos[i]) {
				int[] arg = synFactory.findConjArg(arcLis, heads, i);
				int head = arg[0];
				int left = arg[1];
				int right = arg[2];
				if (left != -1 && right != -1 && left < right) {
					fv.addEntries(getCC1FeatureVector(left, i, right));
					if (head != -1) {
						fv.addEntries(getCC2FeatureVector(i, head, left));
						fv.addEntries(getCC2FeatureVector(i, head, right));
					}
				}
			}

			// punc head
			if (SpecialPos.PNX == specialPos[i]) {
				int j = synFactory.findPuncCounterpart(toks, i);
				if (j != -1 && heads[i] == heads[j])
					fv.addEntries(getPNXFeatureVector(heads[i], i, j));
			}
		}

		int rb = synFactory.getMSTRightBranch(specialPos, arcLis, 0, 0);
		
		code = synFactory.createArcCodeP(Arc.RB, 0x0);
		synFactory.addArcFeature(code, (double)rb / len, fv);

		for (int m = 1; m < len; ++m) {

			// child num
			int leftNum = 0;
			int rightNum = 0;
			int maxDigit = 64 - Arc.numArcFeatBits - synFactory.flagBits;
			//int maxDigit = 64 - Arc.numArcFeatBits - 4;
			int maxChildStrNum = (maxDigit / synFactory.tagNumBits) - 1;
			int childStrNum = 0;
			code = pos[m];
			
			int st = arcLis.startIndex(m);
			int ed = arcLis.endIndex(m);
			
			for (int j = st; j < ed; ++j) {
				int cid = arcLis.get(j);
				if (SpecialPos.PNX != specialPos[cid]) {
					if (cid < m && leftNum < MAX_CHILD_NUM)
						leftNum++;
					else if (cid > m && rightNum < MAX_CHILD_NUM)
						rightNum++;
					if (childStrNum < maxChildStrNum) {
						code = ((code << synFactory.tagNumBits) | pos[cid]);
						childStrNum++;
					}
				}
			}
			code = ((code << Arc.numArcFeatBits) | Arc.CN_STR.ordinal()) << synFactory.flagBits;
			//code = ((code << Arc.numArcFeatBits) | Arc.CN_STR.ordinal()) << 4;
			synFactory.addArcFeature(code, fv);

			fv.addEntries(getChildNumFeatureVector(m, leftNum, rightNum));

			// span
			int end = spanRight[m] == len ? 1 : 0;
			int punc = (spanRight[m] < len && SpecialPos.PNX == specialPos[spanRight[m]]) ? 1 : 0;
			int bin = Math.min(MAX_SPAN_LENGTH, (spanRight[m] - spanLeft[m]));
			fv.addEntries(getSpanFeatureVector(m, end, punc, bin));

			if (heads[m] != -1) {
				// neighbors
				int leftID = spanLeft[m] > 0 ? posA[spanLeft[m] - 1] : synFactory.TOKEN_START;
				int rightID = spanRight[m] < len ? posA[spanRight[m]] : synFactory.TOKEN_END;
				if (leftID > 0 && rightID > 0) {
					fv.addEntries(getNeighborFeatureVector(heads[m], m, leftID, rightID));
				}
			}
		}
		
		return fv;
	}
	
	public double getScore(DependencyInstance now, DependencyArcList arcLis) {
		return getScore(now.heads, arcLis);
	}
	
	public double getScore(int[] heads, DependencyArcList arcLis) {
		
		DependencyInstance now = lfd.inst;
		
		FeatureVector tmpFv = new FeatureVector(lfd.size);
		double score = 0.0;	
		
		if (!lfd.options.useHO)
			return score;
		
		int[] toks = now.formids;
		int len = now.length;
		//DependencyArcList arcLis = new DependencyArcList(heads, lfd.options.useHO);
		
		// non-proj
		//for (int i = 0; i < len; ++i) {
		//	if (heads[i] == -1)
		//		continue;
		//	score += getNonprojScore(arcLis, heads[i], i);
		//}

		int[] pos = now.postagids;
		int[] posA = now.cpostagids;
		SpecialPos[] specialPos = now.specialPos;
		int[] spanLeft = arcLis.left;
		int[] spanRight = arcLis.right;

		long code = 0;

		for (int i = 0; i < len; ++i) {
			// pp attachment
			if (SpecialPos.P == specialPos[i]) {
				int par = heads[i];
				int[] c = synFactory.findPPArg(heads, specialPos, arcLis, i);
				for (int z = 0; z < c.length; ++z) {
					if (par != -1 && c[z] != -1) {
						score += getPPScore(par, i, c[z]);
					}
				}
			}

			// conjunction pos
			if (SpecialPos.C == specialPos[i]) {
				int[] arg = synFactory.findConjArg(arcLis, heads, i);
				int head = arg[0];
				int left = arg[1];
				int right = arg[2];
				if (left != -1 && right != -1 && left < right) {
					score += getCC1Score(left, i, right);
					if (head != -1) {
						score += getCC2Score(i, head, left);
						score += getCC2Score(i, head, right);
					}
				}
			}

			// punc head
			if (SpecialPos.PNX == specialPos[i]) {
				int j = synFactory.findPuncCounterpart(toks, i);
				if (j != -1 && heads[i] == heads[j])
					score += getPNXScore(heads[i], i, j);
			}
		}

		int rb = synFactory.getMSTRightBranch(specialPos, arcLis, 0, 0);
		
		code = synFactory.createArcCodeP(Arc.RB, 0x0);
		synFactory.addArcFeature(code, (double)rb / len, tmpFv);

		for (int m = 1; m < len; ++m) {

			// child num
			int leftNum = 0;
			int rightNum = 0;
			int maxDigit = 64 - Arc.numArcFeatBits - synFactory.flagBits;
			//int maxDigit = 64 - Arc.numArcFeatBits - 4;
			int maxChildStrNum = (maxDigit / synFactory.tagNumBits) - 1;
			int childStrNum = 0;
			code = pos[m];
			
			int st = arcLis.startIndex(m);
			int ed = arcLis.endIndex(m);
			
			for (int j = st; j < ed; ++j) {
				int cid = arcLis.get(j);
				if (SpecialPos.PNX != specialPos[cid]) {
					if (cid < m && leftNum < MAX_CHILD_NUM)
						leftNum++;
					else if (cid > m && rightNum < MAX_CHILD_NUM)
						rightNum++;
					if (childStrNum < maxChildStrNum) {
						code = ((code << synFactory.tagNumBits) | pos[cid]);
						childStrNum++;
					}
				}
			}
			code = ((code << Arc.numArcFeatBits) | Arc.CN_STR.ordinal()) << synFactory.flagBits;
			//code = ((code << Arc.numArcFeatBits) | Arc.CN_STR.ordinal()) << 4;
			synFactory.addArcFeature(code, tmpFv);

			score += getChildNumScore(m, leftNum, rightNum);

			// span
			int end = spanRight[m] == len ? 1 : 0;
			int punc = (spanRight[m] < len && SpecialPos.PNX == specialPos[spanRight[m]]) ? 1 : 0;
			int bin = Math.min(MAX_SPAN_LENGTH, (spanRight[m] - spanLeft[m]));
			score += getSpanScore(m, end, punc, bin);

			if (heads[m] != -1) {
				// neighbors
				int leftID = spanLeft[m] > 0 ? posA[spanLeft[m] - 1] : synFactory.TOKEN_START;
				int rightID = spanRight[m] < len ? posA[spanRight[m]] : synFactory.TOKEN_END;
				if (leftID > 0 && rightID > 0) {
					score += getNeighborScore(heads[m], m, leftID, rightID);
				}
			}
		}
		
		score += lfd.parameters.dotProduct(tmpFv) * lfd.gamma;
		return score;
	}

	public FeatureVector getFeatureDifference(DependencyInstance gold, 
			DependencyInstance pred)
	{
		FeatureVector dfv = getFeatureVector(gold);
		dfv.addEntries(getFeatureVector(pred), -1.0);

		return dfv;
	}

}
