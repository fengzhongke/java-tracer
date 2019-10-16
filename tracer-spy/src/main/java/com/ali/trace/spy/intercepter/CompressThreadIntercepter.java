package com.ali.trace.spy.intercepter;

import com.ali.trace.spy.util.CompressNode;

/**
 * @author nkhanlang@163.com
 */
public class CompressThreadIntercepter extends ThreadTreeIntercepter<CompressNode> {

    protected CompressNode getNode(long metaId) {
        return new CompressNode(metaId);
    }
}
