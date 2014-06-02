package parser.decoding;

import java.io.BufferedWriter;

import parser.DependencyInstance;
import parser.GlobalFeatureData;
import parser.LocalFeatureData;
import parser.Options;
import parser.Options.LearningMode;
import utils.Utils;

public abstract class DependencyDecoder {
	
	Options options;
	
	public static DependencyDecoder createDependencyDecoder(Options options)
	{
		if (options.learningMode != LearningMode.Basic && options.projective) {
			System.out.println("WARNING: high-order projective parsing not supported. "
					+ "Switched to non-projective parsing.");
			options.projective = false;
		}
		
		if (options.learningMode == LearningMode.Basic) {
			if (!options.projective)
				return new ChuLiuEdmondDecoder(options);
			else
				return new CYKDecoder(options);			
		} else
			return new HillClimbingDecoder(options);
		
		//return null;
	}
    
    public void shutdown()
    {
    }
    
    public DependencyInstance decodeGold(DependencyInstance inst, LocalFeatureData lfd,
			GlobalFeatureData gfd,
			boolean addLoss) {
    	Utils.Assert(false);
    	return null;
    }

	public abstract DependencyInstance decode(DependencyInstance inst,
						LocalFeatureData lfd,
						GlobalFeatureData gfd,
						boolean addLoss);

	public void outputArcCount(BufferedWriter bw) {
	}

	public void resetCount() {
	}
}
