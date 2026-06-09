package com.ali.trace.spy.jetty.vo;

/**
 * Result of a method invocation via the /class/invoke.json endpoint.
 */
public class MethodResultVO {
    private String returnType;    // return type simple name (e.g. "String", "void")
    private String returnValue;   // toString of return value, "void" for void methods, "null" for null returns
    private boolean isVoid;       // true if method returns void
    private String exception;     // exception message if thrown, null if success
    private long duration;        // execution time in ms

    public MethodResultVO() {}

    public String getReturnType() {
        return returnType;
    }

    public void setReturnType(String returnType) {
        this.returnType = returnType;
    }

    public String getReturnValue() {
        return returnValue;
    }

    public void setReturnValue(String returnValue) {
        this.returnValue = returnValue;
    }

    public boolean isVoid() {
        return isVoid;
    }

    public void setVoid(boolean isVoid) {
        this.isVoid = isVoid;
    }

    public String getException() {
        return exception;
    }

    public void setException(String exception) {
        this.exception = exception;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"returnType\":\"").append(escapeJson(returnType)).append("\"");
        sb.append(",\"returnValue\":\"").append(escapeJson(returnValue)).append("\"");
        sb.append(",\"isVoid\":").append(isVoid);
        if (exception != null) {
            sb.append(",\"exception\":\"").append(escapeJson(exception)).append("\"");
        }
        sb.append(",\"duration\":").append(duration);
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}