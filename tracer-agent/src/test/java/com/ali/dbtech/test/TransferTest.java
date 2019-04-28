package com.ali.dbtech.test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.Method;

import com.ali.trace.spy.util.NameUtils;

public class TransferTest {

    public static void main(String[] args) throws IOException {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = DynamicCode.class.getResourceAsStream("DynamicCode.class");
            out = new FileOutputStream("/tmp/com/ali/dbtech/test/DynamicCode.class");

            byte[] bytes = new byte[in.available()];
            in.read(bytes);
            bytes = new TraceInjecter(bytes).getBytes();
            out.write(bytes);
        } finally {
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.close();
            }
        }

    }

    public static void s(String c, String m) {
        System.out.println("start [" + c + "." + m + "]");
    }

    public static void e(String c, String m) {
        System.out.println("end [" + c + "." + m + "]");
    }

    static class TraceInjecter extends ClassReader {

        private ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        private Class<?> clasz = TransferTest.class;
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

        public TraceInjecter(byte[] classfileBuffer) {
            super(classfileBuffer);
            accept(new CodeVisitor(classWriter), EXPAND_FRAMES);
        }

        public byte[] getBytes() {
            return classWriter.toByteArray();
        }

        class CodeVisitor extends ClassVisitor {
            private String cName;

            public CodeVisitor(ClassVisitor cv) {
                super(Opcodes.ASM5, cv);
            }

            @Override
            public void visit(int paramInt1, int paramInt2, String paramString1, String paramString2,
                String paramString3, String[] paramArrayOfString) {
                cName = NameUtils.getClassName(paramString1);
                super.visit(paramInt1, paramInt2, paramString1, paramString2, paramString3, paramArrayOfString);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                String[] exceptions) {
                if ((access & 256) != 0) {
                    return super.visitMethod(access, name, desc, signature, exceptions);
                }
                return new FinallyAdapter(super.visitMethod(access, name, desc, signature, exceptions),

                    access, name, desc);
            }

            class FinallyAdapter extends AdviceAdapter {
                private String name;
                private Label startFinally = new Label();
                private Label endFinally = new Label();

                public FinallyAdapter(MethodVisitor mv, int acc, String name, String desc) {
                    super(Opcodes.ASM5, mv, acc, name, desc);
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
                    // maxLocals = maxLocals > 2 ? maxLocals : 2;
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

}
