package com.ali.trace.inject;

import com.ali.asm.ClassReader;
import com.ali.asm.ClassVisitor;
import com.ali.asm.ClassWriter;
import com.ali.asm.Label;
import com.ali.asm.MethodVisitor;
import com.ali.asm.Opcodes;
import com.ali.asm.commons.AdviceAdapter;
import com.ali.trace.util.NameUtils;

public class TraceInjecter extends ClassReader {

	private static final String traceName = NameUtils.getClassPath(TraceEnhance.class.getName());
	private ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);

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

			public FinallyAdapter(MethodVisitor mv, int acc, String name, String desc) {
				super(Opcodes.ASM5, mv, acc, name, desc);
				this.name = NameUtils.getMethodName(name);
			}

			public void visitCode() {
				super.visitCode();
				mv.visitLdcInsn(cName);
				mv.visitLdcInsn(name);
				mv.visitMethodInsn(INVOKESTATIC, traceName, "s", "(Ljava/lang/String;Ljava/lang/String;)V");

				mv.visitLabel(startFinally);
			}

			public void visitMaxs(int maxStack, int maxLocals) {
				Label endFinally = new Label();
				mv.visitTryCatchBlock(startFinally, endFinally, endFinally, null);
				mv.visitLabel(endFinally);
				onFinally(ATHROW);
				mv.visitInsn(ATHROW);
				mv.visitMaxs(maxStack, maxLocals);
			}

			protected void onMethodExit(int opcode) {
				if (opcode != ATHROW) {
					onFinally(opcode);
				}
			}

			private void onFinally(int opcode) {
				mv.visitLdcInsn(cName);
				mv.visitLdcInsn(name);
				mv.visitMethodInsn(INVOKESTATIC, traceName, "e", "(Ljava/lang/String;Ljava/lang/String;)V");
			}
		}
	}
}
