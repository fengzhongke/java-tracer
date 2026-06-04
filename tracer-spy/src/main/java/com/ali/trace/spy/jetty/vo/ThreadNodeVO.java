package com.ali.trace.spy.jetty.vo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thread node for hierarchical thread stack tree display
 */
public class ThreadNodeVO {
    private String name;
    private long id;
    private String state;
    private String fullName;
    private String className;
    private int lineNumber;
    private int count;              // count of threads/frames at this node
    private boolean isFrame;        // true if this is a stack frame node
    private boolean isThread;       // true if this is a thread leaf node
    private List<ThreadNodeVO> children = new ArrayList<ThreadNodeVO>();
    private Map<String, ThreadNodeVO> childrenMap = new LinkedHashMap<String, ThreadNodeVO>();

    public ThreadNodeVO() {}

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public void incCount() {
        this.count++;
    }

    public boolean isFrame() {
        return isFrame;
    }

    public void setIsFrame(boolean isFrame) {
        this.isFrame = isFrame;
    }

    public boolean isThread() {
        return isThread;
    }

    public void setIsThread(boolean isThread) {
        this.isThread = isThread;
    }

    public List<ThreadNodeVO> getChildren() {
        return children;
    }

    public void setChildren(List<ThreadNodeVO> children) {
        this.children = children;
    }

    public Map<String, ThreadNodeVO> getChildrenMap() {
        return childrenMap;
    }

    public void addChild(ThreadNodeVO child) {
        this.children.add(child);
    }

    public ThreadNodeVO getChild(String key) {
        return childrenMap.get(key);
    }

    public void addChildByKey(String key, ThreadNodeVO child) {
        childrenMap.put(key, child);
        children.add(child);
    }

    /**
     * Convert children map to list for JSON output
     */
    public void finalizeChildren() {
        children = new ArrayList<ThreadNodeVO>(childrenMap.values());
        for (ThreadNodeVO child : children) {
            child.finalizeChildren();
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"name\":\"").append(escapeJson(name)).append("\"");
        sb.append(",\"id\":").append(id);
        if (state != null) {
            sb.append(",\"state\":\"").append(escapeJson(state)).append("\"");
        }
        sb.append(",\"count\":").append(count);
        sb.append(",\"isFrame\":").append(isFrame);
        sb.append(",\"isThread\":").append(isThread);
        if (isFrame && !isThread) {
            sb.append(",\"fullName\":\"").append(escapeJson(fullName != null ? fullName : name)).append("\"");
            if (className != null) {
                sb.append(",\"className\":\"").append(escapeJson(className)).append("\"");
            }
            sb.append(",\"lineNumber\":").append(lineNumber);
        }
        sb.append(",\"children\":");
        if (children != null && !children.isEmpty()) {
            sb.append("[");
            for (int i = 0; i < children.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(children.get(i).toString());
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
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}