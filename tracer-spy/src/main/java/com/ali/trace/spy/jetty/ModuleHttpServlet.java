package com.ali.trace.spy.jetty;

import com.ali.trace.spy.jetty.support.HandlerConfig;
import com.ali.trace.spy.jetty.support.Module;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ModuleHttpServlet extends HttpServlet {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public static final String ROOT = "";
    private HandlerConfig config;
    public ModuleHttpServlet(HandlerConfig config){
        this.config = config;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doMethod(req, resp, "get");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doMethod(req, resp, "post");
    }

    private void doMethod(final HttpServletRequest req, final HttpServletResponse resp, final String requestMethod)
        throws ServletException, IOException {
        final String path = req.getPathInfo();
        final PrintWriter writer = resp.getWriter();
        Module module = null;
        for(Module m : config.getModules()){
            if(m.match(path)){
                module = m;
                break;
            }
        }

        if (module == null) {
            module = config.getDefaultModule();
        }

        if (module != null) {
            // 生成方法调用参数
            Method method = module.getMethod();
            final Object[] args = generateParameterObjectArray(method, req, resp);
            try {
                Object ret = method.invoke(module.getHttpHandler(), args);
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

            if(PrintWriter.class.isAssignableFrom(parameterType)) {
                try {
                    parameterObjectArray[index] = resp.getWriter();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                continue;
            }
        }
        return parameterObjectArray;
    }

    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    public @interface TracerPath {
        String value() default "";
        int order() default 0;
    }

    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    public @interface JsonData {
    }


    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    public @interface ViewName {
    }

}
