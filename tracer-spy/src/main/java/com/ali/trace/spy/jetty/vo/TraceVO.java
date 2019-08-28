package com.ali.trace.spy.jetty.vo;

import com.ali.trace.spy.util.BaseNode;
import com.ali.trace.spy.util.CompressNode;

import java.util.Map;

/**
 * @author nkhanlang@163.com
 * @date 2019-08-21 00:51
 */
public class TraceVO {
    private BaseNode node;
    private Map<Long, String[]> metas;

    public TraceVO(BaseNode node, Map<Long, String[]> metas){
        this.node = node;
        this.metas = metas;
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
