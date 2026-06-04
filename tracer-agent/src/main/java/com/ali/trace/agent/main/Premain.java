package com.ali.trace.agent.main;

import java.io.*;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import com.ali.trace.agent.inject.TraceEnhance;
import com.ali.trace.agent.inject.TraceTransformer;
import com.ali.trace.agent.loader.SpyClassLoader;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.AttachNotSupportedException;

/**
 * @author nkhanlang@163.com
 */
public class Premain {

    private static final String PATH = "/META-INF/lib";
    private static final String SPY_CLASS = "com.ali.trace.spy.inject.TraceInjector";
    private static final String JETTY_SERVER_CLASS = "com.ali.trace.spy.jetty.JettyServer";
    private static final String CONFIG_POOL_CLASS = "com.ali.trace.spy.core.ConfigPool";
    private static final String NODE_POOL_CLASS = "com.ali.trace.spy.core.NodePool";
    private static final String VM_LOADER_CLASS = "com.ali.trace.spy.jetty.io.AgentResVmLoader";
    private static final String VM_RESOLVER_CLASS = "com.ali.trace.spy.jetty.io.VmViewResolver";

    // default port opened by JETTY
    private static final int DEFAULT_PORT = 18902;

    private static final String CONFIG_PORT = "-port";
    private static final String CONFIG_SLEEP = "-sleep";
    private static final String CONFIG_MODE = "-mode";
    private static final String CONFIG_RELOAD = "-reload";
    private static final String CONFIG_PID = "-pid";
    private static final String CONFIG_RETRANSFORM = "-retransform";

    private static SpyClassLoader LOADER = new SpyClassLoader(null);
    private static final AtomicReference<Object> INJECT = new AtomicReference<Object>();
    private static volatile Instrumentation instrumentation;
    private static volatile TraceTransformer transformer;

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

