package com.ali.trace.spy.jetty.vo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author nkhanlang@163.com
 * @date 2019-08-21 00:51
 */
public class ConfigVO {
    private String include;
    private String exclude;
    private List<String> includePrefixes;
    private List<String> excludePrefixes;

    public ConfigVO(String include, String exclude){
        this.include = include != null ? include : "";
        this.exclude = exclude != null ? exclude : "";
        this.includePrefixes = new ArrayList<>();
        this.excludePrefixes = new ArrayList<>();
    }

    public ConfigVO(String include, String exclude, Set<String> includePrefixSet, Set<String> excludePrefixSet){
        this.include = include != null ? include : "";
        this.exclude = exclude != null ? exclude : "";
        this.includePrefixes = includePrefixSet != null ? new ArrayList<>(includePrefixSet) : new ArrayList<>();
        this.excludePrefixes = excludePrefixSet != null ? new ArrayList<>(excludePrefixSet) : new ArrayList<>();
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

    public List<String> getIncludePrefixes() {
        return includePrefixes;
    }

    public void setIncludePrefixes(List<String> includePrefixes) {
        this.includePrefixes = includePrefixes;
    }

    public List<String> getExcludePrefixes() {
        return excludePrefixes;
    }

    public void setExcludePrefixes(List<String> excludePrefixes) {
        this.excludePrefixes = excludePrefixes;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"include\":\"").append(escapeJson(include)).append("\"");
        sb.append(",\"exclude\":\"").append(escapeJson(exclude)).append("\"");
        sb.append(",\"includePrefixes\":");
        if (includePrefixes != null && !includePrefixes.isEmpty()) {
            sb.append("[");
            for (int i = 0; i < includePrefixes.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escapeJson(includePrefixes.get(i))).append("\"");
            }
            sb.append("]");
        } else {
            sb.append("[]");
        }
        sb.append(",\"excludePrefixes\":");
        if (excludePrefixes != null && !excludePrefixes.isEmpty()) {
            sb.append("[");
            for (int i = 0; i < excludePrefixes.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escapeJson(excludePrefixes.get(i))).append("\"");
            }
            sb.append("]");
        } else {
            sb.append("[]");
        }
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}