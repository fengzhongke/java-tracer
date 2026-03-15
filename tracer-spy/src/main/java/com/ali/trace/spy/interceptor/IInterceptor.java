package com.ali.trace.spy.interceptor;

/**
 * do around method except exception
 *
 * @author nkhanlang@163.com
 *
 */
public interface IInterceptor {

	/**
	 * do before with class and method name
	 */
	void start(String c, String m) throws Exception;

	/**
	 * do after with class and method name
	 */
	void end(String c, String m) throws Exception;
}
