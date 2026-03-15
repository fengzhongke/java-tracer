package com.ali.trace.agent.inject;

import java.lang.reflect.Method;

/**
 * @author nkhanlang@163.com
 */
public class TraceEnhance {

    /**
     * inner interceptor
     */
    private static volatile interceptor interceptor;

    /**
     * to avoid 
     */
    private static final ThreadLocal<Boolean> IN = new ThreadLocal<Boolean>();

    /**
     * set interceptor
     */
    public static boolean setInterceptor(Object instance) {
        boolean set = false;
        if (instance != null) {
            try {
                TraceEnhance.interceptor = new interceptor(instance);
                System.out.println("set interceptor[" + instance.getClass() + "]");

                set = true;
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        return set;
    }

    public static void delInterceptor() {
        System.out.println("delete interceptor");
        interceptor = null;
    }
    
    public static interceptor getInterceptor() {
        return interceptor;
    }

    /**
     * inject point before execute method body
     */
    public static final void s(String c, String m) {
        try {
            if (interceptor != null && IN.get() == null) {
                IN.set(true);
                interceptor.START.invoke(interceptor.INSTANCE, c, m);
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
            if (interceptor != null && IN.get() == null) {
                IN.set(true);
                interceptor.END.invoke(interceptor.INSTANCE, c, m);
                IN.set(null);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private static class interceptor {
        final Object INSTANCE;
        final Method START;
        final Method END;

        interceptor(Object instance) throws Exception {
            INSTANCE = instance;
            Class<?> clazz = instance.getClass();
            START = clazz.getMethod("start", String.class, String.class);
            END = clazz.getMethod("end", String.class, String.class);
        }
    }

}
