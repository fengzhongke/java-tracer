package com.ali.trace.spy.jetty.vo;

/**
 * @author nkhanlang@163.com
 * @date 2019-08-21 00:51
 */
public class MetaVO {
    private String cname;
    private String mname;
    public MetaVO(String cname, String mname){
        this.cname = cname;
        this.mname = mname;
    }
    public String getCname() {
        return cname;
    }

    public void setCname(String cname) {
        this.cname = cname;
    }

    public String getMname() {
        return mname;
    }

    public void setMname(String mname) {
        this.mname = mname;
    }
}
