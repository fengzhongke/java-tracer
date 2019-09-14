package com.ali.trace.spy.inject;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.Method;

import com.ali.trace.spy.core.ConfigPool;
import com.ali.trace.spy.jetty.JettyServer;

import java.lang.instrument.Instrumentation;

/**
 * trace code inject
 *
 * @author nkhanlang@163.com
 *
 */
public class TraceInjecter {
    private final ClassLoader LOADER;
    private final Type TYPE;
    private final Method START;
    private final Method END;
    private final ConfigPool POOL = ConfigPool.getPool();

    public TraceInjecter(Instrumentation inst, Class<?> clasz, int port) throws NoSuchMethodException, SecurityException {

        LOADER = getClass().getClassLoader();
        TYPE = Type.getType(clasz);
        START = Method.getMethod(clasz.getMethod("s", new Class<?>[] {String.class, String.class}));
        END = Method.getMethod(clasz.getMethod("e", new Class<?>[] {String.class, String.class}));
        POOL.setInst(inst);
        POOL.setWeaveClass(clasz);
        new JettyServer(port);
    }

    public byte[] getBytes(final ClassLoader loader, final String name, byte[] bytes) throws Throwable {
        Integer type = 0;
        try {
            if (name != null) {
                if ((loader != null && loader != LOADER
                    && !name.startsWith("com/alibaba/jvm/sandbox/core/manager/impl/SandboxClassFileTransformer")
                && !name.startsWith("com/google/gson/internal/reflect/ReflectionAccessor"))
                    || (loader == null && name.startsWith("java/com/alibaba/jvm/sandbox/spy"))) {
                    bytes = new CodeReader(loader, name, bytes, POOL.isRedefine(loader, name), false).getBytes();
                    type = 1;
                }
            }
            return bytes;
        } catch (TypeNotPresentException e) {
            type = 3;
            throw e;
        } catch (Throwable t) {
            try{
                bytes = new CodeReader(loader, name, bytes, POOL.isRedefine(loader, name), true).getBytes();
                type = 1;
                return bytes;
            }catch (Throwable t1){
                type = 2;
                System.err.println("class : " + name);
                t1.printStackTrace();
                throw t1;
            }
        } finally {
            if (name != null) {
                POOL.addClass(loader, name, type);
            }
        }
    }

    class CodeReader extends ClassReader {
        private final ClassWriter classWriter;

        public CodeReader(final ClassLoader loader, final String name, byte[] bytes, final boolean redefine, boolean common) {
            super(bytes);
            int flag = ClassWriter.COMPUTE_MAXS;
            if(!common){
                flag = flag | ClassWriter.COMPUTE_FRAMES;
            }
            classWriter = new ClassWriter(flag) {
                @Override
                public ClassLoader getClassLoader() {
                    return loader;
                }

                @Override
                protected String getCommonSuperClass(final String type1, final String type2) {
                    if(!redefine) {
                        if (name.equals(type1)) {
                            throw new TypeNotPresentException(type1,
                                    new Exception("circular define 1:[" + type1 + "," + type2 + "]"));
                        }
                        if (name.equals(type2)) {
                            throw new TypeNotPresentException(type2,
                                    new Exception("circular define 2:[" + type1 + "," + type2 + "]"));
                        }
                    }
                    return super.getCommonSuperClass(type1, type2);
                }
            };
            accept(new CodeVisitor(classWriter, common), EXPAND_FRAMES);
        }
        /**
         * return modified bytes
         */
        public byte[] getBytes() {
            return classWriter.toByteArray();
        }
    }

    /**
     * weave code before and after each method
     *
     * @author hanlang.hl
     *
     */
    class CodeVisitor extends ClassVisitor {
        private String cName;
        private boolean common;

        public CodeVisitor(ClassVisitor cv, boolean common) {
            super(Opcodes.ASM7, cv);
            this.common = common;
        }

        @Override
        public void visit(int paramInt1, int paramInt2, String paramString1, String paramString2, String paramString3,
            String[] paramArrayOfString) {
            cName = paramString1.replace('/', '.').replaceAll("\\$", ".");
            super.visit(paramInt1, paramInt2, paramString1, paramString2, paramString3, paramArrayOfString);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            if ((access & 256) != 0) {
                return super.visitMethod(access, name, desc, signature, exceptions);
            }
            if(common){
                return new CommonAdapter(super.visitMethod(access, name, desc, signature, exceptions), access, name, desc);
            }else {
                return new FinallyAdapter(super.visitMethod(access, name, desc, signature, exceptions), access, name, desc);
            }
        }

        class FinallyAdapter extends AdviceAdapter {
            private String mName;
            private Label startFinally = new Label();
            private Label endFinally = new Label();

            public FinallyAdapter(MethodVisitor methodVisitor, int acc, String name, String desc) {
                super(Opcodes.ASM7, methodVisitor, acc, name, desc);
                this.mName = name.replaceAll("<", "_").replaceAll("\\$|>", "");
            }

            @Override
            protected void onMethodEnter() {
                push(cName);
                push(mName);
                invokeStatic(TYPE, START);
                mark(startFinally);
            }

            @Override
            public void visitMaxs(int maxStack, int maxLocals) {
                mark(endFinally);
                visitTryCatchBlock(startFinally, endFinally, mark(), null);
                onFinally();
                dup();
                throwException();
                super.visitMaxs(maxStack, maxLocals);
            }

            @Override
            protected void onMethodExit(int opcode) {
                if (opcode != ATHROW) {
                    onFinally();
                }
            }

            private void onFinally() {
                push(cName);
                push(mName);
                invokeStatic(TYPE, END);
            }
        }



        class CommonAdapter extends AdviceAdapter {
            private String mName;
            private Label startFinally = new Label();
            private Label endFinally = new Label();

            public CommonAdapter(MethodVisitor methodVisitor, int acc, String name, String desc) {
                super(Opcodes.ASM7, methodVisitor, acc, name, desc);
                this.mName = name.replaceAll("<", "_").replaceAll("\\$|>", "");
            }

            @Override
            protected void onMethodEnter() {
                push(cName);
                push(mName);
                invokeStatic(TYPE, START);
            }

            @Override
            protected void onMethodExit(int opcode) {
                push(cName);
                push(mName);
                invokeStatic(TYPE, END);
            }
        }
    }
}
