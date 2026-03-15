package com.ali.trace.spy.interceptor;

import com.ali.trace.spy.util.CompressNode;

/**
 * @author nkhanlang@163.com
 */
public class CompressTreeInterceptor extends MethodTreeInterceptor<CompressNode> {

    public CompressTreeInterceptor(String c, String m) {
        super(c, m);
    }

    protected CompressNode getNode(long metaId) {
        System.out.println("add a new compress tree trace with id[" + metaId + "]");
        return new CompressNode(metaId);
    }
}
