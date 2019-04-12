package com.ali.trace.intercepter;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.Writer;

import com.ali.trace.support.IFileNameGenerator;
import com.ali.trace.support.ThreadFileNameGenerator;

/**
 * print XML format stacks
 * 
 * @author hanlang.hl
 *
 */
public abstract class BaseIntercepter implements IIntercepter {
	private final String path;
	private final ThreadLocal<Writer> t_outs = new ThreadLocal<Writer>();
	private IFileNameGenerator nameGenerator;

	public BaseIntercepter(String path) {
		this.path = path;
	}

	public void setNameGenerator(IFileNameGenerator nameGenerator) {
		this.nameGenerator = nameGenerator;
	}

	protected void write(String line) throws Exception {
		if (line != null) {
			Writer out = t_outs.get();
			if (out == null) {
				if (nameGenerator == null) {
					synchronized (this) {
						if (nameGenerator == null) {
							nameGenerator = new ThreadFileNameGenerator(path);
						}
					}
				}
				String fName = nameGenerator.getName();
				t_outs.set(out = new BufferedWriter(new FileWriter(fName)));
			}
			out.write(line);
			out.flush();
		}
	}

}
