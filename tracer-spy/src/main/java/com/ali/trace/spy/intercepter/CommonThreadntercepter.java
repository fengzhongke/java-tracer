package com.ali.trace.spy.intercepter;

import com.ali.trace.spy.util.CommonNode;

/**
 * @author nkhanlang@163.com
 */
public class CommonThreadntercepter extends ThreadTreeIntercepter<CommonNode> {

    protected CommonNode getNode(long metaId) {
        return new CommonNode(metaId);
    }
}
