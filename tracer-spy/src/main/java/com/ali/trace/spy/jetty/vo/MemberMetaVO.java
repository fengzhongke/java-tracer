package com.ali.trace.spy.jetty.vo;

/**
 * Member metadata VO — describes a method or field of a class.
 * Used by /invoke/members.json endpoint to show both methods and fields
 * in the Browse Class modal for the chain UI.
 */
public class MemberMetaVO {

    private String name;            // method name or field name
    private String returnType;     // return type simple name (method return type or field type)
    private String[] paramTypes;   // method parameter type simple names (empty array for fields)
    private boolean isStatic;      // true for static methods/fields
    private boolean isField;       // true=field, false=method
    private String declaringClass; // simple name of the declaring class

    public MemberMetaVO() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getReturnType() { return returnType; }
    public void setReturnType(String returnType) { this.returnType = returnType; }

    public String[] getParamTypes() { return paramTypes; }
    public void setParamTypes(String[] paramTypes) { this.paramTypes = paramTypes; }

    public boolean isStatic() { return isStatic; }
    public void setStatic(boolean isStatic) { this.isStatic = isStatic; }

    public boolean isField() { return isField; }
    public void setField(boolean isField) { this.isField = isField; }

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
        sb.append(",\"isField\":").append(isField);
        sb.append(",\"declaringClass\":\"").append(escapeJson(declaringClass)).append("\"");
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}