package com.ali.trace.spy.jetty.handler;

import com.ali.trace.spy.jetty.ModuleHttpServlet.TracerPath;
import com.ali.trace.spy.jetty.io.StaticResResolver;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.regex.Pattern;

public class StaticHandler implements ITraceHttpHandler {

    @TracerPath(value="/static/((js)|(css))/.*", order=100)
    public void set(HttpServletRequest req, PrintWriter writer) throws IOException {
        String path = req.getPathInfo();
        if(path.charAt(0) == '/'){
            path = path.substring(1);
        }
        StaticResResolver.resolve(path, writer);
    }

    public static void main(String[] args){
        System.out.println(Pattern.compile("^/static/js/.*$").matcher("/static/js/home.js").find());
    }
}
