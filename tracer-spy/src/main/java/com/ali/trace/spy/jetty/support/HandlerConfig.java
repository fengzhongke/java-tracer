package com.ali.trace.spy.jetty.support;

import com.ali.trace.spy.jetty.ModuleHttpServlet;
import com.ali.trace.spy.jetty.ModuleHttpServlet.TracerPath;
import com.ali.trace.spy.jetty.handler.ClassHandler;
import com.ali.trace.spy.jetty.handler.ITraceHttpHandler;
import com.ali.trace.spy.jetty.handler.IndexHandler;
import com.ali.trace.spy.jetty.handler.StaticHandler;
import com.ali.trace.spy.jetty.handler.ThreadHandler;
import com.ali.trace.spy.jetty.handler.TraceHandler;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.TreeSet;

/**
 * @auther hanlang@mallcai.com
 * @date 2019-08-21 23:26
 */
public class HandlerConfig {

    private static final HandlerConfig INSTANCE = new HandlerConfig();
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
    }

    public static HandlerConfig getInstance() {
        return INSTANCE;
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
            ModuleHttpServlet.TracerPath tracerPath = method.getAnnotation(TracerPath.class);
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
