package com.ali.trace.spy.jetty.vo;

import java.util.ArrayList;
import java.util.List;

/**
 * Recursive call tree node for method invocation expressions.
 * Each node represents either a method call (className + methodName) or a literal value (value + valueType).
 * For instance methods, the target field provides the receiver object (itself a method call node).
 *
 * Request fields: className, methodName, paramTypes, target, params, value, valueType
 * Response fields: returnType, returnValue, isVoid, exception, duration
 * Internal (transient, not serialized): resolvedValue — the actual Java Object after evaluation
 */
public class MethodCallNode {

    // --- Request fields ---
    private String className;       // fully qualified class name (e.g. "com.xxx.bbb.Aclass")
    private String methodName;      // method name (e.g. "getCount")
    private String[] paramTypes;    // optional: explicit parameter type names for overload resolution
    private MethodCallNode target;  // optional: instance method receiver (method call that returns the object)
    private List<MethodCallNode> params = new ArrayList<MethodCallNode>(); // recursive: each param is a sub-node or literal

    // --- Literal value fields (used when this node is a literal, not a method call) ---
    private String value;           // literal value string (e.g. "42")
    private String valueType;       // literal type (e.g. "int", "String", "null")

    // --- Call type field ---
    private String callType;        // "method", "field", "classRef", "literal" (default "method")

    // --- Response fields (populated after invocation) ---
    private String returnType;      // return type simple name
    private String returnValue;     // toString of return value, truncated
    private boolean isVoid;         // true if method returns void
    private String exception;       // exception message if invocation failed, null if success
    private long duration;          // execution time in ms
    private int loaderId;           // ClassLoader LoaderSet ID (0 = auto-detect, >0 = specific loader)

    // --- Internal (transient, not serialized) ---
    /** The actual Java Object resolved after evaluation. Used by parent nodes as param/receiver. */
    public transient Object resolvedValue;

    public MethodCallNode() {}

    public boolean isMethodCall() {
        // null callType is treated as default "method" (since "method" is omitted from JSON serialization)
        return (callType == null || "method".equals(callType)) && methodName != null;
    }

    public boolean isFieldAccess() {
        return "field".equals(callType) && methodName != null;
    }

    public boolean isLiteral() {
        return value != null && valueType != null;
    }

    public boolean isClassRef() {
        return className != null && !className.isEmpty()
            && (methodName == null || methodName.isEmpty())
            && !"field".equals(callType);
    }

    // --- Getters and setters ---
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }

    public String[] getParamTypes() { return paramTypes; }
    public void setParamTypes(String[] paramTypes) { this.paramTypes = paramTypes; }

    public MethodCallNode getTarget() { return target; }
    public void setTarget(MethodCallNode target) { this.target = target; }

    public List<MethodCallNode> getParams() { return params; }
    public void setParams(List<MethodCallNode> params) { this.params = params; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getValueType() { return valueType; }
    public void setValueType(String valueType) { this.valueType = valueType; }

    public String getReturnType() { return returnType; }
    public void setReturnType(String returnType) { this.returnType = returnType; }

    public String getReturnValue() { return returnValue; }
    public void setReturnValue(String returnValue) { this.returnValue = returnValue; }

    public boolean isVoid() { return isVoid; }
    public void setVoid(boolean isVoid) { this.isVoid = isVoid; }

    public String getException() { return exception; }
    public void setException(String exception) { this.exception = exception; }

    public long getDuration() { return duration; }
    public void setDuration(long duration) { this.duration = duration; }

    public int getLoaderId() { return loaderId; }
    public void setLoaderId(int loaderId) { this.loaderId = loaderId; }

    public String getCallType() { return callType; }
    public void setCallType(String callType) { this.callType = callType; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");

        // Request fields
        if (className != null) {
            sb.append("\"className\":\"").append(escapeJson(className)).append("\"");
        }
        if (methodName != null) {
            if (sb.length() > 1) sb.append(",");
            sb.append("\"methodName\":\"").append(escapeJson(methodName)).append("\"");
        }
        if (callType != null && !callType.isEmpty() && !"method".equals(callType)) {
            if (sb.length() > 1) sb.append(",");
            sb.append("\"callType\":\"").append(escapeJson(callType)).append("\"");
        }
        if (paramTypes != null && paramTypes.length > 0) {
            if (sb.length() > 1) sb.append(",");
            sb.append("\"paramTypes\":[");
            for (int i = 0; i < paramTypes.length; i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escapeJson(paramTypes[i])).append("\"");
            }
            sb.append("]");
        }
        if (value != null) {
            if (sb.length() > 1) sb.append(",");
            sb.append("\"value\":\"").append(escapeJson(value)).append("\"");
        }
        if (valueType != null) {
            if (sb.length() > 1) sb.append(",");
            sb.append("\"valueType\":\"").append(escapeJson(valueType)).append("\"");
        }

        // Response fields
        if (returnType != null) {
            if (sb.length() > 1) sb.append(",");
            sb.append("\"returnType\":\"").append(escapeJson(returnType)).append("\"");
        }
        if (returnValue != null) {
            if (sb.length() > 1) sb.append(",");
            sb.append("\"returnValue\":\"").append(escapeJson(returnValue)).append("\"");
        }
        if (sb.length() > 1) sb.append(",");
        sb.append("\"isVoid\":").append(isVoid);
        if (exception != null) {
            sb.append(",\"exception\":\"").append(escapeJson(exception)).append("\"");
        }
        sb.append(",\"duration\":").append(duration);
        if (loaderId > 0) {
            sb.append(",\"loaderId\":").append(loaderId);
        }

        // Nested structures
        if (target != null) {
            sb.append(",\"target\":").append(target.toString());
        }
        if (params != null && !params.isEmpty()) {
            sb.append(",\"params\":[");
            for (int i = 0; i < params.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(params.get(i).toString());
            }
            sb.append("]");
        }

        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}