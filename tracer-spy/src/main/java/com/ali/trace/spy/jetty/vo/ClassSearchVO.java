package com.ali.trace.spy.jetty.vo;

/**
 * Class search result VO — represents a loaded class with its ClassLoader info.
 * Used by /invoke/classes.json endpoint for Browse Class feature.
 */
public class ClassSearchVO {

    private String name;       // fully qualified class name
    private int loaderId;      // ClassLoader LoaderSet ID (0 = unknown/bootstrap)

    public ClassSearchVO() {}

    public ClassSearchVO(String name, int loaderId) {
        this.name = name;
        this.loaderId = loaderId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
        sb.append("{");
        sb.append("\"name\":\"").append(escapeJson(name)).append("\"");
        sb.append(",\"loaderId\":").append(loaderId);
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}