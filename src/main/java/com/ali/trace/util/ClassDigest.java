package com.ali.trace.util;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.ali.asm.AnnotationVisitor;
import com.ali.asm.ClassReader;
import com.ali.asm.ClassVisitor;
import com.ali.asm.ClassWriter;
import com.ali.asm.FieldVisitor;
import com.ali.asm.Handle;
import com.ali.asm.Label;
import com.ali.asm.MethodVisitor;
import com.ali.asm.Opcodes;

public class ClassDigest extends ClassVisitor {

    private Set<String> imports = new HashSet<String>();

    public String[] getImports() {
        return imports.toArray(new String[0]);
    }

    public ClassDigest(ClassVisitor cv) {
        super(Opcodes.ASM5, cv);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature,
                                   Object value) {
        imports.add(desc);
        return super.visitField(access, name, desc, signature, value);
    }

    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        imports.add(desc);
        return super.visitAnnotation(desc, visible);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName,
                      String[] interfaces) {
        imports.add(superName);
        Collections.addAll(imports, interfaces);
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                                     String[] exceptions) {

        imports.add(desc);
        return new ModifyMethod(super.visitMethod(access, name, desc, signature, exceptions),
            access, name, desc);
    }

    class ModifyMethod extends MethodVisitor implements Opcodes {
        public ModifyMethod(MethodVisitor mv, int access, String mName, String vName) {
            super(Opcodes.ASM5, mv);
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String desc, Handle bsm,
                                           Object... bsmArgs) {
            System.out.println("visitInvokeDynamicInsn :[" + name + "," + desc + "," + bsm + "]");
            super.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc) {
            System.out.println("visitMethodInsn :[" + name + "," + desc + "]");
            super.visitMethodInsn(opcode, owner, name, desc);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            System.out.println("visitMethodInsn :[" + desc + "]");
            return super.visitAnnotation(desc, visible);
        }

        public void visitLocalVariable(String name, String desc, String signature, Label start,
                                       Label end, int index) {
            System.out.println("visitLocalVariable :[" + desc + "," + signature + "]");
            if (mv != null) {
                mv.visitLocalVariable(name, desc, signature, start, end, index);
            }
        }

        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            System.out.println("visitFieldInsn :[" + name + "," + desc + "]");
            if (mv != null) {
                mv.visitFieldInsn(opcode, owner, name, desc);
            }
        }
    }

    private static void digest(Class<?> clasz) throws IOException {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        ClassDigest digest = new ClassDigest(classWriter);
        new ClassReader(clasz.getName()).accept(digest, ClassReader.EXPAND_FRAMES);
        System.out.println(Arrays.toString(digest.getImports()));

    }

}
