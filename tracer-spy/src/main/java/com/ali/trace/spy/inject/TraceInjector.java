package com.ali.trace.spy.inject;

import com.ali.trace.spy.core.ConfigPool;
import com.ali.trace.spy.core.NodePool;
import com.ali.trace.spy.interceptor.CommonThreadntercepter;
import com.ali.trace.spy.interceptor.CompressThreadInterceptor;
import com.ali.trace.spy.jetty.JettyServer;
import com.ali.trace.spy.jetty.io.AgentResVmLoader;

import org.apache.commons.lang.StringUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.Method;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * trace code inject
 *
 * @author nkhanlang@163.com
 */
public class TraceInjector {

    private final ClassLoader LOADER;

    private final Type TYPE;

    private final String TYPE_NAME;

    private final Method START;

    private final Method END;

    private final ConfigPool POOL = ConfigPool.getPool();

    private volatile Set<String> includePrefix = new HashSet<>();

    private volatile Set<String> excludePrefix = new HashSet<>();

    private List<String> redefinedNames = new ArrayList<>();

    public TraceInjector(Instrumentation inst, Class<?> clasz, int port, int mode)
        throws NoSuchMethodException, SecurityException {
        LOADER = getClass().getClassLoader();
        TYPE_NAME = clasz.getName().replace(".", "/");
        TYPE = Type.getType(clasz);
        START = Method.getMethod(clasz.getMethod("s", new Class<?>[] {String.class, String.class}));
        END = Method.getMethod(clasz.getMethod("e", new Class<?>[] {String.class, String.class}));
        POOL.setInst(inst, this);
        POOL.setWeaveClass(clasz);
        NodePool.getPool().setMode(mode);
        initPrefix();

        // Update Velocity to use current ClassLoader (for hot reload)
        AgentResVmLoader.updateClassLoader(LOADER);

        if (mode == 1) {
            POOL.setinterceptor(new CommonThreadntercepter());
        } else if (mode == 2) {
            POOL.setinterceptor(new CompressThreadInterceptor());
        }
        new JettyServer(port);
    }

    public byte[] getBytes(final ClassLoader loader, final String name, byte[] bytes) throws Throwable {
        Integer type = 0;
        try {
            if (name != null) {
                if (loader != null && !filter(name)) {
                    byte[] newBytes = new CodeReader(loader, name, bytes, POOL.isRedefine(loader, name),
                        true).getBytes();  // Always use common mode for better compatibility
                    bytes = newBytes;
                    redefinedNames.add(name);
                    type = 1;  // weave success
                }
            }
            return bytes;
        } catch (TypeNotPresentException e) {
            // Type not found - skip this class, mark as not woven
            type = 0;
            System.out.println("TypeNotPresentException for class: " + name + " - " + e.getMessage());
            throw e;
        } catch (Throwable t) {
            // Transformation failed - mark as not woven
            type = 0;
            System.err.println("Transform error for class: " + name + " - " + t.getClass().getSimpleName() + ": " + t.getMessage());
            throw t;
        } finally {
            if (name != null) {
                POOL.addClass(loader, name, type);
            }
        }
    }

    private void initPrefix() {
        String config = System.getProperty("tracer.config.file");
        System.out.println("config path is [" + config + "]");
        List<String> lines = new ArrayList<>();
        try {
            File file;
            if (config != null && (file = new File(config)).exists()) {
                lines.addAll(Files.readAllLines(Paths.get(file.toURI())));
            }
        } catch (Exception e) {
        }
        setConfig(lines);
    }

    public void setConfig(List<String> lines) {
        Set<String> includes = new HashSet<>();
        Set<String> excludes = new HashSet<>();
        Set<String> parts = null;
        for (String line : lines) {
            if (line.contains("include:")) {
                parts = includes;
            } else if (line.contains("exclude:")) {
                parts = excludes;
            } else if (parts != null && StringUtils.isNotBlank(line.trim())) {
                parts.add(line.trim());
            }
        }
        includes.forEach(prefix -> System.out.println("include: parts[" + prefix + "]"));
        excludes.forEach(prefix -> System.out.println("exclude: parts[" + prefix + "]"));
        includePrefix = includes;
        excludePrefix = excludes;
    }

    public List<String> getConfig() {
        List<String> lines = new ArrayList<>();
        lines.add("include:");
        lines.addAll(includePrefix);
        lines.add("exclude:");
        lines.addAll(excludePrefix);
        return lines;
    }

    public List<String> getRedefinedNames() {
        return redefinedNames;
    }

    /**
     * Add a prefix to include list. Creates new HashSet to maintain volatile semantics.
     */
    public void addIncludePrefix(String prefix) {
        Set<String> newSet = new HashSet<>(includePrefix);
        newSet.add(prefix);
        includePrefix = newSet;
    }

    /**
     * Add a prefix to exclude list. Creates new HashSet to maintain volatile semantics.
     */
    public void addExcludePrefix(String prefix) {
        Set<String> newSet = new HashSet<>(excludePrefix);
        newSet.add(prefix);
        excludePrefix = newSet;
    }

    /**
     * Remove a prefix from include list. Creates new HashSet to maintain volatile semantics.
     */
    public void removeIncludePrefix(String prefix) {
        Set<String> newSet = new HashSet<>(includePrefix);
        newSet.remove(prefix);
        includePrefix = newSet;
    }

    /**
     * Remove a prefix from exclude list. Creates new HashSet to maintain volatile semantics.
     */
    public void removeExcludePrefix(String prefix) {
        Set<String> newSet = new HashSet<>(excludePrefix);
        newSet.remove(prefix);
        excludePrefix = newSet;
    }

