package com.ali.trace.spy.intercepter;

/**
 * do around method except exception
 *
 * @author nkhanlang@163.com
 *
 */
public interface IIntercepter {

	/**
	 * do before with class and method name
	 */
	void start(String c, String m) throws Exception;

	/**
	 * do after with class and method name
	 */
	void end(String c, String m) throws Exception;
}
