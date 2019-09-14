package com.ali.trace.spy.util;

/**
 * @auther nkhanlang@163.com
 * @date 2019-08-29 20:22
 */
public class RootNode {
    private final long id;
    private final String type;
    private final BaseNode node;    private final long start;


    public RootNode(long id, BaseNode node, String type) {
        this.id = id;
        this.node = node;
        this.type = type;
        this.start = System.currentTimeMillis();
    }

    public long getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public BaseNode getNode() {
        return node;
    }

    public long getStart() {
        return start;
    }
}
