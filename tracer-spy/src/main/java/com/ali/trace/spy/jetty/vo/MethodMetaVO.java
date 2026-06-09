package com.ali.trace.spy.jetty.vo;

/**
 * Method metadata VO — describes a method's signature for the method browser UI.
 * Used by /invoke/methods.json endpoint to help users discover available methods.
 */
public class MethodMetaVO {

    private String name;            // method name
    private String returnType;      // simple name of return type
    private String[] paramTypes;    // simple names of parameter types
    private boolean isStatic;       // whether the method is static
    private String declaringClass;  // simple name of the declaring class

    public MethodMetaVO() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getReturnType() { return returnType; }
    public void setReturnType(String returnType) { this.returnType = returnType; }

    public String[] getParamTypes() { return paramTypes; }
    public void setParamTypes(String[] paramTypes) { this.paramTypes = paramTypes; }

    public boolean isStatic() { return isStatic; }
    public void setStatic(boolean isStatic) { this.isStatic = isStatic; }

    public String getDeclaringClass() { return declaringClass; }
    public void setDeclaringClass(String declaringClass) { this.declaringClass = declaringClass; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"name\":\"").append(escapeJson(name)).append("\"");
        sb.append(",\"returnType\":\"").append(escapeJson(returnType)).append("\"");
        if (paramTypes != null && paramTypes.length > 0) {
            sb.append(",\"paramTypes\":[");
            for (int i = 0; i < paramTypes.length; i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escapeJson(paramTypes[i])).append("\"");
            }
            sb.append("]");
        } else {
            sb.append(",\"paramTypes\":[]");
        }
        sb.append(",\"isStatic\":").append(isStatic);
        sb.append(",\"declaringClass\":\"").append(escapeJson(declaringClass)).append("\"");
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}