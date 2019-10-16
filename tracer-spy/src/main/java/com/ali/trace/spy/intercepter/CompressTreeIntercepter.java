package com.ali.trace.spy.intercepter;

import com.ali.trace.spy.util.CompressNode;

/**
 * @author nkhanlang@163.com
 */
public class CompressTreeIntercepter extends MethodTreeIntercepter<CompressNode> {

    public CompressTreeIntercepter(String c, String m) {
        super(c, m);
    }

    protected CompressNode getNode(long metaId) {
        return new CompressNode(metaId);
    }
}
