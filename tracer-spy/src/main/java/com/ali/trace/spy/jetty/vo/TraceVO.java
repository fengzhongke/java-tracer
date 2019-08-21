package com.ali.trace.spy.jetty.vo;

import com.ali.trace.spy.util.TreeNode;

import java.util.Map;

/**
 * @auther hanlang@mallcai.com
 * @date 2019-08-21 00:51
 */
public class TraceVO {
    private TreeNode node;
    private Map<Long, String[]> metas;

    public TraceVO(TreeNode node, Map<Long, String[]> metas){
        this.node = node;
        this.metas = metas;
    }
    public TreeNode getNode() {
        return node;
    }

    public void setNode(TreeNode node) {
        this.node = node;
    }

    public Map<Long, String[]> getMetas() {
        return metas;
    }

    public void setMetas(Map<Long, String[]> metas) {
        this.metas = metas;
    }
}
