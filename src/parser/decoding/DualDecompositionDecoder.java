package parser.decoding;

import optimality.Optimality;
import parser.DependencyInstance;
import parser.GlobalFeatureData;
import parser.LocalFeatureData;
import parser.Options;

public class DualDecompositionDecoder extends DependencyDecoder {
	
	private Optimality opt;

	public DualDecompositionDecoder(Options options)
	{
		this.options = options;
		opt = new Optimality(options);
	}

	@Override
	public DependencyInstance decode(DependencyInstance inst,
			LocalFeatureData lfd, GlobalFeatureData gfd, boolean addLoss) {
		DependencyInstance predInst = opt.decode(inst, lfd, gfd, addLoss);
		return predInst;
	}

}
