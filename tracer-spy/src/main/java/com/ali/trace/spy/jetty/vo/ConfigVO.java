package com.ali.trace.spy.jetty.vo;

import org.apache.commons.lang.StringUtils;

/**
 * @author nkhanlang@163.com
 * @date 2019-08-21 00:51
 */
public class ConfigVO {
    private String include;
    private String exclude;
    public ConfigVO(String include, String exclude){
        this.include = include;
        this.exclude = exclude;
    }

    public String getInclude() {
        return include;
    }

    public void setInclude(String include) {
        this.include = include;
    }

    public String getExclude() {
        return exclude;
    }

    public void setExclude(String exclude) {
        this.exclude = exclude;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        String splliter = "";
        if (StringUtils.isNotBlank(include)) {
            sb.append(splliter).append("\"include\":\"").append(include).append("\"");
            splliter = ",";
        }
        if (StringUtils.isNotBlank(exclude)) {
            sb.append(splliter).append("\"exclude\":\"").append(exclude).append("\"");
            splliter = ",";
        }
        sb.append("}");
        return sb.toString();
    }
}
