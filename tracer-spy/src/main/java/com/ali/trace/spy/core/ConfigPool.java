package com.ali.trace.spy.core;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.ali.trace.spy.intercepter.IIntercepter;

public class ConfigPool {

    private static final ConfigPool POOL = new ConfigPool();

    private CopyOnWriteArrayList<LoaderSet> loaderSets = new CopyOnWriteArrayList<LoaderSet>();
    private Class<?> weaveClass;

    public static ConfigPool getPool() {
        return POOL;
    }

    public List<ClassLoader> getLoaders() {
        List<ClassLoader> loaders = new ArrayList<ClassLoader>();
        for (LoaderSet loaderSet : loaderSets) {
            loaders.add(loaderSet.loader);
        }
        return loaders;
    }

    public Map<String, Integer> getClassNames(ClassLoader loader) {
        LoaderSet loaderSet = getLoaderSet(loader);
        return new TreeMap<String, Integer>(loaderSet.classNames);
    }

    public void addClass(ClassLoader loader, String className, Integer type) {
        LoaderSet loaderSet = getLoaderSet(loader);
        try {
            loaderSet.classNames.put(className, type);
        } catch (NullPointerException n) {
            n.printStackTrace();
        }
    }

    private LoaderSet getLoaderSet(ClassLoader loader) {
        LoaderSet useLoaderSet = null;;
        for (LoaderSet loaderSet : loaderSets) {
            if (loaderSet.loader == loader) {
                useLoaderSet = loaderSet;
            }
        }
        if (useLoaderSet == null) {
            loaderSets.add(useLoaderSet = new LoaderSet(loader));
        }
        return useLoaderSet;
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

    class LoaderSet {
        ClassLoader loader;
        Map<String, Integer> classNames = new ConcurrentHashMap<String, Integer>();

        LoaderSet(ClassLoader loader) {
            this.loader = loader;
        }
    }
}
