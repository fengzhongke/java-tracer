package com.ali.trace.spy.core;

import com.ali.trace.spy.inject.TraceInjector;
import com.ali.trace.spy.interceptor.IInterceptor;

import org.apache.commons.lang.StringUtils;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
/**
 * @author nkhanlang@163.com
 */
public class ConfigPool {

    private static final ConfigPool INSTANCE = new ConfigPool();
    private final AtomicInteger SEQ_SEED = new AtomicInteger(0);

    private CopyOnWriteArrayList<LoaderSet> loaderSets = new CopyOnWriteArrayList<LoaderSet>();
    // Stable ClassLoader -> ID mapping, ensures same ClassLoader always gets same ID
    private ConcurrentHashMap<ClassLoader, Integer> loaderIdMap = new ConcurrentHashMap<ClassLoader, Integer>();
    private Class<?> weaveClass;
    private Instrumentation inst;
    private TraceInjector injector;
    private volatile ClassLoader redefineLoader;
    private volatile String redefineName;
    private EmptyTransformer emptyTransformer = new EmptyTransformer();

    // Retransform progress tracking
    private volatile int progressTotal = 0;
    private volatile int progressDone = 0;
    private volatile int progressFailed = 0;
    private volatile String progressStatus = "idle"; // idle, running, complete

    public static ConfigPool getPool() {
        return INSTANCE;
    }

    /**
     * Clear all data (for hot reload)
     */
    public void clear() {
        loaderSets.clear();
        loaderIdMap.clear();
        weaveClass = null;
        inst = null;
        injector = null;
        SEQ_SEED.set(0);
    }

