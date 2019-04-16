package com.ali.trace.main;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

// import ;

public class AttackEngine {

    public static void main(String[] args) throws Exception {
        String pid = null;
        String tool = null;
        String agent = null;
        String params = null;
        if(args.length > 0){
            pid = args[0];
        }
        if(args.length > 1){
            agent = args[1];
        }
        if(args.length > 2){
            tool = args[2];
        }
        if(args.length > 3){
            params = args[3];
        }
        if(pid == null){
            System.err.println("please input pid agent tool params");
            System.exit(0);
        }
        if(agent == null){
            agent = "/u01/project/java-tracer/target/java-tracer.jar";
        }
        if(tool == null){
            tool = "/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Classes/classes.jar";
        }
        tool = "file:" + tool;
        if(params == null){
            params = "intercepter:thread&class:com.hema.sre.pool.service.SqlService&method:exec&online:true";
        }
        
        Class<?> clasz = null;
        Method detach = null;
        Object vm = null;
        try {
            Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            method.setAccessible(true);
            method.invoke(AttackEngine.class.getClassLoader(), new URL(tool));
             ClassLoader loader = AttackEngine.class.getClassLoader();
            clasz = loader.loadClass("com.sun.tools.attach.VirtualMachine");
            Method attach = clasz.getMethod("attach", String.class);
            Method loadAgent = clasz.getMethod("loadAgent", String.class, String.class);
            detach = clasz.getMethod("detach");

            vm = attach.invoke(null, pid);
            loadAgent.invoke(vm, agent,
                params);
        } finally {
            if (vm != null && detach != null) {
                detach.invoke(vm);
            }
        }
    }

}
