package com.ali.trace.spy.jetty.vo;

import com.ali.trace.spy.jetty.handler.InvokeHandler;
import java.util.List;

/**
 * Value object for weave status response.
 * Has a proper toString() that produces valid JSON (not Java HashMap format).
 * Used by /invoke/weave-status.json endpoint.
 */
public class WeaveStatusVO {
    private String className;           // dot-format class name for display (e.g. "com.example.Service")
    private String methodName;          // original method name (e.g. "foo" or "<init>")
    private String internalClassName;   // JVM internal slash-format class name (e.g. "com/example/Service")
    private String[] paramTypes;        // fully qualified parameter type names
    private List<InvokeHandler.ParamInfo> capturedParams;

    public WeaveStatusVO() {}

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }

    public String getInternalClassName() { return internalClassName; }
    public void setInternalClassName(String internalClassName) { this.internalClassName = internalClassName; }

    public List<InvokeHandler.ParamInfo> getCapturedParams() { return capturedParams; }
    public void setCapturedParams(List<InvokeHandler.ParamInfo> capturedParams) { this.capturedParams = capturedParams; }

    public String[] getParamTypes() { return paramTypes; }
    public void setParamTypes(String[] paramTypes) { this.paramTypes = paramTypes; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        if (className != null) {
            sb.append("\"className\":\"").append(escapeJson(className)).append("\"");
        } else {
            sb.append("\"className\":null");
        }
        if (methodName != null) {
            sb.append(",\"methodName\":\"").append(escapeJson(methodName)).append("\"");
        } else {
            sb.append(",\"methodName\":null");
        }
        if (internalClassName != null) {
            sb.append(",\"internalClassName\":\"").append(escapeJson(internalClassName)).append("\"");
        } else {
            sb.append(",\"internalClassName\":null");
        }
        if (capturedParams != null) {
            sb.append(",\"capturedParams\":[");
            for (int i = 0; i < capturedParams.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(capturedParams.get(i).toString());
            }
            sb.append("]");
        }
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
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
