package com.ali.trace.agent.inject;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;

public class TraceTransformer implements ClassFileTransformer {
    final Object INJECTER;
    final Method GET_BYTES;

    public TraceTransformer(Object injecter) throws SecurityException, NoSuchMethodException {
        INJECTER = injecter;
        GET_BYTES = injecter.getClass().getMethod("getBytes", ClassLoader.class, String.class, byte[].class);
    }

    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
        ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        try {
            classfileBuffer = (byte[])GET_BYTES.invoke(INJECTER, loader, className, classfileBuffer);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return classfileBuffer;
    }
}
