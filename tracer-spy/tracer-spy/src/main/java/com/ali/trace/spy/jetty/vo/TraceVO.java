package com.ali.trace.spy.jetty.vo;

import com.ali.trace.spy.util.BaseNode;
import com.ali.trace.spy.util.CompressNode;

import java.util.Map;

/**
 * @author nkhanlang@163.com
 * @date 2019-08-21 00:51
 */
public class TraceVO {
    private String type;
    private BaseNode node;
    private Map<Long, String[]> metas;

    public TraceVO(String type, BaseNode node, Map<Long, String[]> metas){
        this.type = type;
        this.node = node;
        this.metas = metas;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public BaseNode getNode() {
        return node;
    }

    public void setNode(BaseNode node) {
        this.node = node;
    }

    public Map<Long, String[]> getMetas() {
        return metas;
    }

    public void setMetas(Map<Long, String[]> metas) {
        this.metas = metas;
    }
}
