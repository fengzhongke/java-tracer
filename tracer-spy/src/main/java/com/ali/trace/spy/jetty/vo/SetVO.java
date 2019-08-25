package com.ali.trace.spy.jetty.vo;

/**
 * @author nkhanlang@163.com
 * @date 2019-08-21 00:51
 */
public class SetVO {
    private MetaVO metaVO;
    private long size;

    public SetVO(MetaVO metaVO, long size){
        this.metaVO = metaVO;
        this.size = size;
    }
    public MetaVO getMetaVO() {
        return metaVO;
    }

    public void setMetaVO(MetaVO metaVO) {
        this.metaVO = metaVO;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }
}
