package com.ali.trace.spy.intercepter;

import java.util.Stack;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.ali.trace.spy.util.TreeNode;

public class ThreadCompressIntercepter extends BaseIntercepter {

    private LinkedBlockingQueue<TreeNode> queue = new LinkedBlockingQueue<TreeNode>();
    private String c;
    private String m;
    private final ThreadLocal<Stack<TreeNode>> t_stack = new ThreadLocal<Stack<TreeNode>>();
    private final ThreadLocal<Stack<Long>> t_time = new ThreadLocal<Stack<Long>>();

    public ThreadCompressIntercepter(String path, String c, String m) {
        super(path);
        this.c = c;
        this.m = m;
    }

    public TreeNode getNode() throws InterruptedException {
        TreeNode node = null;
        if (!queue.isEmpty()) {
            node = queue.poll(50, TimeUnit.MILLISECONDS);
        }
        return node;
    }

    public void start(String c, String m) {
        Stack<TreeNode> stack = t_stack.get();
        Stack<Long> time = t_time.get();
        if (c.equalsIgnoreCase(this.c) && m.equalsIgnoreCase(this.m)) {
            if (stack == null) {
                t_stack.set(stack = new Stack<TreeNode>());
                stack.add(new TreeNode(TreeNode.getId(c, m)));
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
            queue.offer(node);
            t_stack.set(null);
            t_time.set(null);
        }
    }
}
