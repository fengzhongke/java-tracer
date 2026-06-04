package com.ali.trace.spy.util;

/**
 * @auther nkhanlang@163.com
 * @date 2019-08-29 20:22
 */
public class RootNode {
    private final long id;
    private final String type;
    private final BaseNode node;
    private final long start;


    public RootNode(long id, BaseNode node, String type) {
        this.id = id;
        this.node = node;
        this.type = type;
        this.start = System.currentTimeMillis();
    }

    public RootNode(long id, BaseNode node, String type, long start) {
        this.id = id;
        this.node = node;
        this.type = type;
        this.start = start;
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

    /**
     * Serialize to JSON string
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"id\":").append(id);
        sb.append(",\"type\":\"").append(escapeJson(type)).append("\"");
        sb.append(",\"start\":").append(start);
        sb.append(",\"node\":");
        if (node != null) {
            node.build(sb);
        } else {
            sb.append("{}");
        }
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