    /**
     * Dynamic attach entry point - called when agent is attached to running JVM
     */
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
                int success = 0, failed = 0;
                Class<?>[] classes = inst.getAllLoadedClasses();
                for (Class<?> clasz : classes) {
                    String name = clasz.getName();
                    try {
                        if (inst.isModifiableClass(clasz)
                            && clasz.getClassLoader() != null
                            && clasz.getClassLoader().getParent() != null
                            && !CANT_TRANSFORM.contains(name)
                            && !shouldSkipClass(name)) {
                            inst.retransformClasses(clasz);
                            success++;
                        }
                    } catch (Throwable t) {
                        CANT_TRANSFORM.add(name);
                        failed++;
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
        String retransformStr = configs.get(CONFIG_RETRANSFORM);
        return retransformStr != null && Boolean.parseBoolean(retransformStr);
    }

    private static boolean shouldSkipClass(String name) {
        if (name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("sun.") || name.startsWith("com.sun.")) {
            return true;
        }
        if (name.contains("$Proxy") || name.contains("$Hibernate") || name.contains("$SpringCGLIB") || name.contains("$FastClass")) {
            return true;
        }
        if (name.contains("$$Lambda") || name.contains("$aux")) {
            return true;
        }
        return false;
    }

    private static Set<String> CANT_TRANSFORM = new HashSet<String>();

    private static Map<String, String> parseArgs(String args) {
        Map<String, String> configs = new HashMap<String, String>();
        if (args != null) {
            String[] pairs = args.split(":");
            if (pairs != null) {
                for (String pair : pairs) {
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
        String portStr = configs.get(CONFIG_PORT);
        if (portStr != null) {
            return Integer.valueOf(portStr);
        }
        return DEFAULT_PORT;
    }

    private static long parseSleep(Map<String, String> configs) {
        String sleepStr = configs.get(CONFIG_SLEEP);
        if (sleepStr != null) {
            return Long.parseLong(sleepStr);
        }
        return 1L;
    }

    private static int parseMode(Map<String, String> configs) {
        String modeStr = configs.get(CONFIG_MODE);
        if (modeStr != null) {
            return Integer.valueOf(modeStr);
        }
        return 0;
    }

    private static boolean parseReload(Map<String, String> configs) {
        String reloadStr = configs.get(CONFIG_RELOAD);
        return reloadStr != null && Boolean.parseBoolean(reloadStr);
    }

    private static void shutdownOldComponents(SpyClassLoader newLoader) {
        try {
            Class<?> jettyClass = LOADER.loadClass(JETTY_SERVER_CLASS);
            Method shutdownMethod = jettyClass.getMethod("shutdown");
            shutdownMethod.invoke(null);
            System.out.println("Jetty shutdown completed");

            Thread.sleep(2000);

            Class<?> configPoolClass = LOADER.loadClass(CONFIG_POOL_CLASS);
            Method getPoolMethod = configPoolClass.getMethod("getPool");
            Object configPool = getPoolMethod.invoke(null);
            Method clearMethod = configPoolClass.getMethod("clear");
            clearMethod.invoke(configPool);
            System.out.println("ConfigPool cleared");

            Class<?> nodePoolClass = LOADER.loadClass(NODE_POOL_CLASS);
            Method getNodePoolMethod = nodePoolClass.getMethod("getPool");
            Object nodePool = getNodePoolMethod.invoke(null);
            Method nodeClearMethod = nodePoolClass.getMethod("clear");
            nodeClearMethod.invoke(nodePool);
            System.out.println("NodePool cleared");

            Class<?> vmLoaderClass = LOADER.loadClass(VM_LOADER_CLASS);
            Method updateMethod = vmLoaderClass.getMethod("updateClassLoader", ClassLoader.class);
            updateMethod.invoke(null, newLoader);
            System.out.println("Velocity ClassLoader updated");

            Class<?> vmResolverClass = LOADER.loadClass(VM_RESOLVER_CLASS);
            Method reinitMethod = vmResolverClass.getMethod("reinit");
            reinitMethod.invoke(null);
            System.out.println("Velocity engine reinitialized");

        } catch (Throwable t) {
            System.out.println("Shutdown warning: " + t.getMessage());
        }
    }

    /**
     * Unload the agent from the target JVM.
     * Called via /unload/exec web endpoint.
     *
     * Sequence:
     * 1. Disable interceptor (TraceEnhance.interceptor = null) — makes s()/e() no-ops
     * 2. Remove ClassFileTransformer from Instrumentation
     * 3. Stop Jetty server
     * 4. Clear ConfigPool
     * 5. Clear NodePool (deletes trace files)
     * 6. Null out Premain references
     */
    public static boolean unload() {
        System.out.println("Unloading java-tracer agent...");

        // Step 1: Disable interceptor FIRST (before clear() nulls weaveClass)
        try {
            Class<?> configPoolClass = LOADER.loadClass(CONFIG_POOL_CLASS);
            Method getPoolMethod = configPoolClass.getMethod("getPool");
            Object configPool = getPoolMethod.invoke(null);
            Method delMethod = configPoolClass.getMethod("delInterceptor");
            delMethod.invoke(configPool);
            System.out.println("Interceptor disabled");
        } catch (Throwable t) {
            System.out.println("Warning: Failed to disable interceptor: " + t.getMessage());
        }

        // Step 2: Remove transformer from Instrumentation
        try {
            if (instrumentation != null && transformer != null) {
                instrumentation.removeTransformer(transformer);
                System.out.println("Transformer removed from Instrumentation");
            }
        } catch (Throwable t) {
            System.out.println("Warning: Failed to remove transformer: " + t.getMessage());
        }

        // Step 3: Stop Jetty server
        try {
            Class<?> jettyClass = LOADER.loadClass(JETTY_SERVER_CLASS);
            Method shutdownMethod = jettyClass.getMethod("shutdown");
            shutdownMethod.invoke(null);
            System.out.println("Jetty server shutdown completed");
        } catch (Throwable t) {
            System.out.println("Warning: Failed to shutdown Jetty: " + t.getMessage());
        }

        // Step 4: Clear ConfigPool
        try {
            Class<?> configPoolClass = LOADER.loadClass(CONFIG_POOL_CLASS);
            Method getPoolMethod = configPoolClass.getMethod("getPool");
            Object configPool = getPoolMethod.invoke(null);
            Method clearMethod = configPoolClass.getMethod("clear");
            clearMethod.invoke(configPool);
            System.out.println("ConfigPool cleared");
        } catch (Throwable t) {
            System.out.println("Warning: Failed to clear ConfigPool: " + t.getMessage());
        }

        // Step 5: Clear NodePool (deletes trace files)
        try {
            Class<?> nodePoolClass = LOADER.loadClass(NODE_POOL_CLASS);
            Method getNodePoolMethod = nodePoolClass.getMethod("getPool");
            Object nodePool = getNodePoolMethod.invoke(null);
            Method nodeClearMethod = nodePoolClass.getMethod("clear");
            nodeClearMethod.invoke(nodePool);
            System.out.println("NodePool cleared");
        } catch (Throwable t) {
            System.out.println("Warning: Failed to clear NodePool: " + t.getMessage());
        }

        // Step 6: Null out Premain references
        INJECT.set(null);
        transformer = null;
        instrumentation = null;

        System.out.println("java-tracer agent unloaded successfully");
        return true;
    }

    /**
     * Check if the agent is currently loaded
     */
    public static boolean isLoaded() {
        return INJECT.get() != null;
    }

    private static Object loadSpyJar(Instrumentation inst, int port, int mode, boolean reload) {
        Object inject = null;

        if (reload) {
            SpyClassLoader newLoader = new SpyClassLoader(null);
            shutdownOldComponents(newLoader);
            INJECT.set(null);
            LOADER = newLoader;
            System.out.println("Created new SpyClassLoader for hot reload");
        }

        if ((inject = INJECT.get()) == null) {
            synchronized (Premain.class) {
                if ((inject = INJECT.get()) == null) {
                    String path = Premain.class.getResource(PATH).getPath();
                    if (path.endsWith(PATH)) {
                        path = path.substring(0, path.length() - PATH.length() - 1);
                    }
                    try {
                        JarInputStream jarInput = null;
                        try {
                            jarInput = new JarInputStream(new URL(path).openStream());
                            JarEntry entry = null;
                            while ((entry = jarInput.getNextJarEntry()) != null) {
                                String entryName = "/" + entry.getName();
                                if (entryName.startsWith(PATH) && entryName.endsWith(".jar")) {
                                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                                    int chunk = 0;
                                    byte[] data = new byte[256];
                                    while (-1 != (chunk = jarInput.read(data))) {
                                        bytes.write(data, 0, chunk);
                                    }
                                    data = bytes.toByteArray();
                                    LOADER.load(new ByteArrayInputStream(data, 0, data.length));
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            if (jarInput != null) {
                                try {
                                    jarInput.close();
                                } catch (IOException e1) {
                                    e1.printStackTrace();
                                }
                            }
                        }
                        Class<?> injectClass = LOADER.loadClass(SPY_CLASS);
                        INJECT.set(inject =
                            injectClass.getConstructor(Instrumentation.class, Class.class, int.class, int.class)
                                .newInstance(inst, TraceEnhance.class, port, mode));
                        System.out.println("Spy module loaded successfully");
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
            }
        }
        return inject;
    }

    /**
     * Main method for standalone attach via: java -jar java-tracer.jar -pid=xxx:-port=18902
     */
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            return;
        }

        Map<String, String> params = parseMainArgs(args);
        String pidStr = params.get(CONFIG_PID);
        if (pidStr == null) {
            System.err.println("Error: -pid parameter is required");
            printUsage();
            return;
        }

        int pid = Integer.parseInt(pidStr);
        int port = params.containsKey(CONFIG_PORT) ? Integer.parseInt(params.get(CONFIG_PORT)) : DEFAULT_PORT;

        System.out.println("Attaching to process " + pid + " with port " + port);

        // Get jar file path
        String jarPath = getJarFilePath();
        System.out.println("Agent jar: " + jarPath);

        // Extract and set up native attach library for bundled attach classes
        extractAndSetupNativeLibrary(jarPath);

        // Build agent arguments (use -key=value format matching agentmain's parseArgs)
        StringBuilder agentArgs = new StringBuilder();
        agentArgs.append(CONFIG_PORT).append("=").append(port);
        if (params.containsKey(CONFIG_MODE)) {
            agentArgs.append(":").append(CONFIG_MODE).append("=").append(params.get(CONFIG_MODE));
        }
        if (params.containsKey(CONFIG_SLEEP)) {
            agentArgs.append(":").append(CONFIG_SLEEP).append("=").append(params.get(CONFIG_SLEEP));
        }

        // Attach to target VM using JDK's built-in Attach API
        attachViaJDK(pid, jarPath, agentArgs.toString());
    }

    private static void printUsage() {
        System.out.println("Usage: java -jar java-tracer.jar -pid=<pid> [-port=<port>] [-mode=<mode>] [-sleep=<ms>]");
        System.out.println("  -pid    : Target Java process ID (required)");
        System.out.println("  -port   : Web server port (default: 18902)");
        System.out.println("  -mode   : Interceptor mode (0=default, 1=CommonThreadInterceptor, 2=CompressThreadInterceptor)");
        System.out.println("  -sleep  : Delay before transformer registration in ms (default: 1)");
        System.out.println("\nExample: java -jar java-tracer.jar -pid=12345:-port=19902:-mode=1");
    }

    private static Map<String, String> parseMainArgs(String[] args) {
        Map<String, String> params = new LinkedHashMap<>();
        for (String arg : args) {
            // Handle combined args like "-pid=123:-port=18902"
            String[] parts = arg.split(":");
            for (String part : parts) {
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
        // Handle URL encoded path: file:/D:/path/to/java-tracer.jar!/com/ali/...
        // or file:///D:/path/to/java-tracer.jar!/com/ali/...

        // Remove "file:" prefix
        if (fileName.startsWith("file:")) {
            fileName = fileName.substring(5);
        }

        // Handle Windows path with extra slashes: ///D:/ or /D:/ -> D:/
        if (fileName.startsWith("/") && fileName.length() > 2 && fileName.charAt(2) == ':') {
            fileName = fileName.substring(1);  // Remove leading /
        } else if (fileName.startsWith("///") && fileName.length() > 3 && fileName.charAt(3) == ':') {
            fileName = fileName.substring(3);  // Remove ///
        }

        // Remove JAR internal path after !
        int idx2 = fileName.indexOf("!");
        if (idx2 > 0) {
            fileName = fileName.substring(0, idx2);
        }

        // Decode URL encoding
        fileName = fileName.replace("%20", " ");

        System.out.println("Resolved jar path: " + fileName);
        return fileName;
    }

    /**
     * Attach using bundled Attach API classes (works without tools.jar dependency).
     * On JDK 9+ we try the system classloader first (jdk.attach module),
     * then fall back to our bundled classes.
     */
    private static void attachViaJDK(int pid, String jarPath, String agentArgs) throws Exception {
        VirtualMachine vm = null;
        try {
            // Try JDK's built-in VirtualMachine first (JDK 9+ jdk.attach module)
            try {
                ClassLoader cl = ClassLoader.getSystemClassLoader();
                cl.loadClass("com.sun.tools.attach.VirtualMachine");
                // JDK provides VirtualMachine natively - use it
                vm = VirtualMachine.attach(String.valueOf(pid));
            } catch (ClassNotFoundException e) {
                // JDK 8 without tools.jar - use our bundled classes
                vm = VirtualMachine.attach(String.valueOf(pid));
            }
            System.out.println("Attached to VM " + pid);

            vm.loadAgent(jarPath, agentArgs);
            System.out.println("Agent loaded successfully");

        } finally {
            if (vm != null) {
                vm.detach();
                System.out.println("Detached from VM");
            }
        }
    }

    /**
     * Extract the native attach library from the JAR to an "agent" subdirectory
     * under the JAR's parent directory, and set the "attach.lib.path" system property
     * so that LinuxVirtualMachine can load it. The "agent" directory is auto-created.
     */
    private static void extractAndSetupNativeLibrary(String jarPath) {
        String osArch = System.getProperty("os.arch");
        String osName = System.getProperty("os.name").toLowerCase();

        String libName;
        String subDir;
        if (osName.contains("windows")) {
            libName = "attach.dll";
            subDir = "windows";
        } else if (osName.contains("linux") || osName.contains("mac")) {
            if (osArch.contains("aarch64") || osArch.contains("arm64")) {
                libName = "libattach.so";
                subDir = "aarch64";
            } else {
                libName = "libattach.so";
                subDir = "amd64";
            }
        } else {
            // Unsupported platform - skip extraction, rely on JDK's own library
            return;
        }

        String entryPath = "META-INF/lib/" + subDir + "/" + libName;
        try {
            java.util.jar.JarFile jarFile = new java.util.jar.JarFile(jarPath);
            java.util.jar.JarEntry entry = jarFile.getJarEntry(entryPath);
            if (entry == null) {
                System.out.println("Native library " + entryPath + " not found in JAR, will try System.loadLibrary");
                return;
            }

            // Extract to "agent" subdirectory under the JAR's parent directory
            File jarDir = new File(jarPath).getAbsoluteFile().getParentFile();
            File agentDir = new File(jarDir, "agent");
            agentDir.mkdirs();
            File libFile = new File(agentDir, libName);

            InputStream is = jarFile.getInputStream(entry);
            try {
                FileOutputStream fos = new FileOutputStream(libFile);
                try {
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = is.read(buf)) != -1) {
                        fos.write(buf, 0, n);
                    }
                } finally {
                    fos.close();
                }
            } finally {
                is.close();
            }
            jarFile.close();

            // Set system property for LinuxVirtualMachine to find the library
            System.setProperty("attach.lib.path", libFile.getAbsolutePath());
            System.out.println("Extracted native library to: " + libFile.getAbsolutePath());
        } catch (IOException e) {
            System.out.println("Warning: Failed to extract native library: " + e.getMessage());
            // Continue - might still work via System.loadLibrary if JDK provides it
        }
    }
}