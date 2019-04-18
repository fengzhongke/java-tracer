package com.ali.trace.intercepter;

import java.io.Writer;
import java.util.Stack;

import com.ali.trace.util.TreeNode;

public class ThreadCompressIntercepter extends BaseIntercepter {

    private String c;
    private String m;
    private final ThreadLocal<Stack<TreeNode>> t_stack = new ThreadLocal<Stack<TreeNode>>();
    private final ThreadLocal<Stack<Long>> t_time = new ThreadLocal<Stack<Long>>();

    public ThreadCompressIntercepter(String path, String c, String m) {
        super(path);
        this.c = c;
        this.m = m;
    }

    @Override
    public void start(String c, String m) {
        Stack<TreeNode> stack = t_stack.get();
        Stack<Long> time = t_time.get();
        if (c.startsWith(this.c) && m.equalsIgnoreCase(this.m)) {
            if (stack == null) {
                t_stack.set(stack = new Stack<TreeNode>());
                stack.add(new TreeNode(TreeNode.getId(c, m)));
                t_time.set(time = new Stack<Long>());
            }
        }
        if (stack != null && !stack.isEmpty()) {
            if(!time.isEmpty()){
                stack.push(stack.peek().addSon(TreeNode.getId(c, m), 1L));
            }
            time.add(System.currentTimeMillis());
        }
    }

    @Override
    public void end(String c, String m) {
        Stack<TreeNode> stack = t_stack.get();
        Stack<Long> time = t_time.get();
        TreeNode node = null;
        if (stack != null && stack.size() > 0) {
            node = stack.pop();
            node.addRt(System.currentTimeMillis() - time.pop());
            if(!node.equal(c, m)){
                System.err.println("not equal : " + c + "," + m + ":" + node.getName());
            }
        }
        if (stack != null && stack.isEmpty()) {
            try {
                Writer out = getWriter();
                node.writeFile(out);
                out.flush();
            } catch (Throwable t) {
                t.printStackTrace();
            }
            t_stack.set(null);
            t_time.set(null);
        }
    }
}
