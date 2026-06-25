package com.ali.trace.spy.jetty.vo;

/**
 * Stack trace element VO for single thread detail display
 */
public class StackTraceVO {
    private String className;
    private String methodName;
    private int lineNumber;
    private boolean isNativeMethod;

    public StackTraceVO() {}

    public StackTraceVO(StackTraceElement element) {
        this.className = element.getClassName();
        this.methodName = element.getMethodName();
        this.lineNumber = element.getLineNumber();
        this.isNativeMethod = element.isNativeMethod();
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public boolean isNativeMethod() {
        return isNativeMethod;
    }

    public void setNativeMethod(boolean isNativeMethod) {
        this.isNativeMethod = isNativeMethod;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"className\":\"").append(escapeJson(className)).append("\"");
        sb.append(",\"methodName\":\"").append(escapeJson(methodName)).append("\"");
        sb.append(",\"lineNumber\":").append(lineNumber);
        sb.append(",\"isNativeMethod\":").append(isNativeMethod);
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
