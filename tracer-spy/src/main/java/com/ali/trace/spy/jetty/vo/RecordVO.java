package com.ali.trace.spy.jetty.vo;

/**
 * @author nkhanlang@163.com
 * @date 2019-08-21 00:51
 */
public class RecordVO {
    private MetaVO metaVO;
    private String type;
    private Long seed;
    private String time;
    private long rt;

    public RecordVO(long seed, String type, MetaVO metaVO, String time, long rt){
        this.seed = seed;
        this.type = type;
        this.metaVO = metaVO;
        this.time = time;
        this.rt = rt;
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
