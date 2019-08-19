package com.ali.trace.agent.main;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import com.ali.trace.agent.inject.TraceEnhance;
import com.ali.trace.agent.inject.TraceTransformer;
import com.ali.trace.agent.loader.SpyClassLoader;

/**
 * 
 * @author hanlang.hl
 *
 */
public class Premain {

    private static final String PATH = "/META-INF/lib";
    private static final String SPY_CLASS = "com.ali.trace.spy.inject.TraceInjecter";
    // default port opened by JETTY
    private static final int DEFAULT_PORT = 18902;
    private static final String DEFAULT_CONFIG = "";

    private static final String CONFIG_PORT="port";
    private static final String CONFIG_SLEEP="sleep";


    private static final SpyClassLoader LOADER = new SpyClassLoader(ClassLoader.getSystemClassLoader());
    private static final AtomicReference<Object> INJECT = new AtomicReference<Object>();

    public static void premain(String args, Instrumentation inst) {
        Map<String, String> configs = new HashMap<String, String>();

        int port = DEFAULT_PORT;
        long sleep = 1L;
        if (args != null) {
            String[] pairs = args.split(":");

            if(pairs != null){
                for(String pair : pairs){
                    int idx  = pair.indexOf("=");
                    if(idx > 0){
                        configs.put(pair.substring(0, idx), pair.substring(idx+1));
                    }else{
                        configs.put(pair, DEFAULT_CONFIG);
                    }
                }
            }
            String portStr = configs.get(CONFIG_PORT);
            if(portStr != null && portStr != DEFAULT_CONFIG){
                port = Integer.valueOf(args);
            }
            String sleepStr = configs.get(CONFIG_SLEEP);
            if(sleepStr != null){
                sleep = Long.parseLong(sleepStr);
            }
        }
        System.out.println("init trace agent with port [" + port + "] and sleep [" + sleep + "]");
        Object inject = loadSpyJar(port);
        try {
            if (inject == null) {
                throw new Exception("inject is null");
            }
            Thread.sleep(sleep);
            inst.addTransformer(new TraceTransformer(inject));
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    /**
     * agent loaded by bootstrap loader, spy and all dependent jars loaded by spy loader
     */
    private static Object loadSpyJar(int port) {
        Object inject = null;
        if ((inject = INJECT.get()) == null) {
            synchronized (Premain.class) {
                if ((inject = INJECT.get()) == null) {
                    String path = Premain.class.getResource(PATH).getPath();
                    if (path.endsWith(PATH)) {
                        path = path.substring(0, path.length() - PATH.length() - 1);
                    }
                    try {
                        JarInputStream jarInput = null;
                        try {
                            jarInput = new JarInputStream(new URL(path).openStream());
                            JarEntry entry = null;
                            while ((entry = jarInput.getNextJarEntry()) != null) {
                                String entryName = "/" + entry.getName();
                                if (entryName.startsWith(PATH) && entryName.endsWith(".jar")) {
                                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                                    int chunk = 0;
                                    byte[] data = new byte[256];
                                    while (-1 != (chunk = jarInput.read(data))) {
                                        bytes.write(data, 0, chunk);
                                    }
                                    data = bytes.toByteArray();
                                    LOADER.load(new ByteArrayInputStream(data, 0, data.length));
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            if (jarInput != null) {
                                try {
                                    jarInput.close();
                                } catch (IOException e1) {
                                    e1.printStackTrace();
                                }
                            }
                        }
                        Class<?> injectClass = LOADER.loadClass(SPY_CLASS);
                        INJECT.set(inject =
                            injectClass.getConstructor(Class.class, int.class).newInstance(TraceEnhance.class, port));
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
            }
        }
        return inject;
    }

    private static Set<String> CANT_TRANSFORM = new HashSet<String>();

    public static void agentmain(String args, Instrumentation inst) {
        CoreEngine.process(args, inst);
        Class<?>[] classes = inst.getAllLoadedClasses();
        for (Class<?> clasz : classes) {
            String name = clasz.getName();
            try {
                if (clasz.getClassLoader() != null && clasz.getClassLoader().getParent() != null
                    && !CANT_TRANSFORM.contains(name)) {
                    inst.retransformClasses(new Class<?>[] {clasz});
                }
            } catch (Throwable t) {
                CANT_TRANSFORM.add(name);
                // t.printStackTrace();
            }
        }
    }

}
