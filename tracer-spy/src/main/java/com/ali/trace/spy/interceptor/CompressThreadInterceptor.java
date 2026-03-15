package com.ali.trace.spy.interceptor;

import com.ali.trace.spy.util.CompressNode;

/**
 * @author nkhanlang@163.com
 */
public class CompressThreadInterceptor extends ThreadTreeInterceptor<CompressNode> {

    protected CompressNode getNode(long metaId) {
        return new CompressNode(metaId);
    }
}
