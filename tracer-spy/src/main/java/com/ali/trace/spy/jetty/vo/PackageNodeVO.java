package com.ali.trace.spy.jetty.vo;

import java.util.ArrayList;
import java.util.List;

/**
 * Package node for hierarchical package tree statistics
 */
public class PackageNodeVO {
    private String name;
    private String fullPath;
    private int wovenCount;        // type=1 的数量（包含子节点）
    private int totalCount;        // 总数量（包含子节点）
    private boolean isClass;       // true if this is a class node, false if package
    private int type;              // class type (1=woven, 0=not woven)
    private int loaderId;          // classloader id
    private List<PackageNodeVO> children = new ArrayList<PackageNodeVO>();

    public PackageNodeVO() {}

    public PackageNodeVO(String name, String fullPath) {
        this.name = name;
        this.fullPath = fullPath;
        this.isClass = false;
    }

    public boolean isClass() {
        return isClass;
    }

    public void setClass(boolean isClass) {
        this.isClass = isClass;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getLoaderId() {
        return loaderId;
    }

    public void setLoaderId(int loaderId) {
        this.loaderId = loaderId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFullPath() {
        return fullPath;
    }

    public void setFullPath(String fullPath) {
        this.fullPath = fullPath;
    }

    public int getWovenCount() {
        return wovenCount;
    }

    public void setWovenCount(int wovenCount) {
        this.wovenCount = wovenCount;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public List<PackageNodeVO> getChildren() {
        return children;
    }

    public void setChildren(List<PackageNodeVO> children) {
        this.children = children;
    }

    public void addChild(PackageNodeVO child) {
        this.children.add(child);
    }

    public PackageNodeVO getChild(String name) {
        for (PackageNodeVO child : children) {
            if (child.getName().equals(name)) {
                return child;
            }
        }
        return null;
    }

    /**
     * Calculate totals from children (call after all children added)
     */
    public void calculateTotals() {
        if (isClass) {
            // Leaf node: wovenCount and totalCount are set directly
            return;
        }
        // Package node: sum from children
        this.wovenCount = 0;
        this.totalCount = 0;
        for (PackageNodeVO child : children) {
            child.calculateTotals();
            this.wovenCount += child.getWovenCount();
            this.totalCount += child.getTotalCount();
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"name\":\"").append(escapeJson(name)).append("\"");
        sb.append(",\"fullPath\":\"").append(escapeJson(fullPath != null ? fullPath : name)).append("\"");
        sb.append(",\"isClass\":").append(isClass);
        sb.append(",\"wovenCount\":").append(wovenCount);
        sb.append(",\"totalCount\":").append(totalCount);
        if (isClass) {
            sb.append(",\"type\":").append(type);
            sb.append(",\"loaderId\":").append(loaderId);
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