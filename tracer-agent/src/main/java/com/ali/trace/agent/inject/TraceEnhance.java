package com.ali.trace.agent.inject;

import java.lang.reflect.Method;

/**
 * @author nkhanlang@163.com
 */
public class TraceEnhance {

    /**
     * inner interceptor
     */
    private static volatile Interceptor interceptor;

    /**
     * to avoid
     */
    private static final ThreadLocal<Boolean> IN = new ThreadLocal<Boolean>();


    private static Method SET_PARAMS;

    /**
     * set interceptor
     */
    public static boolean setInterceptor(Object instance) {
        boolean set = false;
        if (instance != null) {
            try {
                TraceEnhance.interceptor = new Interceptor(instance);
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

    public static void setParamsMethod(Method method) {
        SET_PARAMS = method;
    }

    public static Interceptor getInterceptor() {
        return interceptor;
    }

    /**
     * inject point before execute method body
     */
    public static final void s(String c, String m) {
        try {
            if (interceptor != null && IN.get() == null) {
                IN.set(true);
                try {
                    interceptor.START.invoke(interceptor.INSTANCE, c, m);
                } finally {
                    IN.set(null);
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    /**
     * set captured method params to interceptor
     * SET_PARAMS is a static method (BaseInterceptor.setParams), so invoke(null, args) is valid.
     * Must not access interceptor.INSTANCE when interceptor is null — that would NPE.
     */
    public static final void setParams(Object[] params) {
        try {
            if (SET_PARAMS != null) {
                // SET_PARAMS is BaseInterceptor.setParams(Object[]) — a static method.
                // For static methods, the obj argument to invoke() is ignored, so null is safe.
                SET_PARAMS.invoke(null, (Object) params);
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
                try {
                    interceptor.END.invoke(interceptor.INSTANCE, c, m);
                } finally {
                    IN.set(null);
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private static class Interceptor {
        final Object INSTANCE;
        final Method START;
        final Method END;

        Interceptor(Object instance) throws Exception {
            INSTANCE = instance;
            Class<?> clazz = instance.getClass();
            START = clazz.getMethod("start", String.class, String.class);
            END = clazz.getMethod("end", String.class, String.class);
        }
    }

}
