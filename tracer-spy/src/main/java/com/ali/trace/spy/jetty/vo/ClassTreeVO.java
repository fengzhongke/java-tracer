package com.ali.trace.spy.jetty.vo;

/**
 * Tree view VO for Class
 */
public class ClassTreeVO {
    private String name;
    private int type;
    private int loaderId;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getLoaderId() {
        return loaderId;
    }

    public void setLoaderId(int loaderId) {
        this.loaderId = loaderId;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"name\":\"").append(escapeJson(name)).append("\"");
        sb.append(",\"type\":").append(type);
        sb.append(",\"loaderId\":").append(loaderId);
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}