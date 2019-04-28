package com.ali.trace.agent.inject;

import java.lang.reflect.Method;

public class TraceEnhance {

    /**
     * inner intercepter
     */
    private static volatile Intercepter intercepter;
    
    private static final ThreadLocal<Boolean> IN = new ThreadLocal<Boolean>();

    /**
     * set intercepter
     */
    public static boolean setIntecepter(Object instance) {
        boolean set = false;
        if (instance != null) {
            try {
                Intercepter intercepter = new Intercepter();
                intercepter.instance = instance;
                intercepter.start = instance.getClass().getMethod("start", String.class, String.class);
                intercepter.end = instance.getClass().getMethod("end", String.class, String.class);
                TraceEnhance.intercepter = intercepter;
                set = true;
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        return set;
    }

    public static void delIntercepter() {
        intercepter = null;
    }

    /**
     * inject point before execute method body
     */
    public static final void s(String c, String m) {
        try {
            if (IN.get() == null && intercepter != null) {
                IN.set(true);
                intercepter.start.invoke(intercepter.instance, c, m);
                IN.set(null);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    /**
     * inject point after execute method body
     */
    public static final void e(String c, String m) {
        try {
            if (IN.get() == null && intercepter != null) {
                IN.set(true);
                intercepter.end.invoke(intercepter.instance, c, m);
                IN.set(null);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    static class Intercepter {
        Object instance;
        Method start;
        Method end;
    }

}
