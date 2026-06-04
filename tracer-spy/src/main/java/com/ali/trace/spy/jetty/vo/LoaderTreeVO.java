package com.ali.trace.spy.jetty.vo;

import java.util.List;

/**
 * Tree view VO for ClassLoader
 */
public class LoaderTreeVO {
    private String name;
    private int id;
    private int cnt;
    private List<ClassTreeVO> classes;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getCnt() {
        return cnt;
    }

    public void setCnt(int cnt) {
        this.cnt = cnt;
    }

    public List<ClassTreeVO> getClasses() {
        return classes;
    }

    public void setClasses(List<ClassTreeVO> classes) {
        this.classes = classes;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"name\":\"").append(escapeJson(name)).append("\"");
        sb.append(",\"id\":").append(id);
        sb.append(",\"cnt\":").append(cnt);
        sb.append(",\"classes\":").append(classes != null ? classes.toString() : "[]");
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}