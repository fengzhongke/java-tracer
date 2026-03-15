package com.ali.trace.spy.interceptor;

import com.ali.trace.spy.util.CommonNode;

/**
 * @author nkhanlang@163.com
 */
public class CommonTreeInterceptor extends MethodTreeInterceptor<CommonNode> {

    public CommonTreeInterceptor(String c, String m) {
        super(c, m);
    }

    protected CommonNode getNode(long metaId) {
        System.out.println("add a new common tree trace with id[" + metaId + "]");
        return new CommonNode(metaId);
    }
}