    /**
     * Unload the agent: disable interceptor, remove transformer, stop Jetty, clear pools.
     * Uses reflection to call Premain.unload() across the classloader boundary.
     * Premain is loaded by the bootstrap classloader (Boot-Class-Path in manifest),
     * so we use Class.forName with null (bootstrap) classloader to find it.
     */
    public boolean unload() {
        try {
            // Premain and TraceEnhance are both on the bootstrap classpath
            // (Boot-Class-Path: java-tracer.jar), so use bootstrap loader (null)
            Class<?> premainClass = Class.forName("com.ali.trace.agent.main.Premain", true, null);
            java.lang.reflect.Method unloadMethod = premainClass.getMethod("unload");
            return (Boolean) unloadMethod.invoke(null);
        } catch (Throwable e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Get all loaded classes from JVM (not just transformer-processed ones)
     */
    public List<LoaderSet> getAllLoadedClasses() {
        if (inst == null) {
            return loaderSets;
        }

        // Build a map of ClassLoader -> List<Class>
        Map<ClassLoader, List<Class<?>>> loaderClassMap = new ConcurrentHashMap<ClassLoader, List<Class<?>>>();
        Class<?>[] allClasses = inst.getAllLoadedClasses();

        for (Class<?> clazz : allClasses) {
            ClassLoader loader = clazz.getClassLoader();
            // Skip bootstrap loader (null) and array types
            if (loader != null && !clazz.isArray()) {
                List<Class<?>> classes = loaderClassMap.get(loader);
                if (classes == null) {
                    classes = new ArrayList<Class<?>>();
                    loaderClassMap.put(loader, classes);
                }
                classes.add(clazz);
            }
        }

        // Build result list
        List<LoaderSet> result = new ArrayList<LoaderSet>();
        for (Map.Entry<ClassLoader, List<Class<?>>> entry : loaderClassMap.entrySet()) {
            ClassLoader loader = entry.getKey();
            List<Class<?>> classes = entry.getValue();

            // Find existing LoaderSet or assign stable ID
            LoaderSet loaderSet = getLoaderSet(loader);
            int loaderId;
            if (loaderSet != null) {
                loaderId = loaderSet.getId();
            } else {
                // Use stable id map: same ClassLoader always gets same id
                Integer existingId = loaderIdMap.get(loader);
                if (existingId != null) {
                    loaderId = existingId;
                    // Also create a LoaderSet entry for this known loader
                    loaderSet = new LoaderSet(loaderId, loader);
                } else {
                    loaderId = SEQ_SEED.incrementAndGet();
                    loaderIdMap.put(loader, loaderId);
                    loaderSet = new LoaderSet(loaderId, loader);
                }
            }

            // Create a new LoaderSet with all classes
            LoaderSet fullSet = new LoaderSet(loaderSet.getId(), loader);
            for (Class<?> clazz : classes) {
                String name = clazz.getName().replace('.', '/');
                // Check if this class was woven (type=1) or not (type=0)
                Integer existingType = loaderSet.classNames.get(name);
                int type = (existingType != null && existingType == 1) ? 1 : 0;
                fullSet.classNames.put(name, type);
            }
            result.add(fullSet);
        }

        // Sort by class count
        Collections.sort(result, new java.util.Comparator<LoaderSet>() {
            public int compare(LoaderSet o1, LoaderSet o2) {
                return o2.classNames.size() - o1.classNames.size();
            }
        });

        return result;
    }

    public void setInst(Instrumentation inst, TraceInjector injector) {
        this.inst = inst;
        this.injector = injector;
    }

    public Instrumentation getInst() {
        return inst;
    }

    public boolean isRedefine(ClassLoader loader, String name){
        return loader == redefineLoader && name.equalsIgnoreCase(redefineName);
    }

    public void resetConfig(String includes, String excludes) {
        List<String> configLines = new ArrayList<>();
        configLines.add("include:");
        Collections.addAll(configLines, includes.split(";"));
        configLines.add("exclude:");
        Collections.addAll(configLines, excludes.split(";"));
        injector.setConfig(configLines);

        // Use getAllLoadedClasses to get all classes in JVM
        List<LoaderSet> allLoaders = getAllLoadedClasses();
        for(LoaderSet loaderSet : allLoaders){
            ClassLoader loader = loaderSet.getLoader();
            for (Map.Entry<String, Integer> entry : loaderSet.classNames.entrySet()) {
                String name = entry.getKey();
                boolean shouldWeave = !injector.filter(name);
                int currentType = entry.getValue();

                // If should weave but not yet woven, or should not weave but already woven
                if (shouldWeave && currentType == 0) {
                    redefine(loader, name);
                } else if (!shouldWeave && currentType == 1) {
                    redefine(loader, name);
                }
            }
        }
    }

    /**
     * Incrementally add/remove a prefix from include/exclude config and retransform all affected classes.
     * The prefix change is applied globally; the retransform runs asynchronously with progress tracking.
     * @param action one of "addInclude", "addExclude", "removeInclude", "removeExclude"
     * @param prefix the prefix to add/remove (e.g., "com/example/api")
     */
    public void incrementalConfig(String action, String prefix) {
        if (injector == null) return;

        switch (action) {
            case "addInclude":
                injector.addIncludePrefix(prefix);
                break;
            case "addExclude":
                injector.addExcludePrefix(prefix);
                break;
            case "removeInclude":
                injector.removeIncludePrefix(prefix);
                break;
            case "removeExclude":
                injector.removeExcludePrefix(prefix);
                break;
            default:
                return;
        }

        // Collect classes that need retransform across all loaders
        final List<LoaderSet> allLoaders = getAllLoadedClasses();
        final List<RedefineTask> tasks = new ArrayList<>();
        for (LoaderSet loaderSet : allLoaders) {
            ClassLoader loader = loaderSet.getLoader();
            for (Map.Entry<String, Integer> entry : loaderSet.classNames.entrySet()) {
                String name = entry.getKey();
                if (name.startsWith(prefix) || prefix.equalsIgnoreCase("*")) {
                    boolean shouldWeave = !injector.filter(name);
                    int currentType = entry.getValue();
                    if (shouldWeave && currentType == 0) {
                        tasks.add(new RedefineTask(loader, name));
                    } else if (!shouldWeave && currentType == 1) {
                        tasks.add(new RedefineTask(loader, name));
                    }
                }
            }
        }

        // Start async retransform with progress tracking
        progressTotal = tasks.size();
        progressDone = 0;
        progressFailed = 0;
        progressStatus = "running";

        new Thread(new Runnable() {
            public void run() {
                for (RedefineTask task : tasks) {
                    try {
                        redefine(task.loader, task.name);
                        progressDone++;
                    } catch (Throwable e) {
                        progressFailed++;
                        progressDone++;
                    }
                }
                progressStatus = "complete";
            }
        }, "retransform-thread").start();
    }

    /**
     * Get current retransform progress
     */
    public RetransformProgress getProgress() {
        return new RetransformProgress(progressTotal, progressDone, progressFailed, progressStatus);
    }

    /**
     * Internal task holder for async retransform
     */
    private static class RedefineTask {
        final ClassLoader loader;
        final String name;
        RedefineTask(ClassLoader loader, String name) {
            this.loader = loader;
            this.name = name;
        }
    }

    /**
     * Simple progress data holder (no VO dependency)
     */
    public static class RetransformProgress {
        public final int total;
        public final int done;
        public final int failed;
        public final String status;
        RetransformProgress(int total, int done, int failed, String status) {
            this.total = total;
            this.done = done;
            this.failed = failed;
            this.status = status;
        }
        @Override
        public String toString() {
            return "{\"total\":" + total + ",\"done\":" + done + ",\"failed\":" + failed + ",\"status\":\"" + status + "\"}";
        }
    }

    /**
     * Get current include/exclude prefix sets for UI display
     * @return array of [includePrefixSet, excludePrefixSet]
     */
    public Set<String>[] getConfigPrefixes() {
        if (injector == null) {
            return new Set[]{new HashSet<>(), new HashSet<>()};
        }
        return new Set[]{injector.getIncludePrefixes(), injector.getExcludePrefixes()};
    }

    public List<String> getConfig() {
        Set<String> includes = new HashSet<>();
        Set<String> excludes = new HashSet<>();
        Set<String> parts = null;
        for (String line : injector.getConfig()) {
            if (line.contains("include:")) {
                parts = includes;
            } else if (line.contains("exclude:")) {
                parts = excludes;
            } else if (parts != null && StringUtils.isNotBlank(line.trim())) {
                parts.add(line.trim());
            }
        }
        List<String> lines = new ArrayList<>();
        lines.add(StringUtils.join(includes.toArray(), ";"));
        lines.add(StringUtils.join(excludes.toArray(), ";"));
        return lines;
    }

    public List<String> getRedefinedNames() {
        return injector.getRedefinedNames();
    }

    public void redefine(int type){
        for(LoaderSet loaderSet : loaderSets){
            ClassLoader loader = loaderSet.getLoader();
            for(String name : loaderSet.classNames.keySet()){
                if(type == loaderSet.classNames.get(name)){
                   redefine(loader, name);
                }
            }
        }
    }

    private synchronized void redefine(ClassLoader loader, String name){
        this.redefineLoader = loader;
        this.redefineName = name;
        try {
            inst.retransformClasses(loader.loadClass(name.replace("/", ".")));
        } catch (Throwable e) {
            System.out.println("redefine error [" + name + "]");
            e.printStackTrace();
        }
        this.redefineLoader = null;
        this.redefineName = null;
    }

    public ClassLoader redefine(int loaderId, String redefineName){
        LoaderSet loaderSet = getLoaderSet(Integer.valueOf(loaderId));
        ClassLoader loader =null;
        if(loaderSet != null) {
            loader = loaderSet.getLoader();
            for (String name : loaderSet.classNames.keySet()) {
                if(name.equalsIgnoreCase(redefineName)){
                    redefine(loader, name);
                    break;
                }
            }
        }
        return loader;
    }

    private LoaderSet getLoaderSet(ClassLoader loader){
        LoaderSet loaderSet = null;;
        for (LoaderSet set : loaderSets) {
            if (set.loader == loader) {
                loaderSet = set;
            }
        }
        return loaderSet;
    }

    public void addClass(ClassLoader loader, String className, Integer type) {
        LoaderSet loaderSet = null;
        if ((loaderSet = getLoaderSet(loader)) == null) {
            synchronized (loaderSets){
                if ((loaderSet = getLoaderSet(loader)) == null) {
                    loaderSets.add(loaderSet = new LoaderSet(SEQ_SEED.incrementAndGet(), loader));
                }
            }
        }
        loaderSet.classNames.put(className, type);
    }

    public List<LoaderSet> getLoaderSets(){
        return new ArrayList<LoaderSet>(loaderSets);
    }

    public LoaderSet getLoaderSet(int id){
        LoaderSet loaderSet = null;;
        for (LoaderSet set : loaderSets) {
            if (set.id == id) {
                loaderSet = set;
            }
        }
        return loaderSet;
    }

    public void setWeaveClass(Class<?> weaveClass) {
        if (weaveClass != null) {
            this.weaveClass = weaveClass;
        }
    }

    public boolean setinterceptor(IInterceptor interceptor) {
        boolean set = false;
        if (weaveClass != null) {
            try {
                Method method = weaveClass.getDeclaredMethod("setInterceptor", Object.class);
                Object setObj = method.invoke(null, interceptor);
                if (setObj != null && Boolean.TRUE.equals(setObj)) {
                    set = true;
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        return set;
    }

    public boolean delInterceptor() {
        boolean del = false;
        if (weaveClass != null) {
            try {
                Method method = weaveClass.getDeclaredMethod("delInterceptor");
                method.invoke(null);
                del = true;
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        return del;
    }

    public IInterceptor getInterceptor() {
        IInterceptor interceptor = null;
        if (weaveClass != null) {
            try {
                Method method = weaveClass.getDeclaredMethod("getInterceptor");
                interceptor = (IInterceptor) method.invoke(null);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        return interceptor;
    }

    public class LoaderSet {
        final int id;
        final ClassLoader loader;
        Map<String, Integer> classNames = new ConcurrentHashMap<String, Integer>();
        private LoaderSet(int id, ClassLoader loader) {
            this.id=id;
            this.loader = loader;
        }
        public int getId() {
            return id;
        }
        public ClassLoader getLoader() {
            return loader;
        }
        public Map<String, Integer> getClassNames() {
            return classNames;
        }
    }

    public static class EmptyTransformer implements ClassFileTransformer{

        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
            System.out.println("empty:" + className);
            return classfileBuffer;
        }
    }
}
