package com.ali.trace.spy.jetty.vo;

/**
 * @author nkhanlang@163.com
 * @date 2019-08-21 00:51
 */
public class ConfigVO {
    private String include;
    private String exclude;

    public ConfigVO(String include, String exclude){
        this.include = include != null ? include : "";
        this.exclude = exclude != null ? exclude : "";
    }

    public String getInclude() {
        return include;
    }

    public void setInclude(String include) {
        this.include = include != null ? include : "";
    }

    public String getExclude() {
        return exclude;
    }

    public void setExclude(String exclude) {
        this.exclude = exclude != null ? exclude : "";
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"include\":\"").append(escapeJson(include)).append("\"");
        sb.append(",\"exclude\":\"").append(escapeJson(exclude)).append("\"}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}