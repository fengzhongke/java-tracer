package com.ali.trace.spy.helper;

public class ThreadFileNameGenerator implements IFileNameGenerator {

	private String path;

	public ThreadFileNameGenerator(String path) {
		this.path = path;
	}

	@Override
	public String getName() {
		Thread thread = Thread.currentThread();
		StackTraceElement[] traces = thread.getStackTrace();
		StackTraceElement trace = traces[traces.length - 1];
		String fName = new StringBuilder(path).append("/").append(trace.getClassName()).append("-")
				.append(thread.getId()).append(".xml").toString();
		return fName;
	}

}
