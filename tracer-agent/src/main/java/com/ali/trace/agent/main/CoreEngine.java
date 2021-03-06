package com.ali.trace.agent.main;

import com.ali.trace.agent.inject.TraceEnhance;
import com.ali.trace.spy.intercepter.CommonIntercepter;
import com.ali.trace.spy.intercepter.CompressIntercepter;
import com.ali.trace.spy.intercepter.CompressTreeIntercepter;

import java.lang.instrument.Instrumentation;
import java.util.HashMap;
import java.util.Map;
/**
 * @author nkhanlang@163.com
 */
public class CoreEngine {

    private static String INTERCEPTER = "intercepter";
    private static String CLASS = "class";
    private static String METHOD = "method";
    private static String PATH = "path";
    private static String ONLINE = "online";

    //private static TraceTransformer transformer = new TraceTransformer();

    public static void process(String args, Instrumentation inst) {
        String path = null;
        String intercepter = null;
        Map<String, String> map = new HashMap<String, String>();
        if (args != null) {
            String[] items = args.split("#");
            for (String item : items) {
                String[] keyVal = item.split(":");
                if (keyVal.length == 2) {
                    map.put(keyVal[0], keyVal[1]);
                }
            }
        }
        intercepter = map.get(INTERCEPTER);
        path = map.get(PATH);
        if ("compress".equalsIgnoreCase(intercepter)) {
            TraceEnhance.setIntecepter(new CompressIntercepter(path));
        } else if ("stastic".equals(intercepter)) {
            String clasz = map.get(CLASS);
            String method = map.get(METHOD);
            System.out.println("class:[" + clasz + "]method:[" + method + "]");
            TraceEnhance.setIntecepter(new CompressTreeIntercepter(clasz, method));
        } else if ("thread".equals(intercepter)) {
            String clasz = map.get(CLASS);
            String method = map.get(METHOD);
            System.out.println("class:[" + clasz + "]method:[" + method + "]");
            TraceEnhance.setIntecepter(new CompressTreeIntercepter(clasz, method));
        } else {
            boolean printTime = Boolean.valueOf(args);
            TraceEnhance.setIntecepter(new CommonIntercepter(path, printTime));
        }
        String online = map.get(ONLINE);
        System.out.println("map:" + map);
        if (Boolean.valueOf(online)) {
            System.out.println("online transformer");
            //inst.addTransformer(transformer, true);
        } else {
            System.out.println("offline transformer");
            //inst.removeTransformer(transformer);
        }
    }

}
