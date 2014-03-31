package parser;

public class GlobalFeatureData {

	final static int BINNED_BUCKET = 8;
	final static int MAX_CHILD_NUM = 5;
	final static int MAX_SPAN_LENGTH = 5;
	final static int MAX_FEATURE_NUM = 7;

	LocalFeatureData lfd;
	
	int RB;

	FeatureDataItem[] cn;	// [len][leftNum][rightNum]

	FeatureDataItem[] span;	// [len][end][punc][bin]

	FeatureDataItem[] nb;		// [maxid][cpos][cpos]

	FeatureDataItem[] ppcc1;	// pp attachment, punc head and part of conjunction

	FeatureDataItem[] fvs_cc2;		// arg, head, left/right

	FeatureDataItem[] nonproj;	// nonproj arc, [dep id][nonproj binned num]

	public GlobalFeatureData(LocalFeatureData lfd) 
	{
		this.lfd = lfd;
		
		// init array
		if (lfd.options.useHO) {
			cn = new FeatureDataItem[lfd.len * (MAX_CHILD_NUM + 1) * (MAX_CHILD_NUM + 1)];

			span = new FeatureDataItem[lfd.len * 2 * 2 * (MAX_SPAN_LENGTH + 1)];

			nb = new FeatureDataItem[lfd.nuparcs * lfd.pipe.tagDictionary.size() * lfd.pipe.tagDictionary.size()];

			ppcc1 = new FeatureDataItem[lfd.len * lfd.len * lfd.len];	// pp attachment, punc head and part of conjunction

			fvs_cc2 = new FeatureDataItem[lfd.len * lfd.len * lfd.len];		// arg, head, left/right

			nonproj = new FeatureDataItem[lfd.nuparcs * BINNED_BUCKET];	// nonproj arc, [dep id][nonproj binned num]
		}
	}

}
