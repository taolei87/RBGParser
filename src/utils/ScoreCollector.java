package utils;

import parser.Parameters;

public class ScoreCollector implements Collector {
	
	float[] weights;
	public double score;
	
	public ScoreCollector(Parameters params) {
		weights = params.params;
		score = 0;
	}
	
	public ScoreCollector(Parameters params, boolean isLabel) {
		weights = isLabel ? params.paramsL : params.params;
		score = 0;
	}
	
	@Override
	public void addEntry(int x) {
		score += weights[x];
	}

	@Override
	public void addEntry(int x, double va) {
		score += weights[x]*va;
	}
	
}
