package com.ali.trace.spy.intercepter;

import com.ali.trace.spy.core.NodePool;
import com.ali.trace.spy.util.BaseNode;

import java.util.Stack;

/**
 * @author nkhanlang@163.com
 */
public abstract class BaseTreeIntercepter<T extends BaseNode> extends BaseIntercepter {

	protected final String c;
	protected final String m;
	protected final ThreadLocal<Stack<T>> t_stack = new ThreadLocal<Stack<T>>();
	protected final ThreadLocal<Stack<Long>> t_time = new ThreadLocal<Stack<Long>>();
	protected final NodePool nodePool;

	public BaseTreeIntercepter(String c, String m) {
		super(null);
		this.c = c;
		this.m = m;
		nodePool = NodePool.getPool();
	}

	public String getC(){
		return c;
	}
	public String getM(){
		return m;
	}

	public void start(String c, String m) {
		Stack<T> stack = t_stack.get();
		Stack<Long> time = t_time.get();
		if (c.equalsIgnoreCase(this.c) && m.equalsIgnoreCase(this.m)) {
			if (stack == null) {
				t_stack.set(stack = new Stack<T>());
				T node = getNode(BaseNode.getId(c, m));
				nodePool.addNode(node);
				stack.add(node);
				t_time.set(time = new Stack<Long>());
			}
		}
		if (stack != null && !stack.isEmpty()) {
			if (!time.isEmpty()) {
				stack.push((T)stack.peek().addSon(BaseNode.getId(c, m)));
			}
			time.add(System.currentTimeMillis());
		}
	}

	protected abstract T getNode(long metaId);

	public void end(String c, String m) {
		Stack<T> stack = t_stack.get();
		Stack<Long> time = t_time.get();
		T node = null;
		if (stack != null && !stack.isEmpty()) {
			node = stack.pop();
			node.addRt(System.currentTimeMillis() - time.pop());
		}
		if (stack != null && stack.isEmpty()) {
			t_stack.set(null);
			t_time.set(null);
		}
	}
}
