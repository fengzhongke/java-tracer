package com.ali.trace.inject;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

public class TraceTransformer implements ClassFileTransformer {
	public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
			ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
		boolean transform = false;
		if (loader != null && loader.getParent() != null) {
			try {
				classfileBuffer = new TraceInjecter(classfileBuffer).getBytes();
				transform = true;
			} catch (Exception e) {
				System.err.print("error " + className);
				e.printStackTrace();
			}
		}
		System.out.println("class:[" + className + "],transform:[" + transform + "]");
		return classfileBuffer;
	}
}