    /**
     * Get current include prefix set (for UI status determination)
     */
    public Set<String> getIncludePrefixes() {
        return includePrefix;
    }

    /**
     * Get current exclude prefix set (for UI status determination)
     */
    public Set<String> getExcludePrefixes() {
        return excludePrefix;
    }

    public boolean filter(String name) {
        for (String prefix : excludePrefix) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        for (String prefix : includePrefix) {
            if (name.startsWith(prefix) || prefix.equalsIgnoreCase("*")) {
                return false;
            }
        }
        return true;
    }

    class CodeReader extends ClassReader {
        private final ClassWriter classWriter;

        public CodeReader(final ClassLoader loader, final String name, byte[] bytes, final boolean redefine,
            boolean common) {
            super(bytes);
            int flag = ClassWriter.COMPUTE_MAXS;
            if (!common) {
                flag = flag | ClassWriter.COMPUTE_FRAMES;
            }
            classWriter = new ClassWriter(flag) {
                @Override
                public ClassLoader getClassLoader() {
                    return loader;
                }

                @Override
                protected String getCommonSuperClass(final String type1, final String type2) {
                    if (!redefine) {
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
            //return new TraceTryFinallyAdapter(super.visitMethod(access, name, desc, signature, exceptions), access, name, desc);
            if (common) {
                return new CommonAdapter(super.visitMethod(access, name, desc, signature, exceptions), access, name,
                    desc);
            } else {
                return new FinallyAdapter(super.visitMethod(access, name, desc, signature, exceptions), access, name,
                    desc);
            }
        }

        class FinallyAdapter extends AdviceAdapter {
            private String mName;

            private Label startFinally = new Label();

            private Label endFinally = new Label();

            public FinallyAdapter(MethodVisitor methodVisitor, int acc, String name, String desc) {
                super(Opcodes.ASM7, methodVisitor, acc, name, desc);
                this.mName = name.replaceAll("<", "_").replaceAll("\\$|>", "");

                if (cName.contains("CommonTaskTracker")) {
                    System.out.println("debug xxxxx vs [" + cName + "," + mName + "]");
                }
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

        public class TraceTryFinallyAdapter extends AdviceAdapter {
            private final boolean isConstructor;

            private final String mName;

            private final Label tryStart = new Label();

            private final Label tryEnd = new Label();

            private final Label catchAll = new Label();

            // 用于存储返回值的局部变量（如果有）
            private int returnValueVar = -1;

            private final Type returnType;

            private final boolean hasReturnValue;

            public TraceTryFinallyAdapter(MethodVisitor mv, int access,
                String name, String desc) {
                super(Opcodes.ASM7, mv, access, name, desc);
                this.isConstructor = name.equals("<init>");
                this.mName = name;
                this.returnType = Type.getReturnType(desc);
                this.hasReturnValue = returnType.getSort() != Type.VOID;
            }

            @Override
            public void visitCode() {
                super.visitCode();

                // 对于构造函数，在 super() 调用之后才插入 try 块
                // 对于普通方法，直接插入
                if (!isConstructor) {
                    // 1. 进入 try 前打印
                    printMethodEntry();
                    // 2. 标记 try 块开始
                    mv.visitLabel(tryStart);
                }
            }

            @Override
            protected void onMethodEnter() {
                // 对于构造函数，AdviceAdapter 确保在 super() 调用之后才执行这里
                if (isConstructor) {
                    // 1. 进入 try 前打印
                    printMethodEntry();
                    // 2. 标记 try 块开始
                    mv.visitLabel(tryStart);
                }
            }

            @Override
            public void visitInsn(int opcode) {
                // 在每个返回指令前执行 finally 打印
                if (opcode >= IRETURN && opcode <= RETURN) {
                    printMethodFinally("NORMAL");
                }
                super.visitInsn(opcode);
            }

            @Override
            public void visitMaxs(int maxStack, int maxLocals) {
                // 标记 try 块结束
                mv.visitLabel(tryEnd);

                // 添加异常处理器
                mv.visitTryCatchBlock(tryStart, tryEnd, catchAll, null);

                // 异常处理器
                mv.visitLabel(catchAll);

                // 保存异常对象
                int exceptionVar = newLocal(Type.getType(Throwable.class));
                mv.visitVarInsn(ASTORE, exceptionVar);

                // 打印退出信息
                printMethodFinally("EXCEPTION");

                // 重新抛出异常
                mv.visitVarInsn(ALOAD, exceptionVar);
                mv.visitInsn(ATHROW);

                // 确保足够的栈空间
                int requiredStack = Math.max(maxStack, 4);
                int requiredLocals = Math.max(maxLocals, exceptionVar + 1);
                super.visitMaxs(requiredStack, requiredLocals);
            }

            /**
             * 打印方法进入信息
             */
            private void printMethodEntry() {
                // 调用 Tracer.methodEntry(className, methodName)
                mv.visitLdcInsn(cName);
                mv.visitLdcInsn(mName);
                mv.visitMethodInsn(INVOKESTATIC,
                    TYPE_NAME,
                    "s",
                    START.getDescriptor(),
                    false);

            }

            /**
             * 打印方法 finally 信息
             */
            private void printMethodFinally(String exitType) {
                // 调用 Tracer.methodFinally(className, methodName, exitType)
                mv.visitLdcInsn(cName);
                mv.visitLdcInsn(mName);
                mv.visitMethodInsn(INVOKESTATIC,
                    TYPE_NAME,
                    "e",
                    END.getDescriptor(),
                    false);
            }

            private boolean isReturnOpcode(int opcode) {
                return opcode == RETURN || opcode == IRETURN || opcode == LRETURN ||
                    opcode == FRETURN || opcode == DRETURN || opcode == ARETURN;
            }
        }

    }
}