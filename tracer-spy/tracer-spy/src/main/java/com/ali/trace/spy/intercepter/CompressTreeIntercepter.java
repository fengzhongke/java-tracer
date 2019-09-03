package com.ali.trace.spy.intercepter;

import com.ali.trace.spy.core.NodePool;
import com.ali.trace.spy.util.BaseNode;
import com.ali.trace.spy.util.CompressNode;

import java.util.Stack;

/**
 * @author nkhanlang@163.com
 */
public class CompressTreeIntercepter extends BaseTreeIntercepter<CompressNode> {

    public CompressTreeIntercepter(String c, String m) {
        super(c, m);
    }

    protected CompressNode getNode(long metaId) {
        return new CompressNode(metaId);
    }
}
