package com.ali.trace.inject;

import java.util.HashMap;
import java.util.Map;

import com.ali.asm.ClassReader;
import com.ali.asm.ClassVisitor;
import com.ali.asm.ClassWriter;
import com.ali.asm.Label;
import com.ali.asm.MethodVisitor;
import com.ali.asm.Opcodes;
import com.ali.asm.Type;
import com.ali.asm.commons.AdviceAdapter;
import com.ali.asm.commons.Method;
import com.ali.trace.util.NameUtils;

public class TraceInjecter extends ClassReader {

    private Map<String, Map<String, String>> commonSuperMap = new HashMap<String, Map<String, String>>();
    private ClassWriter classWriter;
    private Class<?> clasz = TraceEnhance.class;
    private Type type;
    private Method start;
    private Method end;
    {
        {
            type = Type.getType(clasz);
            try {
                start = Method.getMethod(clasz.getMethod("s", new Class<?>[] {String.class, String.class}));
                end = Method.getMethod(clasz.getMethod("e", new Class<?>[] {String.class, String.class}));
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    public TraceInjecter(byte[] classfileBuffer, final ClassLoader loader, boolean weave) {
        super(classfileBuffer);
        classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
//             @Override
//             protected ClassLoader getClassLoader() {
//             return loader;
//             }

            protected String getCommonSuperClass(final String type1, final String type2) {
                String type = null;
                try {
                    type = super.getCommonSuperClass(type1, type2);
                    return type;
                }finally {
                    System.out.println("getCommonSuperClass : " + type1 + "," + type2 + "," + type);
                }
            }

            private String get(String type1, String type2) {
                ClassLoader classLoader = getClassLoader();
                Class<?> class1;
                try {
                    class1 = Class.forName(type1.replace('/', '.'), false, classLoader);
                } catch (ClassNotFoundException e) {
                    throw new TypeNotPresentException(type1, e);
                }
                Class<?> class2;
                try {
                    class2 = Class.forName(type2.replace('/', '.'), false, classLoader);
                } catch (ClassNotFoundException e) {
                    throw new TypeNotPresentException(type2, e);
                }
                if (class1.isAssignableFrom(class2)) {
                    return type1;
                }
                if (class2.isAssignableFrom(class1)) {
                    return type2;
                }
                if (class1.isInterface() || class2.isInterface()) {
                    return "java/lang/Object";
                } else {
                    do {
                        class1 = class1.getSuperclass();
                    } while (!class1.isAssignableFrom(class2));
                    return class1.getName().replace('.', '/');
                }
            }
        };
        if (weave) {
            accept(new CodeVisitor(classWriter), EXPAND_FRAMES);
        } else {
            accept(new ClassVisitor(Opcodes.ASM7, classWriter){}, EXPAND_FRAMES);
        }
    }

    public byte[] getBytes() {
        return classWriter.toByteArray();
    }

    class CodeVisitor extends ClassVisitor {
        private String cName;

        public CodeVisitor(ClassVisitor cv) {
            super(Opcodes.ASM7, cv);
        }

        @Override
        public void visit(int paramInt1, int paramInt2, String paramString1, String paramString2, String paramString3,
            String[] paramArrayOfString) {
            cName = NameUtils.getClassName(paramString1);
            super.visit(paramInt1, paramInt2, paramString1, paramString2, paramString3, paramArrayOfString);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            if ((access & 256) != 0) {
                return super.visitMethod(access, name, desc, signature, exceptions);
            }
            return new FinallyAdapter(super.visitMethod(access, name, desc, signature, exceptions), access, name, desc);
        }

        class FinallyAdapter extends AdviceAdapter {
            private String name;
            private Label startFinally = new Label();
            private Label endFinally = new Label();

            public FinallyAdapter(MethodVisitor methodVisitor, int acc, String name, String desc) {
                super(Opcodes.ASM7, methodVisitor, acc, name, desc);
                this.name = NameUtils.getMethodName(name);
            }

            @Override
            protected void onMethodEnter() {
                push(cName);
                push(name);
                invokeStatic(type, start);
                mark(startFinally);
            }

            public void visitMaxs(int maxStack, int maxLocals) {
                mark(endFinally);
                visitTryCatchBlock(startFinally, endFinally, mark(), null);
                onFinally(ATHROW);
                dup();
                throwException();
                super.visitMaxs(maxStack, maxLocals);
            }

            protected void onMethodExit(int opcode) {
                if (opcode != ATHROW) {
                    onFinally(opcode);
                }
            }

            private void onFinally(int opcode) {
                push(cName);
                push(name);
                invokeStatic(type, end);
            }
        }
    }
}
