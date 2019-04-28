package com.ali.trace.spy.jetty.handler;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ali.trace.spy.core.ConfigPool;
import com.ali.trace.spy.intercepter.IIntercepter;
import com.ali.trace.spy.intercepter.ThreadCompressIntercepter;
import com.ali.trace.spy.jetty.ModuleHttpServlet.ITracerHttpHandler;
import com.ali.trace.spy.jetty.ModuleHttpServlet.TracerPath;
import com.ali.trace.spy.util.TreeNode;

public class TraceHandler extends ITracerHttpHandler {

    private volatile IIntercepter intercepter;

    @TracerPath(value="/trace/set", params="type(can be compressThread),class,method", desc="set intercepter ")
    public void set(HttpServletRequest req, HttpServletResponse res) throws IOException {
        PrintWriter writer = res.getWriter();
        String type = req.getParameter("type");
        if ("compressThread".equals(type)) {
            String cname = req.getParameter("class");
            String mname = req.getParameter("method");
            boolean set = false;
            if (cname != null && mname != null) {
                intercepter = new ThreadCompressIntercepter(null, cname, mname);
                set = ConfigPool.getPool().setIntercepter(intercepter);
            }
            writer.write("set class:[" + cname + "]method:[" + mname + "]status:[" + set + "]");
        }
    }

    @TracerPath(value = "/trace/get", desc = "get xml trade of the method")
    public void get(HttpServletRequest req, HttpServletResponse res) throws IOException, InterruptedException {
        PrintWriter writer = res.getWriter();
        if (intercepter != null && intercepter instanceof ThreadCompressIntercepter) {
            TreeNode node = ((ThreadCompressIntercepter)intercepter).getNode();
            if (node != null) {
                writer.write("<?xml version='1.0' encoding='UTF-8' ?>");
                node.writeFile(writer);
            } else {
                writer.write("no result no record");
            }
        } else {
            writer.write("no result intercepter is null");
        }
    }

    @TracerPath(value = "/trace/del", desc = "delete the intercepter")
    public void del(HttpServletRequest req, HttpServletResponse res) throws IOException {
        PrintWriter writer = res.getWriter();
        IIntercepter old = intercepter;
        intercepter = null;
        boolean del = ConfigPool.getPool().delIntercepter();
        writer.write("del intercepter:[" + old + "]status:[" + del + "]");
    }
}
