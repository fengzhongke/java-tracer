package com.ali.trace.spy.util;

import javax.annotation.Resource;

@Resource
public class TestAsmImport {

    private Long name;// = new String("abc");

    public int getAge(String type) {
        new StringBuilder("abc").append(12);
        System.out.println(name);
        return new Integer(0);
    }

    public int getAge(String type, StringBuilder sb) {
        new StringBuilder("abc").append(12);
        //System.out.println(name);
        return new Integer(0);
    }
    
    

}
