package com.ali.trace.agent.inject;

import java.lang.reflect.Method;

public class TraceEnhance {

    /**
     * inner intercepter
     */
    private static volatile Intercepter intercepter;

    /**
     * to avoid 
     */
    private static final ThreadLocal<Boolean> IN = new ThreadLocal<Boolean>();

    /**
     * set intercepter
     */
    public static boolean setIntecepter(Object instance) {
        boolean set = false;
        if (instance != null) {
            try {
                TraceEnhance.intercepter = new Intercepter(instance);
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
            if (intercepter != null && IN.get() == null) {
                IN.set(true);
                intercepter.START.invoke(intercepter.INSTANCE, c, m);
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
            if (intercepter != null && IN.get() == null) {
                IN.set(true);
                intercepter.END.invoke(intercepter.INSTANCE, c, m);
                IN.set(null);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private static class Intercepter {
        final Object INSTANCE;
        final Method START;
        final Method END;

        Intercepter(Object instance) throws Exception {
            INSTANCE = instance;
            Class<?> clazz = instance.getClass();
            START = clazz.getMethod("start", String.class, String.class);
            END = clazz.getMethod("end", String.class, String.class);
        }
    }

}
