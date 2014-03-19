package parser.decoding;

import parser.DependencyInstance;
import parser.GlobalFeatureData;
import parser.LocalFeatureData;
import parser.Options;
import parser.Options.LearningMode;

public abstract class DependencyDecoder {
	
	Options options;
	public double totalLoopCount;
	public double totalClimbTime;
	public double totalClimbAndSampleTime;
	
	public static DependencyDecoder createDependencyDecoder(Options options)
	{
		if (options.learningMode == LearningMode.Basic) {
			if (!options.projective)
				return new ChuLiuEdmondDecoder(options);
			// TODO: CYK 1st order decoder
		} else
			return new HillClimbingDecoder(options);
		
		return null;
	}
    
    public void shutdown()
    {
    }

	public abstract DependencyInstance decode(DependencyInstance inst,
						LocalFeatureData lfd,
						GlobalFeatureData gfd,
						boolean addLoss);
}
