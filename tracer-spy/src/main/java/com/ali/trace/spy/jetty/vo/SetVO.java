package com.ali.trace.spy.jetty.vo;

/**
 * @author nkhanlang@163.com
 * @date 2019-08-21 00:51
 */
public class SetVO {
    private MetaVO metaVO;
    private String type;
    private long size;
    private int mode;

    public SetVO(MetaVO metaVO, String type, long size, int mode){
        this.metaVO = metaVO;
        this.type = type;
        this.size = size;
        this.mode = mode;
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getMode() {
        return mode;
    }

    public void setMode(int mode) {
        this.mode = mode;
    }
}
