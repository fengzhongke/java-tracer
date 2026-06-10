//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.ali.trace.agent.main;

import com.ali.trace.agent.inject.TraceEnhance;
import com.ali.trace.agent.inject.TraceTransformer;
import com.ali.trace.agent.loader.SpyClassLoader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class Premain {
    private static final String PATH = "/META-INF/lib";
    private static final String SPY_CLASS = "com.ali.trace.spy.inject.TraceInjector";
    private static final String JETTY_SERVER_CLASS = "com.ali.trace.spy.jetty.JettyServer";
    private static final String CONFIG_POOL_CLASS = "com.ali.trace.spy.core.ConfigPool";
    private static final String NODE_POOL_CLASS = "com.ali.trace.spy.core.NodePool";
    private static final String VM_LOADER_CLASS = "com.ali.trace.spy.jetty.io.AgentResVmLoader";
    private static final String VM_RESOLVER_CLASS = "com.ali.trace.spy.jetty.io.VmViewResolver";
    private static final int DEFAULT_PORT = 18902;
    private static final String CONFIG_PORT = "-port";
    private static final String CONFIG_SLEEP = "-sleep";
    private static final String CONFIG_MODE = "-mode";
    private static final String CONFIG_RELOAD = "-reload";
    private static final String CONFIG_PID = "-pid";
    private static final String CONFIG_RETRANSFORM = "-retransform";
    private static final String CONFIG_JDKLIB = "-jdklib";
    private static SpyClassLoader LOADER = new SpyClassLoader((ClassLoader)null);
    private static final AtomicReference<Object> INJECT = new AtomicReference();
    private static volatile Instrumentation instrumentation;
    private static volatile TraceTransformer transformer;
    private static Set<String> CANT_TRANSFORM = new HashSet();

    public static void premain(String args, Instrumentation inst) {
        Map<String, String> configs = parseArgs(args);
        int port = parsePort(configs);
        long sleep = parseSleep(configs);
        int mode = parseMode(configs);
        System.out.println("init trace agent with port [" + port + "] and sleep [" + sleep + "]");
        System.out.println("pages can be found in http://127.0.0.1:" + port);
        Object inject = loadSpyJar(inst, port, mode, false);

        try {
            if (inject == null) {
                throw new Exception("inject is null");
            }

            Thread.sleep(sleep);
            TraceTransformer tx = new TraceTransformer(inject);
            inst.addTransformer(tx, true);
            instrumentation = inst;
            transformer = tx;
        } catch (Throwable t) {
            t.printStackTrace();
        }

    }

    public static void agentmain(String args, Instrumentation inst) {
        Map<String, String> configs = parseArgs(args);
        int port = parsePort(configs);
        long sleep = parseSleep(configs);
        int mode = parseMode(configs);
        boolean reload = parseReload(configs);
        boolean retransform = parseRetransform(configs);
        System.out.println("attach trace agent with port [" + port + "] mode [" + mode + "] reload [" + reload + "] retransform [" + retransform + "]");
        System.out.println("pages can be found in http://127.0.0.1:" + port);
        Object inject = loadSpyJar(inst, port, mode, reload);

        try {
            if (inject == null) {
                throw new Exception("inject is null");
            }

            Thread.sleep(sleep);
            TraceTransformer tx = new TraceTransformer(inject);
            inst.addTransformer(tx, true);
            instrumentation = inst;
            transformer = tx;
            if (retransform) {
                System.out.println("Retransforming already loaded classes...");
                int success = 0;
                int failed = 0;
                Class<?>[] classes = inst.getAllLoadedClasses();

                for(Class<?> clasz : classes) {
                    String name = clasz.getName();

                    try {
                        if (inst.isModifiableClass(clasz) && clasz.getClassLoader() != null && clasz.getClassLoader().getParent() != null && !CANT_TRANSFORM.contains(name) && !shouldSkipClass(name)) {
                            inst.retransformClasses(new Class[]{clasz});
                            ++success;
                        }
                    } catch (Throwable var20) {
                        CANT_TRANSFORM.add(name);
                        ++failed;
                    }
                }

                System.out.println("Retransform completed: " + success + " success, " + failed + " failed");
            } else {
                System.out.println("Skipping retransform of already loaded classes. Use /class/redefine API to retransform specific classes.");
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }

    }

    private static boolean parseRetransform(Map<String, String> configs) {
        String retransformStr = (String)configs.get("-retransform");
        return retransformStr != null && Boolean.parseBoolean(retransformStr);
    }

    private static boolean shouldSkipClass(String name) {
        if (!name.startsWith("java.") && !name.startsWith("javax.") && !name.startsWith("sun.") && !name.startsWith("com.sun.")) {
            if (!name.contains("$Proxy") && !name.contains("$Hibernate") && !name.contains("$SpringCGLIB") && !name.contains("$FastClass")) {
                return name.contains("$$Lambda") || name.contains("$aux");
            } else {
                return true;
            }
        } else {
            return true;
        }
    }

    private static Map<String, String> parseArgs(String args) {
        Map<String, String> configs = new HashMap();
        if (args != null) {
            String[] pairs = args.split(":");
            if (pairs != null) {
                for(String pair : pairs) {
                    int idx = pair.indexOf("=");
                    if (idx > 0) {
                        configs.put(pair.substring(0, idx), pair.substring(idx + 1));
                    }
                }
            }
        }

        return configs;
    }

    private static int parsePort(Map<String, String> configs) {
        String portStr = (String)configs.get("-port");
        return portStr != null ? Integer.valueOf(portStr) : 18902;
    }

    private static long parseSleep(Map<String, String> configs) {
        String sleepStr = (String)configs.get("-sleep");
        return sleepStr != null ? Long.parseLong(sleepStr) : 1L;
    }

    private static int parseMode(Map<String, String> configs) {
        String modeStr = (String)configs.get("-mode");
        return modeStr != null ? Integer.valueOf(modeStr) : 0;
    }

    private static boolean parseReload(Map<String, String> configs) {
        String reloadStr = (String)configs.get("-reload");
        return reloadStr != null && Boolean.parseBoolean(reloadStr);
    }

    private static void shutdownOldComponents(SpyClassLoader newLoader) {
        try {
            Class<?> jettyClass = LOADER.loadClass("com.ali.trace.spy.jetty.JettyServer");
            Method shutdownMethod = jettyClass.getMethod("shutdown");
            shutdownMethod.invoke((Object)null);
            System.out.println("Jetty shutdown completed");
            Thread.sleep(2000L);
            Class<?> configPoolClass = LOADER.loadClass("com.ali.trace.spy.core.ConfigPool");
            Method getPoolMethod = configPoolClass.getMethod("getPool");
            Object configPool = getPoolMethod.invoke((Object)null);
            Method clearMethod = configPoolClass.getMethod("clear");
            clearMethod.invoke(configPool);
            System.out.println("ConfigPool cleared");
            Class<?> nodePoolClass = LOADER.loadClass("com.ali.trace.spy.core.NodePool");
            Method getNodePoolMethod = nodePoolClass.getMethod("getPool");
            Object nodePool = getNodePoolMethod.invoke((Object)null);
            Method nodeClearMethod = nodePoolClass.getMethod("clear");
            nodeClearMethod.invoke(nodePool);
            System.out.println("NodePool cleared");
            Class<?> vmLoaderClass = LOADER.loadClass("com.ali.trace.spy.jetty.io.AgentResVmLoader");
            Method updateMethod = vmLoaderClass.getMethod("updateClassLoader", ClassLoader.class);
            updateMethod.invoke((Object)null, newLoader);
            System.out.println("Velocity ClassLoader updated");
            Class<?> vmResolverClass = LOADER.loadClass("com.ali.trace.spy.jetty.io.VmViewResolver");
            Method reinitMethod = vmResolverClass.getMethod("reinit");
            reinitMethod.invoke((Object)null);
            System.out.println("Velocity engine reinitialized");
        } catch (Throwable t) {
            System.out.println("Shutdown warning: " + t.getMessage());
        }

    }

    public static boolean unload() {
        System.out.println("Unloading java-tracer agent...");

        try {
            Class<?> configPoolClass = LOADER.loadClass("com.ali.trace.spy.core.ConfigPool");
            Method getPoolMethod = configPoolClass.getMethod("getPool");
            Object configPool = getPoolMethod.invoke((Object)null);
            Method delMethod = configPoolClass.getMethod("delInterceptor");
            delMethod.invoke(configPool);
            System.out.println("Interceptor disabled");
        } catch (Throwable t) {
            System.out.println("Warning: Failed to disable interceptor: " + t.getMessage());
        }

        try {
            if (instrumentation != null && transformer != null) {
                instrumentation.removeTransformer(transformer);
                System.out.println("Transformer removed from Instrumentation");
            }
        } catch (Throwable t) {
            System.out.println("Warning: Failed to remove transformer: " + t.getMessage());
        }

        try {
            Class<?> jettyClass = LOADER.loadClass("com.ali.trace.spy.jetty.JettyServer");
            Method shutdownMethod = jettyClass.getMethod("shutdown");
            shutdownMethod.invoke((Object)null);
            System.out.println("Jetty server shutdown completed");
        } catch (Throwable t) {
            System.out.println("Warning: Failed to shutdown Jetty: " + t.getMessage());
        }

        try {
            Class<?> configPoolClass = LOADER.loadClass("com.ali.trace.spy.core.ConfigPool");
            Method getPoolMethod = configPoolClass.getMethod("getPool");
            Object configPool = getPoolMethod.invoke((Object)null);
            Method clearMethod = configPoolClass.getMethod("clear");
            clearMethod.invoke(configPool);
            System.out.println("ConfigPool cleared");
        } catch (Throwable t) {
            System.out.println("Warning: Failed to clear ConfigPool: " + t.getMessage());
        }

        try {
            Class<?> nodePoolClass = LOADER.loadClass("com.ali.trace.spy.core.NodePool");
            Method getNodePoolMethod = nodePoolClass.getMethod("getPool");
            Object nodePool = getNodePoolMethod.invoke((Object)null);
            Method nodeClearMethod = nodePoolClass.getMethod("clear");
            nodeClearMethod.invoke(nodePool);
            System.out.println("NodePool cleared");
        } catch (Throwable t) {
            System.out.println("Warning: Failed to clear NodePool: " + t.getMessage());
        }

        INJECT.set((Object)null);
        transformer = null;
        instrumentation = null;
        System.out.println("java-tracer agent unloaded successfully");
        return true;
    }

    public static boolean isLoaded() {
        return INJECT.get() != null;
    }

    private static Object loadSpyJar(Instrumentation inst, int port, int mode, boolean reload) {
        Object inject = null;
        if (reload) {
            SpyClassLoader newLoader = new SpyClassLoader((ClassLoader)null);
            shutdownOldComponents(newLoader);
            INJECT.set((Object)null);
            LOADER = newLoader;
            System.out.println("Created new SpyClassLoader for hot reload");
        }

        if ((inject = INJECT.get()) == null) {
            synchronized(Premain.class) {
                if ((inject = INJECT.get()) == null) {
                    try {
                        String jarFilePath = getJarFilePath();
                        System.out.println("Reading tracer-spy from jar file on disk: " + jarFilePath);
                        JarFile jarFile = new JarFile(jarFilePath);

                        try {
                            Enumeration<JarEntry> entries = jarFile.entries();

                            while(entries.hasMoreElements()) {
                                JarEntry entry = (JarEntry)entries.nextElement();
                                String entryName = "/" + entry.getName();
                                if (entryName.startsWith("/META-INF/lib") && entryName.endsWith(".jar")) {
                                    InputStream is = jarFile.getInputStream(entry);
                                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                                    int chunk = 0;
                                    byte[] data = new byte[256];

                                    while(-1 != (chunk = is.read(data))) {
                                        bytes.write(data, 0, chunk);
                                    }

                                    is.close();
                                    data = bytes.toByteArray();
                                    LOADER.load(new ByteArrayInputStream(data, 0, data.length));
                                }
                            }
                        } finally {
                            jarFile.close();
                        }

                        Class injectClass = LOADER.loadClass("com.ali.trace.spy.inject.TraceInjector");
                        INJECT.set(inject = injectClass.getConstructor(Instrumentation.class, Class.class, Integer.TYPE, Integer.TYPE).newInstance(inst, TraceEnhance.class, port, mode));
                        System.out.println("Spy module loaded successfully");
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
            }
        }

        return inject;
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
        } else {
            ensureModuleAccess(args);
            Map<String, String> params = parseMainArgs(args);
            String pidStr = (String)params.get("-pid");
            if (pidStr == null) {
                System.err.println("Error: -pid parameter is required");
                printUsage();
            } else {
                int pid = Integer.parseInt(pidStr);
                int port = params.containsKey("-port") ? Integer.parseInt((String)params.get("-port")) : 18902;
                String jdklibOverride = (String)params.get("-jdklib");
                int jdkMajorVersion = getJdkMajorVersion();
                System.out.println("Attaching to process " + pid + " with port " + port + " (JDK " + jdkMajorVersion + ")");
                String jarPath = getJarFilePath();
                System.out.println("Agent jar: " + jarPath);
                AttachComponents components = extractAttachComponents(jarPath, jdkMajorVersion, jdklibOverride);
                StringBuilder agentArgsBuilder = new StringBuilder();
                agentArgsBuilder.append("-port").append("=").append(port);
                if (params.containsKey("-mode")) {
                    agentArgsBuilder.append(":").append("-mode").append("=").append((String)params.get("-mode"));
                }

                if (params.containsKey("-sleep")) {
                    agentArgsBuilder.append(":").append("-sleep").append("=").append((String)params.get("-sleep"));
                }

                String agentArgs = agentArgsBuilder.toString();
                boolean attached = false;
                if (jdkMajorVersion >= 9) {
                    try {
                        System.out.println("Trying JDK's built-in Attach API (jdk.attach module)...");
                        Class<?> vmClass = ClassLoader.getSystemClassLoader().loadClass("com.sun.tools.attach.VirtualMachine");
                        Method attachMethod = vmClass.getMethod("attach", String.class);
                        Object vm = attachMethod.invoke((Object)null, String.valueOf(pid));
                        System.out.println("Attached to VM " + pid + " via JDK module");

                        try {
                            Method loadAgentMethod = vmClass.getMethod("loadAgent", String.class, String.class);
                            loadAgentMethod.invoke(vm, jarPath, agentArgs);
                            System.out.println("Agent loaded successfully");
                        } finally {
                            Method detachMethod = vmClass.getMethod("detach");
                            detachMethod.invoke(vm);
                            System.out.println("Detached from VM");
                        }

                        attached = true;
                    } catch (InvocationTargetException e) {
                        Throwable cause = e.getCause();
                        System.out.println("JDK Attach API failed: " + (cause != null ? cause.getClass().getName() + ": " + cause.getMessage() : e.getMessage()));
                    } catch (ClassNotFoundException var24) {
                        System.out.println("JDK Attach API module not available, trying tool JAR...");
                    } catch (Exception e) {
                        System.out.println("JDK Attach API failed: " + e.getMessage());
                    }
                }

                if (!attached && components.attachJarFile != null) {
                    System.out.println("Using attach JAR from disk: " + components.attachJarFile.getAbsolutePath());
                    attachViaFileClassLoader(components.attachJarFile, pid, jarPath, agentArgs);
                    attached = true;
                }

                if (!attached) {
                    System.err.println("ERROR: Could not attach to target VM.");
                    System.err.println("On JDK 9+: ensure jdk.attach module is accessible (--add-modules jdk.attach)");
                    System.err.println("On JDK 8: ensure jre08_tool.jar is in META-INF/lib/jdk8/");
                    System.exit(1);
                }

            }
        }
    }

    private static void printUsage() {
        System.out.println("Usage: java -jar java-tracer.jar -pid=<pid> [-port=<port>] [-mode=<mode>] [-sleep=<ms>] [-jdklib=<version>] [-retransform=true] [-reload=true]");
        System.out.println("  -pid       : Target Java process ID (required)");
        System.out.println("  -port      : Web server port (default: 18902)");
        System.out.println("  -mode      : Interceptor mode (0=default, 1=CommonThreadInterceptor, 2=CompressThreadInterceptor)");
        System.out.println("  -sleep     : Delay before transformer registration in ms (default: 1)");
        System.out.println("  -jdklib    : JDK version for native library (auto-detected, override: 8 or 21)");
        System.out.println("  -retransform: Retransform already loaded classes (default: false)");
        System.out.println("  -reload    : Force new ClassLoader for hot reload (default: false)");
        System.out.println("\nExample: java -jar java-tracer.jar -pid=12345:-port=19902:-mode=1");
        System.out.println("         java -jar java-tracer.jar -pid=12345:-port=18902:-jdklib=21");
    }

    private static Map<String, String> parseMainArgs(String[] args) {
        Map<String, String> params = new LinkedHashMap();

        for(String arg : args) {
            String[] parts = arg.split(":");

            for(String part : parts) {
                int idx = part.indexOf("=");
                if (idx > 0) {
                    params.put(part.substring(0, idx), part.substring(idx + 1));
                } else if (part.length() > 0 && part.startsWith("-")) {
                    params.put(part, "true");
                }
            }
        }

        return params;
    }

    private static String getJarFilePath() {
        String fileName = Premain.class.getResource(Premain.class.getSimpleName() + ".class").getFile();
        if (fileName.startsWith("file:")) {
            fileName = fileName.substring(5);
        }

        if (fileName.startsWith("/") && fileName.length() > 2 && fileName.charAt(2) == ':') {
            fileName = fileName.substring(1);
        } else if (fileName.startsWith("///") && fileName.length() > 3 && fileName.charAt(3) == ':') {
            fileName = fileName.substring(3);
        }

        int idx2 = fileName.indexOf("!");
        if (idx2 > 0) {
            fileName = fileName.substring(0, idx2);
        }

        fileName = fileName.replace("%20", " ");
        System.out.println("Resolved jar path: " + fileName);
        return fileName;
    }

    private static void ensureModuleAccess(String[] originalArgs) throws Exception {
        int jdkMajor = getJdkMajorVersion();
        if (jdkMajor >= 9) {
            if (!Boolean.getBoolean("tracer.attach.reexeced")) {
                boolean hasJdkAttach = hasModule("jdk.attach");
                String javaHome = System.getProperty("java.home");
                String javaExe = javaHome + File.separator + "bin" + File.separator + "java";
                if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                    javaExe = javaExe + ".exe";
                }

                List<String> command = new ArrayList();
                command.add(javaExe);
                if (hasJdkAttach) {
                    command.add("--add-modules");
                    command.add("jdk.attach");
                }

                command.add("--add-opens");
                command.add("java.base/jdk.internal.misc=ALL-UNNAMED");
                command.add("--add-opens");
                command.add("java.base/java.io=ALL-UNNAMED");
                command.add("--add-opens");
                command.add("java.base/sun.nio.ch=ALL-UNNAMED");
                command.add("-Dtracer.attach.reexeced=true");
                command.add("-jar");
                command.add(getJarFilePath());

                for(String arg : originalArgs) {
                    command.add(arg);
                }

                System.out.println("JDK " + jdkMajor + " detected, re-executing with module access flags..." + (hasJdkAttach ? " (jdk.attach module available)" : " (jdk.attach module not found, will use bundled tool JAR)"));
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.inheritIO();
                Process process = pb.start();
                int exitCode = process.waitFor();
                System.exit(exitCode);
            }
        }
    }

    private static boolean hasModule(String moduleName) {
        try {
            Class<?> moduleLayerClass = Class.forName("java.lang.ModuleLayer");
            Object bootLayer = moduleLayerClass.getMethod("boot").invoke((Object)null);
            Method findModuleMethod = moduleLayerClass.getMethod("findModule", String.class);
            Object result = findModuleMethod.invoke(bootLayer, moduleName);
            Class<?> optionalClass = Class.forName("java.util.Optional");
            Method isPresentMethod = optionalClass.getMethod("isPresent");
            return (Boolean)isPresentMethod.invoke(result);
        } catch (Throwable var7) {
            return false;
        }
    }

    private static int getJdkMajorVersion() {
        String version = System.getProperty("java.specification.version");
        if (version == null) {
            return 8;
        } else if (version.startsWith("1.")) {
            try {
                return Integer.parseInt(version.substring(2));
            } catch (NumberFormatException var2) {
                return 8;
            }
        } else {
            try {
                return Integer.parseInt(version);
            } catch (NumberFormatException var3) {
                return 8;
            }
        }
    }

    private static String getJdkLibDir(int jdkMajorVersion, String override) {
        if (override != null && !override.isEmpty()) {
            if (override.equals("8")) {
                return "jdk8";
            } else {
                return !override.equals("21") && !override.equals("9") && !override.equals("11") && !override.equals("17") && !override.equals("23") ? override : "jdk21";
            }
        } else {
            return jdkMajorVersion <= 8 ? "jdk8" : "jdk21";
        }
    }

    private static void attachViaFileClassLoader(File attachJarFile, int pid, String jarPath, String agentArgs) throws Exception {
        URLClassLoader attachLoader = new URLClassLoader(new URL[]{attachJarFile.toURI().toURL()}, (ClassLoader)null);

        try {
            Class<?> vmClass = attachLoader.loadClass("com.sun.tools.attach.VirtualMachine");
            Method attachMethod = vmClass.getMethod("attach", String.class);
            Object vm = attachMethod.invoke((Object)null, String.valueOf(pid));
            System.out.println("Attached to VM " + pid + " via file ClassLoader");

            try {
                Method loadAgentMethod = vmClass.getMethod("loadAgent", String.class, String.class);
                loadAgentMethod.invoke(vm, jarPath, agentArgs);
                System.out.println("Agent loaded successfully");
            } finally {
                Method detachMethod = vmClass.getMethod("detach");
                detachMethod.invoke(vm);
                System.out.println("Detached from VM");
            }

        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause != null) {
                throw new Exception("Attach failed: " + cause.getClass().getName() + ": " + cause.getMessage(), cause);
            } else {
                throw new Exception("Attach failed", e);
            }
        } catch (ClassNotFoundException e) {
            throw new Exception("Attach API classes not found in " + attachJarFile.getAbsolutePath(), e);
        }
    }

    private static String getAttachJarName(String jdkLibDir) {
        if (jdkLibDir.equals("jdk8")) {
            return "jre08_tool.jar";
        } else {
            return jdkLibDir.equals("jdk21") ? "jre21_tool.jar" : "attach-tools.jar";
        }
    }

    private static AttachComponents extractAttachComponents(String jarPath, int jdkMajorVersion, String jdklibOverride) {
        String osArch = System.getProperty("os.arch");
        String osName = System.getProperty("os.name").toLowerCase();
        String libName;
        String archDir;
        if (osName.contains("windows")) {
            libName = "attach.dll";
            archDir = "windows";
        } else {
            if (!osName.contains("linux") && !osName.contains("mac")) {
                System.out.println("Unsupported platform " + osName + ", skipping component extraction");
                return new AttachComponents();
            }

            if (!osArch.contains("aarch64") && !osArch.contains("arm64")) {
                libName = "libattach.so";
                archDir = "amd64";
            } else {
                libName = "libattach.so";
                archDir = "aarch64";
            }
        }

        String jdkLibDir = getJdkLibDir(jdkMajorVersion, jdklibOverride);
        AttachComponents result = new AttachComponents();
        File cwdDir = new File(System.getProperty("user.dir"));
        String attachJarName = getAttachJarName(jdkLibDir);
        String jarEntry = "META-INF/lib/" + jdkLibDir + "/" + attachJarName;
        System.out.println("Looking for attach JAR: " + jarEntry + " (JDK " + jdkMajorVersion + " → " + jdkLibDir + ")");
        result.attachJarFile = extractFileFromJar(jarPath, jarEntry, attachJarName, cwdDir);
        if (result.attachJarFile == null) {
            String fallbackDir = jdkLibDir.equals("jdk8") ? "jdk21" : "jdk8";
            String fallbackJarName = getAttachJarName(fallbackDir);
            String fallbackEntry = "META-INF/lib/" + fallbackDir + "/" + fallbackJarName;
            System.out.println("Primary attach JAR not found, trying fallback: " + fallbackEntry);
            result.attachJarFile = extractFileFromJar(jarPath, fallbackEntry, fallbackJarName, cwdDir);
        }

        if (result.attachJarFile != null) {
            System.out.println("Attach JAR extracted to " + result.attachJarFile.getAbsolutePath());
            String nativeInJar = "META-INF/lib/" + archDir + "/" + libName;
            System.out.println("Looking for native library inside attach JAR: " + nativeInJar);
            result.nativeLib = extractFileFromJar(result.attachJarFile.getAbsolutePath(), nativeInJar, libName, cwdDir);
        }

        if (result.nativeLib == null) {
            String nativeInAgent = "META-INF/lib/" + jdkLibDir + "/" + archDir + "/" + libName;
            System.out.println("Native library not found in attach JAR, trying agent JAR: " + nativeInAgent);
            result.nativeLib = extractFileFromJar(jarPath, nativeInAgent, libName, cwdDir);
        }

        if (result.nativeLib == null) {
            String fallbackDir = jdkLibDir.equals("jdk8") ? "jdk21" : "jdk8";
            String fallbackNative = "META-INF/lib/" + fallbackDir + "/" + archDir + "/" + libName;
            System.out.println("Trying fallback native library: " + fallbackNative);
            result.nativeLib = extractFileFromJar(jarPath, fallbackNative, libName, cwdDir);
        }

        if (result.nativeLib == null) {
            String classpathRes = "/META-INF/lib/" + jdkLibDir + "/" + archDir + "/" + libName;
            System.out.println("Trying classpath resource extraction: " + classpathRes);
            result.nativeLib = extractResourceFromClasspath(classpathRes, libName, cwdDir);
        }

        if (result.nativeLib == null) {
            String fallbackDir2 = jdkLibDir.equals("jdk8") ? "jdk21" : "jdk8";
            String classpathFallback = "/META-INF/lib/" + fallbackDir2 + "/" + archDir + "/" + libName;
            System.out.println("Trying classpath fallback: " + classpathFallback);
            result.nativeLib = extractResourceFromClasspath(classpathFallback, libName, cwdDir);
        }

        if (result.nativeLib == null) {
            result.nativeLib = findJdkOwnNativeLib(libName, archDir);
        }

        if (result.nativeLib != null) {
            String nativeLibPath = result.nativeLib.getAbsolutePath();
            System.setProperty("attach.lib.path", nativeLibPath);
            System.out.println("Native library ready: " + nativeLibPath + " (will be loaded by Attach API classes)");
            if (jdkMajorVersion <= 8) {
                File cwdCopy = copyNativeLibToCwd(result.nativeLib);
                if (cwdCopy != null) {
                    nativeLibPath = cwdCopy.getAbsolutePath();
                    System.setProperty("attach.lib.path", nativeLibPath);
                    System.out.println("Native library cwd copy: " + nativeLibPath);
                }
            }
        } else {
            System.out.println("No native library found, will try System.loadLibrary(\"attach\") at runtime");
        }

        return result;
    }

    private static File extractFileFromJar(String sourceJarPath, String entryPath, String outputName, File outputDir) {
        try {
            JarFile jarFile = new JarFile(sourceJarPath);
            JarEntry entry = jarFile.getJarEntry(entryPath);
            if (entry == null) {
                jarFile.close();
                return null;
            } else {
                outputDir.mkdirs();
                File outputFile = new File(outputDir, outputName);
                InputStream is = jarFile.getInputStream(entry);

                try {
                    FileOutputStream fos = new FileOutputStream(outputFile);

                    try {
                        byte[] buf = new byte[4096];

                        int n;
                        while((n = is.read(buf)) != -1) {
                            fos.write(buf, 0, n);
                        }
                    } finally {
                        fos.close();
                    }
                } finally {
                    is.close();
                }

                jarFile.close();
                System.out.println("Extracted " + entryPath + " → " + outputFile.getAbsolutePath());
                return outputFile;
            }
        } catch (IOException e) {
            System.out.println("Warning: Failed to extract " + entryPath + " from " + sourceJarPath + ": " + e.getMessage());
            return null;
        }
    }

    private static File extractResourceFromClasspath(String resourcePath, String outputName, File outputDir) {
        try {
            InputStream in = Premain.class.getResourceAsStream(resourcePath);
            if (in == null) {
                System.out.println("Resource not found in classpath: " + resourcePath);
                return null;
            } else {
                outputDir.mkdirs();
                File outputFile = new File(outputDir, outputName);

                try {
                    FileOutputStream fos = new FileOutputStream(outputFile);

                    try {
                        byte[] data = new byte[1024];

                        int len;
                        while((len = in.read(data)) > 0) {
                            fos.write(data, 0, len);
                        }
                    } finally {
                        fos.close();
                    }
                } finally {
                    in.close();
                }

                System.out.println("Extracted from classpath: " + resourcePath + " → " + outputFile.getAbsolutePath());
                return outputFile;
            }
        } catch (Exception e) {
            System.out.println("Warning: Failed to extract " + resourcePath + " from classpath: " + e.getMessage());
            return null;
        }
    }

    private static File copyNativeLibToCwd(File nativeLib) {
        try {
            File cwd = new File(System.getProperty("user.dir"));
            File cwdCopy = new File(cwd, nativeLib.getName());
            if (cwdCopy.exists()) {
                System.out.println("Native library already in cwd: " + cwdCopy.getAbsolutePath());
                return cwdCopy;
            } else {
                FileInputStream fis = new FileInputStream(nativeLib);

                try {
                    FileOutputStream fos = new FileOutputStream(cwdCopy);

                    try {
                        byte[] buf = new byte[4096];

                        int n;
                        while((n = fis.read(buf)) != -1) {
                            fos.write(buf, 0, n);
                        }
                    } finally {
                        fos.close();
                    }
                } finally {
                    fis.close();
                }

                System.out.println("Copied native library to cwd: " + cwdCopy.getAbsolutePath());
                return cwdCopy;
            }
        } catch (IOException e) {
            System.out.println("Warning: Failed to copy native library to cwd: " + e.getMessage());
            return null;
        }
    }

    private static File findJdkOwnNativeLib(String libName, String archDir) {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            File jdkLib = new File(javaHome, "lib" + File.separator + libName);
            if (jdkLib.exists()) {
                System.out.println("Using JDK's own native library: " + jdkLib.getAbsolutePath());
                return jdkLib;
            }

            File jdk8Lib = new File(javaHome, "jre" + File.separator + "lib" + File.separator + archDir + File.separator + libName);
            if (jdk8Lib.exists()) {
                System.out.println("Using JDK's own native library: " + jdk8Lib.getAbsolutePath());
                return jdk8Lib;
            }
        }

        return null;
    }

    static class AttachComponents {
        File attachJarFile;
        File nativeLib;
    }
}
