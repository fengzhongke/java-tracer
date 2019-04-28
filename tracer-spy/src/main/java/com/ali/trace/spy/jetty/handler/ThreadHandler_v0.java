package com.ali.trace.spy.jetty.handler;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Stack;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ali.trace.spy.jetty.ModuleHttpServlet.ITracerHttpHandler;

public class ThreadHandler_v0 extends ITracerHttpHandler {

    //@TracerPath("/thread")
    public void thread(HttpServletRequest req, HttpServletResponse res) throws IOException {
        PrintWriter writer = res.getWriter();
        writer.write("<?xml version='1.0' encoding='UTF-8' ?>");
        Map<Thread, StackTraceElement[]> stackTraces = Thread.getAllStackTraces();
        writer.write("<threads cnt='" + stackTraces.size() + "'>");
        for (Entry<Thread, StackTraceElement[]> entry : stackTraces.entrySet()) {
            Thread thread = entry.getKey();
            StackTraceElement[] stacks = entry.getValue();
            Stack<String> stack = new Stack<String>();
            int size = stacks.length;
            for (size = stacks.length - 1; size >= 0; size--) {
                StackTraceElement stackTrace = stacks[size];
                String mname = stackTrace.getMethodName().replaceAll("\\$", "_");
                String cname = stackTrace.getClassName().replaceAll("\\$", "_");
                int line = stackTrace.getLineNumber();

                writer.write("<" + mname + " name='" + cname + "' line='" + line + "'>");
                writer.write("\r\n");
                stack.push("</" + mname + ">");
            }
            writer.write("<thread name='" + thread.getName() + "' id='" + thread.getId() + "'>");
            stack.push("</thread>");
            while (!stack.isEmpty()) {
                writer.write(stack.pop());
                writer.write("\r\n");
            }
        }
        writer.write("</threads>");
    }

}
