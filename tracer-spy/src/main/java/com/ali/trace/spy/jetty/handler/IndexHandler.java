package com.ali.trace.spy.jetty.handler;

import com.ali.trace.spy.jetty.ModuleHttpServlet.TracerPath;
import com.ali.trace.spy.jetty.io.VmViewResolver;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;

public class IndexHandler implements ITraceHttpHandler {

    @TracerPath(value = "/index", order = 1)
    public void getInfo(PrintWriter writer) throws IOException {
        VmViewResolver.resolve("static/vm/index.vm", new HashMap<String, Object>(), writer);
    }
}
