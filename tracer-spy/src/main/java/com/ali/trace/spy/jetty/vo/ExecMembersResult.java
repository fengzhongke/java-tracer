package com.ali.trace.spy.jetty.vo;

import java.util.List;

/**
 * Combined result for /invoke/execMembers.json endpoint.
 * Contains the invoke result, runtime class name, and member list
 * from the actual runtime type (resolvedValue.getClass()).
 * Manual JSON serialization (no Gson dependency).
 */
public class ExecMembersResult {

    private MethodCallNode invokeResult;
    private String runtimeClassName;
    private List<MemberMetaVO> members;

    public ExecMembersResult() {}

    public MethodCallNode getInvokeResult() { return invokeResult; }
    public void setInvokeResult(MethodCallNode invokeResult) { this.invokeResult = invokeResult; }

    public String getRuntimeClassName() { return runtimeClassName; }
    public void setRuntimeClassName(String runtimeClassName) { this.runtimeClassName = runtimeClassName; }

    public List<MemberMetaVO> getMembers() { return members; }
    public void setMembers(List<MemberMetaVO> members) { this.members = members; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"invokeResult\":").append(invokeResult != null ? invokeResult.toString() : "null");
        sb.append(",\"runtimeClassName\":\"").append(escapeJson(runtimeClassName)).append("\"");
        sb.append(",\"members\":");
        if (members != null && !members.isEmpty()) {
            sb.append("[");
            for (int i = 0; i < members.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(members.get(i).toString());
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
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}