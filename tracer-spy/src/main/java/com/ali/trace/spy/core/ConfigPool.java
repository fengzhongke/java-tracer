package com.ali.trace.spy.core;

import com.ali.trace.spy.intercepter.IIntercepter;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private Class<?> weaveClass;
    private Instrumentation inst;
    private volatile ClassLoader redefineLoader;
    private volatile String redefineName;
    private EmptyTransformer emptyTransformer = new EmptyTransformer();

    public static ConfigPool getPool() {
        return INSTANCE;
    }

    public void setInst(Instrumentation inst) {
        this.inst = inst;
    }

    public boolean isRedefine(ClassLoader loader, String name){
        return loader == redefineLoader && name.equalsIgnoreCase(redefineName);
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
        } catch (Exception e) {
            e.printStackTrace();
        }
        this.redefineLoader = null;
        this.redefineName = null;
    }

    public ClassLoader redefine(int loaderId, String redefineName){
        LoaderSet loaderSet = getLoderSet(Integer.valueOf(loaderId));
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

    private LoaderSet getLoderSet(int id){
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

    public boolean setIntercepter(IIntercepter intercepter) {
        boolean set = false;
        if (weaveClass != null) {
            try {
                Method method = weaveClass.getDeclaredMethod("setIntecepter", Object.class);
                Object setObj = method.invoke(null, intercepter);
                if (setObj != null && Boolean.TRUE.equals(setObj)) {
                    set = true;
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        return set;
    }

    public boolean delIntercepter() {
        boolean del = false;
        if (weaveClass != null) {
            try {
                Method method = weaveClass.getDeclaredMethod("delIntercepter");
                method.invoke(null);
                del = true;
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        return del;
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
