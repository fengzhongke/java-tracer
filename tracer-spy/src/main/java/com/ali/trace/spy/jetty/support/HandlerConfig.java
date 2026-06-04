package com.ali.trace.spy.jetty.support;

import com.ali.trace.spy.jetty.handler.ClassHandler;
import com.ali.trace.spy.jetty.handler.ITraceHttpHandler;
import com.ali.trace.spy.jetty.handler.ITraceHttpHandler.TracerPath;
import com.ali.trace.spy.jetty.handler.IndexHandler;
import com.ali.trace.spy.jetty.handler.PackageHandler;
import com.ali.trace.spy.jetty.handler.StaticHandler;
import com.ali.trace.spy.jetty.handler.ThreadHandler;
import com.ali.trace.spy.jetty.handler.TraceHandler;
import com.ali.trace.spy.jetty.handler.UnloadHandler;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author nkhanlang@163.com
 * @date 2019-08-21 23:26
 */
public class HandlerConfig {

    private static HandlerConfig INSTANCE;
    private Module defaultModule;
    private Set<Module> modules = new TreeSet<Module>();

    private HandlerConfig() {
        ITraceHttpHandler handler = new IndexHandler();
        addHandler(handler);
        setDefaultModule(handler);
        addHandler(new ThreadHandler());
        addHandler(new ClassHandler());
        addHandler(new TraceHandler());
        addHandler(new StaticHandler());
        addHandler(new PackageHandler());
        addHandler(new UnloadHandler());
    }

    public static HandlerConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new HandlerConfig();
        }
        return INSTANCE;
    }

    /**
     * Create new instance for hot reload
     */
    public static HandlerConfig createNew() {
        return new HandlerConfig();
    }

    public Set<Module> getModules() {
        return modules;
    }

    public Module getDefaultModule() {
        return defaultModule;
    }

    public void addHandler(ITraceHttpHandler handler) {
        Class<?> clasz = handler.getClass();
        Method[] methods = clasz.getDeclaredMethods();
        for (Method method : methods) {
            TracerPath tracerPath = method.getAnnotation(TracerPath.class);
            if (tracerPath != null) {
                String path = tracerPath.value();
                if (path != null && (path = path.trim()).length() > 0) {
                    if (path.charAt(0) != '/') {
                        path = "/" + path;
                    }
                    Module module = new Module(path, method, handler, tracerPath.order());
                    modules.add(module);
                }
            }
        }
    }

    public void setDefaultModule(ITraceHttpHandler handler) {
        for (Module module : modules) {
            if (module.getHttpHandler() == handler) {
                this.defaultModule = module;
            }
        }
    }
}
