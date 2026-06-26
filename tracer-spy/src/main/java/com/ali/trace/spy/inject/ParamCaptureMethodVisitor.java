package com.ali.trace.spy.inject;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.Method;

/**
 * AdviceAdapter that captures method parameters at entry for the param capture target method.
 * Since TraceInjector.CodeVisitor now precisely selects the target method by ASM descriptor
 * (not just name), this visitor is ONLY created for the exact target method — no redundant
 * name-only guard needed.
 *
 * On method entry: builds Object[] with [this/null, arg0, arg1, ...] and calls
 *   TraceEnhance.setParams(Object[]), then TraceEnhance.s(String,String) for normal tracing.
 * On method exit: calls TraceEnhance.e(String,String) for normal tracing.
 *
 * The first element of the captured array is the receiver object (this) for instance methods,
 * or null for static methods. This allows InvokeEngine to use the captured receiver as the
 * invocation target when re-executing the method.
 */
public class ParamCaptureMethodVisitor extends AdviceAdapter {

    private static final Type OBJECT_ARRAY_TYPE = Type.getType(Object[].class);
    private static final Type OBJECT_TYPE = Type.getType(Object.class);

    private final String mName;
    private final String cName;
    private final boolean isStatic;
    private final Type TYPE;
    private final Method START;
    private final Method END;
    private final Method SET_PARAMS;

    public ParamCaptureMethodVisitor(MethodVisitor methodVisitor, int acc, String name, String desc,
                                      String cName,
                                      Type TYPE, Method START, Method END, Method SET_PARAMS) {
        super(Opcodes.ASM7, methodVisitor, acc, name, desc);
        this.mName = name.replaceAll("<", "_").replaceAll("\\$|>", "");
        this.cName = cName;
        this.isStatic = (acc & Opcodes.ACC_STATIC) != 0;
        this.TYPE = TYPE;
        this.START = START;
        this.END = END;
        this.SET_PARAMS = SET_PARAMS;
    }

    @Override
    protected void onMethodEnter() {
        // Build Object[] with [this/null, arg0, arg1, ...]
        int argCount = getArgumentTypes().length;
        int arrayLen = argCount + 1;  // +1 for receiver/null

        // Create new Object[arrayLen]
        push(arrayLen);
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");

        // Store receiver/null at index 0
        dup();
        push(0);
        if (isStatic) {
            mv.visitInsn(Opcodes.ACONST_NULL);  // null for static methods
        } else {
            loadThis();    // this for instance methods
        }
        mv.visitInsn(Opcodes.AASTORE);

        // Store each argument at index 1..argCount
        for (int i = 0; i < argCount; i++) {
            dup();
            push(i + 1);
            loadArg(i);
            box(getArgumentTypes()[i]);
            mv.visitInsn(Opcodes.AASTORE);
        }

        invokeStatic(TYPE, SET_PARAMS);

        // Normal tracing call (same as CommonAdapter)
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
