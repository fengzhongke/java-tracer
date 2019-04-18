package com.ali.trace.inject;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

public class TraceTransformer implements ClassFileTransformer {
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
        ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        boolean transform = false;
        
        if ((loader != null || className.startsWith("java/com/alibaba/jvm/sandbox/spy"))
            && !className.startsWith("com/alibaba/jvm/sandbox/core/manager/impl/SandboxClassFileTransformer")
            && !className.startsWith("com/ali/trace") && !className.startsWith("com/ali/asm")
            && !className.startsWith("com/taobao/pandora/boot/loader/SarLauncher")) {
            // if(loader != null){
            System.out
                .println("### start class:[" + className + "]loader:[" + loader + "]transform:[" + transform + "]");
            try {
                classfileBuffer = new TraceInjecter(classfileBuffer, loader, true).getBytes();
                transform = true;
            } catch (Exception e) {
                // System.err.println("error " + className);
                // e.printStackTrace();

                System.out
                    .println("### end class:[" + className + "]loader:[" + loader + "]transform:[" + transform + "]");
            }
        }
        // System.out.println("class:[" + className + "," + classBeingRedefined + "]loader:[" + loader + "]transform:["
        // + transform + "]");
        return classfileBuffer;
    }
}
