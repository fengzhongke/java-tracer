package com.ali.trace.spy.intercepter;

import com.ali.trace.spy.util.CommonNode;

/**
 * @author nkhanlang@163.com
 */
public class CommonTreeIntercepter extends BaseTreeIntercepter<CommonNode>{

    public CommonTreeIntercepter(String c, String m) {
        super(c, m);
    }

    protected CommonNode getNode(long metaId) {
        return new CommonNode(metaId);
    }
}
