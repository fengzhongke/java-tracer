package com.ali.trace.agent.inject;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;

public class TraceTransformer implements ClassFileTransformer {
    Object injecter;
    Method getBytes;

    public TraceTransformer(Object injecter) throws SecurityException, NoSuchMethodException {
        this.injecter = injecter;
        this.getBytes = injecter.getClass().getMethod("getBytes", ClassLoader.class, String.class, byte[].class);
    }

    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
        ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        try {
            classfileBuffer = (byte[])getBytes.invoke(injecter, loader, className, classfileBuffer);
        } catch (Throwable e) {
            //e.printStackTrace();
        }
        return classfileBuffer;
    }
}
