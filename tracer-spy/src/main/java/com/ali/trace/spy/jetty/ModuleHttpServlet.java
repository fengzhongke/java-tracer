package com.ali.trace.spy.jetty;

import com.ali.trace.spy.jetty.handler.ITraceHttpHandler.ModelMap;
import com.ali.trace.spy.jetty.handler.ITraceHttpHandler.TraceParam;
import com.ali.trace.spy.jetty.handler.ITraceHttpHandler.TraceView;
import com.ali.trace.spy.jetty.io.VmViewResolver;
import com.ali.trace.spy.jetty.support.HandlerConfig;
import com.ali.trace.spy.jetty.support.Module;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * @author nkhanlang@163.com
 */
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

        final PrintWriter writer = resp.getWriter();
        if (module != null) {
            Method method = module.getMethod();
            try {
                Object[] params = generateParams(method, req, resp);
                Object ret = method.invoke(module.getHttpHandler(), params);
                if (ret != null) {
                    TraceView view = method.getAnnotation(TraceView.class);
                    if(ret instanceof String && view != null){
                        String viewPath = "static/vm/" + ret + ".vm";
                        Map<String, Object> model = new HashMap<String, Object>();
                        for(Object param : params){
                            if(param instanceof ModelMap){
                                model.putAll((ModelMap)param);
                            }
                        }
                        VmViewResolver.resolve(viewPath, model, writer);
                    }else{
                        writer.write(ret.toString());
                    }
                }
            } catch (Throwable e) {
                e.printStackTrace(writer);
            }
        } else {
            writer.write("path with[" + path + "]has no handler");
        }
    }

    private Object[] generateParams(final Method method, final HttpServletRequest req,
        final HttpServletResponse resp) {
        final Class<?>[] paramTypes = method.getParameterTypes();
        if (paramTypes != null && paramTypes.length > 0) {
            final Object[] params = new Object[paramTypes.length];
            final Annotation[][] annos = method.getParameterAnnotations();
            for (int index = 0; index < params.length; index++) {
                final Class<?> paramType = paramTypes[index];
                if (HttpServletRequest.class.isAssignableFrom(paramType)) {
                    params[index] = req;
                }else if (HttpServletResponse.class.isAssignableFrom(paramType)) {
                    params[index] = resp;
                }else if(PrintWriter.class.isAssignableFrom(paramType)) {
                    try {
                        params[index] = resp.getWriter();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }else if(String.class.isAssignableFrom(paramType) && annos[index] != null && annos[index].length > 0){
                    for(Annotation anno : annos[index]){
                        if(anno.annotationType() == TraceParam.class){
                            String name = ((TraceParam)anno).value();
                            if(name != null){
                                String value = req.getParameter(name);
                                if(value != null){
                                    params[index] = value.trim();
                                }
                            }
                        }
                    }
                }else if(ModelMap.class.isAssignableFrom(paramType)){
                    params[index] = new ModelMap();
                }
            }
            return params;
        }
        return null;
    }

}
