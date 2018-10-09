package com.ali.trace.inject;

import com.ali.trace.intercepter.IIntercepter;

public class TraceEnhance {

	/**
	 * inner intercepter
	 */
	private static IIntercepter intercepter;

	/**
	 * set intercepter
	 */
	public static void setIntecepter(IIntercepter intercepter) {
		if (intercepter != null) {
			TraceEnhance.intercepter = intercepter;
		}
	}

	/**
	 * inject point before execute method body
	 */
	public static final void s(String c, String m) {
		try {
			if (intercepter != null) {
				intercepter.start(c, m);
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	/**
	 * inject point after execute method body
	 */
	public static final void e(String c, String m) {
		try {
			if (intercepter != null) {
				intercepter.end(c, m);
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

}
