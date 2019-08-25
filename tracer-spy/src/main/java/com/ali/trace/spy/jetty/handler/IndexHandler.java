package com.ali.trace.spy.jetty.handler;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * @author nkhanlang@163.com
 */
public class IndexHandler implements ITraceHttpHandler {

    @TracerPath(value = "/index", order = 1)
    @TraceView
    public String getInfo(PrintWriter writer) throws IOException {
        return "index";
    }
}
