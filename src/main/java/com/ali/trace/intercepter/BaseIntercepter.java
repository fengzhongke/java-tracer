package com.ali.trace.intercepter;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

/**
 * print XML format stacks
 * 
 * @author hanlang.hl
 *
 */
public abstract class BaseIntercepter implements IIntercepter {
	private final String path;
	private final ThreadLocal<Writer> t_outs = new ThreadLocal<Writer>();

	public BaseIntercepter(String path) {
		this.path = path;
	}

	protected void write(String line) throws Exception {
		if (line != null) {
			Writer out = t_outs.get();
			if (out == null) {
				Thread thread = Thread.currentThread();
				StackTraceElement[] traces = thread.getStackTrace();
				StackTraceElement trace = traces[traces.length - 1];
				String fName = new StringBuilder(path).append("/").append(trace.getClassName()).append("-")
						.append(thread.getId()).append(".xml").toString();
				t_outs.set(out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fName))));
			}
			out.write(line);
			out.flush();
		}
	}

}
