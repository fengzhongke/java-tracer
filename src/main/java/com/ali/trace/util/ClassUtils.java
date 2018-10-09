package com.ali.trace.util;

import java.lang.reflect.Method;

import com.ali.asm.util.ASMifier;

public class ClassUtils {

    public static void main(String[] args) throws Exception {
    	
    	Method[] methods = ClassUtils.class.getMethods();
        //ASMifier.main(new String[]{"/u01/eclipse/wdk-backend/wdkloc/loc-order/target/classes/com/taobao/loc/order/serivce/InvoiceServiceImpl.class"});
        ASMifier.main(new String[]{TestAsmImport.class.getName()});
        
    }

}
