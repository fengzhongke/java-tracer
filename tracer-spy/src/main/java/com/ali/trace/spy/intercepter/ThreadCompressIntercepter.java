package com.ali.trace.spy.intercepter;

import com.ali.trace.spy.core.NodePool;
import com.ali.trace.spy.util.TreeNode;

import java.util.Stack;

/**
 * @author nkhanlang@163.com
 */
public class ThreadCompressIntercepter extends BaseIntercepter {

    private final String c;
    private final String m;
    private final ThreadLocal<Stack<TreeNode>> t_stack = new ThreadLocal<Stack<TreeNode>>();
    private final ThreadLocal<Stack<Long>> t_time = new ThreadLocal<Stack<Long>>();
    private final NodePool nodePool;

    public ThreadCompressIntercepter(String c, String m) {
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
        Stack<TreeNode> stack = t_stack.get();
        Stack<Long> time = t_time.get();
        if (c.equalsIgnoreCase(this.c) && m.equalsIgnoreCase(this.m)) {
            if (stack == null) {
                t_stack.set(stack = new Stack<TreeNode>());
                long metaId = TreeNode.getId(c, m);
                TreeNode node = new TreeNode(metaId);
                nodePool.addNode(node);
                stack.add(node);
                t_time.set(time = new Stack<Long>());
            }
        }
        if (stack != null && !stack.isEmpty()) {
            if (!time.isEmpty()) {
                stack.push(stack.peek().addSon(TreeNode.getId(c, m), 1L));
            }
            time.add(System.currentTimeMillis());
        }
    }

    public void end(String c, String m) {
        Stack<TreeNode> stack = t_stack.get();
        Stack<Long> time = t_time.get();
        TreeNode node = null;
        if (stack != null && !stack.isEmpty()) {
            node = stack.pop();
            node.addRt(System.currentTimeMillis() - time.pop());
            if (!node.equal(c, m)) {
                System.err.println("not equal : " + c + "," + m + ":" + node.getName());
            }
        }
        if (stack != null && stack.isEmpty()) {
            t_stack.set(null);
            t_time.set(null);
        }
    }
}
