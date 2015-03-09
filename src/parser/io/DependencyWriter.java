package parser.io;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

import parser.DependencyInstance;
import parser.DependencyPipe;
import parser.Options;

public abstract class DependencyWriter {
	BufferedWriter writer;
	Options options;
	String[] labels;
	boolean first, isLabeled;
	
	public static DependencyWriter createDependencyWriter(Options options, DependencyPipe pipe) {
		String format = options.format;
		if (format.equalsIgnoreCase("CONLL06") || format.equalsIgnoreCase("CONLL-06")) {
			return new Conll06Writer(options, pipe);
		} else if (format.equalsIgnoreCase("CONLLX") || format.equalsIgnoreCase("CONLL-X")) {
			return new Conll06Writer(options, pipe);
		} else if (format.equalsIgnoreCase("CONLL09") || format.equalsIgnoreCase("CONLL-09")) {
			return new Conll09Writer(options, pipe);
		} else {
			System.out.printf("!!!!! Unsupported file format: %s%n", format);
			return new Conll06Writer(options, pipe);
		}
	}
	
	public abstract void writeInstance(DependencyInstance inst) throws IOException;
	
	public void startWriting(String file) throws IOException {
		writer = new BufferedWriter(new FileWriter(file));
		first = true;
		isLabeled = options.learnLabel;
	}
	
	public void close() throws IOException {
		if (writer != null) writer.close();
	}
	
}
