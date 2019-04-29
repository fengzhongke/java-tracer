package com.ali.dbtech.test;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import com.ali.dbtech.test.AsmTest.MyClassVisitor.MethodInfo;

public class AsmTest {

    public static void main(String[] args) throws FileNotFoundException, IOException {
        MyClassVisitor visitor = new MyClassVisitor(AsmTest.class.getName());
        for (MethodInfo method : visitor.getMethods()) {
            System.out.println("Title: invokes of " + method.getClasz() + ":" + method.getMethod());
            for (MethodInfo invoke : method.getInvoke()) {
                if (!invoke.getClasz().startsWith("java.")) {
                    System.out.println(method.getClasz() + "->" + invoke.getClasz() + ":" + invoke.getMethod());
                }
            }
            System.out.println();
        }

    }

    /**
     * 类的所有方法存在methods里面
     */
    public static class MyClassVisitor extends ClassVisitor {
        private List<MethodInfo> methods = new ArrayList<MethodInfo>();
        private String cname;

        public MyClassVisitor(String className) throws IOException {
            super(Opcodes.ASM7, new ClassWriter(ClassReader.SKIP_DEBUG));
            ClassReader cr = new ClassReader(className);
            cr.accept(this, ClassReader.SKIP_DEBUG);
        }

        public List<MethodInfo> getMethods() {
            return methods;
        }

        public void visit(final int version, final int access, final String cname, final String signature,
            final String superName, final String[] interfaces) {
            this.cname = cname;
            super.visit(version, access, cname, signature, superName, interfaces);
        }

        public MethodVisitor visitMethod(final int access, final String mname, final String descriptor,
            final String signature, final String[] exceptions) {
            return new MyMethodVisitor(mname, descriptor,
                cv.visitMethod(access, mname, descriptor, signature, exceptions));
        }

        private class MyMethodVisitor extends MethodVisitor {
            private MethodInfo method;

            public MyMethodVisitor(String mname, String descriptor, MethodVisitor mv) {
                super(Opcodes.ASM7, mv);
                method = new MethodInfo(cname, mname, descriptor);
                methods.add(method);
            }

            public void visitMethodInsn(final int opcode, final String invokeCname, final String invokeMname,
                final String InvokeDescriptor, final boolean isInterface) {
                method.addInvoke(new MethodInfo(invokeCname, invokeMname, InvokeDescriptor));
                super.visitMethodInsn(opcode, invokeCname, invokeMname, InvokeDescriptor, isInterface);
            }
        }

        /**
         * 存储类名，方法名，方法描述，及方法内部调用过的方法
         */
        public class MethodInfo {
            String cname;
            String mname;
            String descripter;
            List<MethodInfo> invokeInfo = new ArrayList<MethodInfo>();

            public MethodInfo(String cname, String mname, String descripter) {
                this.cname = cname;
                this.mname = mname;
                this.descripter = descripter;
            }

            public void addInvoke(MethodInfo invoke) {
                invokeInfo.add(invoke);
            }

            public List<MethodInfo> getInvoke() {
                return invokeInfo;
            }

            public String getClasz() {
                return Type.getType("L" + cname + ";").getClassName();
            }

            public String getMethod() {
                Type retType = Type.getReturnType(descripter);
                Type[] argTypes = Type.getArgumentTypes(descripter);
                StringBuilder sb = new StringBuilder();
                String split = "";
                for (Type argType : argTypes) {
                    sb.append(split).append(argType.getClassName());
                    split = ",";
                }
                return mname + "#(" + sb + ")" + retType.getClassName();
            }
        }

    }
}
