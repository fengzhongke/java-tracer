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
    private String callType;        // "method", "field", "classRef", "literal", "arrayGet" (default "method")

    // --- Array/List index access field (used when callType is "arrayGet") ---
    private int index = -1;         // array/list index (e.g. 0, 1, 2...); -1 means not set

    // --- Array/Collection length field (response, set when result is array or Collection) ---
    private int arrayLength = -1;   // length of array or Collection result (-1 = not an array/collection, 0+ = actual length)

    // --- Response fields (populated after invocation) ---
    private String returnType;      // return type simple name
    private String realReturnType;  // return value's actual runtime class full name (e.g. "com.xxx.AuthService$TestImpl")
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

    public boolean isArrayGet() {
        return "arrayGet".equals(callType) && index >= 0;
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

    public String getRealReturnType() { return realReturnType; }
    public void setRealReturnType(String realReturnType) { this.realReturnType = realReturnType; }

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

    public int getIndex() { return index; }
    public void setIndex(int index) { this.index = index; }

    public int getArrayLength() { return arrayLength; }
    public void setArrayLength(int arrayLength) { this.arrayLength = arrayLength; }


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
        if (realReturnType != null && !realReturnType.isEmpty()) {
            if (sb.length() > 1) sb.append(",");
            sb.append("\"realReturnType\":\"").append(escapeJson(realReturnType)).append("\"");
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
        if (index >= 0) {
            sb.append(",\"index\":").append(index);
        }
        if (arrayLength >= 0) {
            if (sb.length() > 1) sb.append(",");
            sb.append("\"arrayLength\":").append(arrayLength);
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

    /**
     * Flatten the target-linked tree into a sequential chain list.
     * Walks the target chain from innermost (deepest target) to outermost (root),
     * producing [innermost, ..., root] order matching the frontend chainSteps[] model.
     * Each node in the list has its target field cleared (the relationship is implicit by position).
     */
    public List<MethodCallNode> flattenChain() {
        List<MethodCallNode> chain = new ArrayList<MethodCallNode>();
        // Collect innermost first by recursing into targets
        if (target != null) {
            chain.addAll(target.flattenChain());
        }
        // Add this node (clear target since it's implicit in the flat format)
        MethodCallNode copy = shallowCopy();
        copy.target = null;  // relationship is implicit by position in the chain array
        chain.add(copy);
        return chain;
    }

    /**
     * Create a shallow copy of this node (does NOT copy target or params recursively).
     * Used by flattenChain() to produce nodes without target references.
     */
    private MethodCallNode shallowCopy() {
        MethodCallNode copy = new MethodCallNode();
        copy.className = this.className;
        copy.methodName = this.methodName;
        copy.paramTypes = this.paramTypes;
        copy.value = this.value;
        copy.valueType = this.valueType;
        copy.callType = this.callType;
        copy.index = this.index;
        copy.arrayLength = this.arrayLength;
        copy.loaderId = this.loaderId;
        // Response fields
        copy.returnType = this.returnType;
        copy.realReturnType = this.realReturnType;
        copy.returnValue = this.returnValue;
        copy.isVoid = this.isVoid;
        copy.exception = this.exception;
        copy.duration = this.duration;
        // Copy params list (each param may be a literal, sub-chain, etc.)
        if (this.params != null) {
            copy.params = new ArrayList<MethodCallNode>(this.params);
        }
        return copy;
    }

    /**
     * Produce a flat chain JSON string: { "chain": [node0, node1, node2, ...] }
     * where node0 is the innermost (deepest target) and the last node is the outermost (root).
     * This format matches the frontend chainSteps[] display order.
     */
    public String toChainString() {
        List<MethodCallNode> chain = flattenChain();
        StringBuilder sb = new StringBuilder();
        sb.append("{\"chain\":[");
        for (int i = 0; i < chain.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(chain.get(i).toString());
        }
        sb.append("]}");
        return sb.toString();
    }
}