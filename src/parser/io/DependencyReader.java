package parser.io;


import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import parser.DependencyInstance;
import parser.Options;

public abstract class DependencyReader {
	
	BufferedReader reader;
	boolean isLabeled;
	Options options;
	
	public static DependencyReader createDependencyReader(Options options) {
		String format = options.format;
		if (format.equalsIgnoreCase("CONLL06") || format.equalsIgnoreCase("CONLL-06")) {
			return new Conll06Reader(options);
		} else if (format.equalsIgnoreCase("CONLLX") || format.equalsIgnoreCase("CONLL-X")) {
			return new Conll06Reader(options);
		} else if (format.equalsIgnoreCase("CONLL09") || format.equalsIgnoreCase("CONLL-09")) {
			return new Conll09Reader(options);
		} else {
			System.out.printf("!!!!! Unsupported file format: %s%n", format);
			return new Conll06Reader(options);
		}
	}
	
	public abstract DependencyInstance nextInstance() throws IOException;
	public abstract boolean IsLabeledDependencyFile(String file) throws IOException;
	
	public boolean startReading(String file) throws IOException {
		isLabeled = IsLabeledDependencyFile(file);
		reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF8"));
		return isLabeled;
	}
	
	public void close() throws IOException { if (reader != null) reader.close(); }

    
}
