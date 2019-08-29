package com.ali.trace.spy.jetty.vo;

/**
 * @author nkhanlang@163.com
 * @date 2019-08-21 00:51
 */
public class RecordVO {
    private MetaVO metaVO;
    private String type;
    private Long seed;

    public RecordVO(long seed, String type, MetaVO metaVO){
        this.seed = seed;
        this.type = type;
        this.metaVO = metaVO;
    }

    public MetaVO getMetaVO() {
        return metaVO;
    }

    public void setMetaVO(MetaVO metaVO) {
        this.metaVO = metaVO;
    }

    public Long getSeed() {
        return seed;
    }

    public void setSeed(Long seed) {
        this.seed = seed;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
