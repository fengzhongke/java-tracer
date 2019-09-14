package com.ali.dbtech.test.log;

/**
 * @auther hanlang
 * @date 2019-09-07 16:24
 */
public class LogClient {

    public static void main(String[] args){
        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger("name");
        logger.info("test");

    }
}
