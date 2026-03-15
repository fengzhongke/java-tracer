package com.ali.trace.spy.interceptor;

import com.ali.trace.spy.util.CommonNode;

/**
 * @author nkhanlang@163.com
 */
public class CommonThreadntercepter extends ThreadTreeInterceptor<CommonNode> {

    protected CommonNode getNode(long metaId) {
        return new CommonNode(metaId);
    }
}
