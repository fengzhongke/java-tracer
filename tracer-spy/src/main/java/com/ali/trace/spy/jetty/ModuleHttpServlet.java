package com.ali.trace.spy.jetty;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ModuleHttpServlet extends HttpServlet {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public static final String ROOT = "/tracer";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doMethod(req, resp, "get");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doMethod(req, resp, "post");
    }

    private void doMethod(final HttpServletRequest req, final HttpServletResponse resp, final String method)
        throws ServletException, IOException {

        // 获取请求路径
        final String path = req.getPathInfo();
        final PrintWriter writer = resp.getWriter();
        Module moduleInfo = ITracerHttpHandler.moduleMap.get(path);

        if (moduleInfo == null && ITracerHttpHandler.defaultModule != null) {
            moduleInfo = ITracerHttpHandler.defaultModule;
        }
        if (moduleInfo != null) {
            // 生成方法调用参数
            final Object[] args = generateParameterObjectArray(moduleInfo.method, req, resp);
            try {
                Object ret = moduleInfo.method.invoke(moduleInfo.httpHandler, args);
                if (ret != null) {
                    writer.write(ret.toString());
                }
            } catch (Throwable e) {
                e.printStackTrace(writer);
            }
        } else {
            writer.write("path with[" + path + "]has no handler");
        }

    }

    /**
     * 生成方法请求参数数组 主要用于填充HttpServletRequest和HttpServletResponse
     *
     * @param method 模块Java方法
     * @param req HttpServletRequest
     * @param resp HttpServletResponse
     * @return 请求方法参数列表
     */
    private Object[] generateParameterObjectArray(final Method method, final HttpServletRequest req,
        final HttpServletResponse resp) {

        final Class<?>[] parameterTypeArray = method.getParameterTypes();
        if (parameterTypeArray != null && parameterTypeArray.length == 0) {
            return null;
        }
        final Object[] parameterObjectArray = new Object[parameterTypeArray.length];
        for (int index = 0; index < parameterObjectArray.length; index++) {
            final Class<?> parameterType = parameterTypeArray[index];

            // HttpServletRequest
            if (HttpServletRequest.class.isAssignableFrom(parameterType)) {
                parameterObjectArray[index] = req;
                continue;
            }

            // HttpServletResponse
            if (HttpServletResponse.class.isAssignableFrom(parameterType)) {
                parameterObjectArray[index] = resp;
                continue;
            }
        }
        return parameterObjectArray;
    }

    public static class ITracerHttpHandler {
        private static Module defaultModule;
        private static Map<String, Module> moduleMap = new HashMap<String, Module>();

        static {
            new InfoHandler();
        }

        protected ITracerHttpHandler() {
            Class<?> clasz = getClass();
            Method[] methods = clasz.getDeclaredMethods();
            for (Method method : methods) {
                TracerPath tracerPath = method.getAnnotation(TracerPath.class);
                if (tracerPath != null) {
                    String path = tracerPath.value();
                    String params = tracerPath.params();
                    String desc = tracerPath.desc();

                    if (path != null && (path = path.trim()).length() > 0) {
                        if (path.charAt(0) != '/') {
                            path = "/" + path;
                        }
                        Module module = new Module(path, method, this, params, desc);
                        moduleMap.put(path, module);
                        if (clasz == InfoHandler.class) {
                            defaultModule = module;
                        }
                    }
                }
            }
        }

        static class InfoHandler extends ITracerHttpHandler {
            @TracerPath(value = "/info", desc = "get all handlers")
            public void getInfo(HttpServletResponse res) throws IOException {
                PrintWriter writer = res.getWriter();
                writer.write("<?xml version='1.0' encoding='UTF-8' ?>");
                writer.write("<handlers cnt='" + moduleMap.size() + "'>");
                for (Entry<String, Module> entry : moduleMap.entrySet()) {
                    writer.write("<handler path='" + ROOT + entry.getKey() + "' params='"
                        + entry.getValue().params + "' desc='" + entry.getValue().desc + "'/>");
                }
                writer.write("</handlers>");
            }
        }
    }

    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    public @interface TracerPath {
        String value() default "";

        String params() default "";

        String desc() default "";
    }

    public static class Module {
        // private String path;
        private Method method;
        private ITracerHttpHandler httpHandler;
        private String params;
        private String desc;

        public Module(String path, Method method, ITracerHttpHandler httpHandler, String params, String desc) {
            // this.path = path;
            this.method = method;
            this.httpHandler = httpHandler;
            this.params = params;
            this.desc = desc;
        }
    }
}
