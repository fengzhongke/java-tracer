package com.ali.trace.spy.jetty.vo;

/**
 * Field value VO for displaying class field information.
 * Static fields show their current value; instance fields show type/name only.
 */
public class FieldVO {
    private String name;       // field name
    private String type;       // field type short name (e.g. "String", "int")
    private String value;      // current value string (static fields), "instance" for instance fields
    private boolean isStatic;  // true for static fields
    private String modifier;   // "public"/"private"/"protected"/"default"

    public FieldVO() {}

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public boolean isStatic() {
        return isStatic;
    }

    public void setStatic(boolean isStatic) {
        this.isStatic = isStatic;
    }

    public String getModifier() {
        return modifier;
    }

    public void setModifier(String modifier) {
        this.modifier = modifier;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"name\":\"").append(escapeJson(name)).append("\"");
        sb.append(",\"type\":\"").append(escapeJson(type)).append("\"");
        sb.append(",\"value\":\"").append(escapeJson(value)).append("\"");
        sb.append(",\"isStatic\":").append(isStatic);
        sb.append(",\"modifier\":\"").append(escapeJson(modifier)).append("\"");
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}