package com.ali.trace.spy.intercepter;

public class ThreadIntercepter extends CommonIntercepter {

	private String c;
	private String m;
	private final ThreadLocal<Integer> t_stack = new ThreadLocal<Integer>();

	public ThreadIntercepter(String path, boolean printTime, String c, String m) {
		super(path, printTime);
		this.c = c;
		this.m = m;
	}

	@Override
	public void start(String c, String m) {
		Integer stack = t_stack.get();
		if (c.startsWith(this.c) && m.equalsIgnoreCase(this.m)) {
			if (stack == null) {
				t_stack.set(stack = 1);
			} else {
				t_stack.set(++stack);
			}
		}
		if (stack != null && stack != 0) {
			super.start(c, m);
		}
	}

	@Override
	public void end(String c, String m) {
		Integer stack = t_stack.get();
		if (stack != null && stack != 0) {
			super.end(c, m);
		}
		if (c.equalsIgnoreCase(this.c) && m.equalsIgnoreCase(this.m)) {
			t_stack.set(--stack);
		}
	}
}
