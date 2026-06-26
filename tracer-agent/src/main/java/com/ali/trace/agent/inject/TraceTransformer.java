package com.ali.trace.agent.inject;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;

/**
 * @author nkhanlang@163.com
 */
public class TraceTransformer implements ClassFileTransformer {
    final Object INJECTOR;
    final Method GET_BYTES;

    public TraceTransformer(Object injector) throws SecurityException, NoSuchMethodException {
        INJECTOR = injector;
        GET_BYTES = injector.getClass().getMethod("getBytes", ClassLoader.class, String.class, byte[].class);
    }

    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
        ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        try {
            classfileBuffer = (byte[])GET_BYTES.invoke(INJECTOR, loader, className, classfileBuffer);
        } catch (Throwable e) {
            System.out.println("[tracer-transform] Error transforming " + className + ": " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
        return classfileBuffer;
    }
}
