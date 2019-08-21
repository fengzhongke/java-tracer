package com.ali.trace.spy.jetty.vo;

/**
 * @auther hanlang@mallcai.com
 * @date 2019-08-21 00:51
 */
public class RecordVO {
    private MetaVO metaVO;
    private Long seed;

    public RecordVO(long seed, MetaVO metaVO){
        this.seed = seed;
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
}
