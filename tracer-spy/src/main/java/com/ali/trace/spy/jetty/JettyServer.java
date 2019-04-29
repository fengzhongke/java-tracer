package com.ali.trace.spy.jetty;

import static org.eclipse.jetty.servlet.ServletContextHandler.NO_SESSIONS;

import java.net.InetSocketAddress;
import java.util.ResourceBundle;

import javax.servlet.Servlet;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import com.ali.trace.spy.jetty.handler.ClassHandler;
import com.ali.trace.spy.jetty.handler.ThreadHandler;
import com.ali.trace.spy.jetty.handler.TraceHandler;

public class JettyServer {

    public JettyServer(int port) {
        new ThreadHandler();
        new ClassHandler();
        new TraceHandler();

        System.out.println("resource : [" + Servlet.class.getResource("LocalStrings.properties") + "]");
        System.out.println("resource : [" + ResourceBundle.getBundle("javax.servlet.LocalStrings") + "]");
        
        final ServletContextHandler context = new ServletContextHandler(NO_SESSIONS);

        context.setContextPath(ModuleHttpServlet.ROOT);
        context.setClassLoader(JettyServer.class.getClassLoader());
        final String pathSpec = "/*";
        context.addServlet(new ServletHolder(new ModuleHttpServlet()), pathSpec);

        Server httpServer = new Server(new InetSocketAddress(port));
        if (httpServer.getThreadPool() instanceof QueuedThreadPool) {
            final QueuedThreadPool qtp = (QueuedThreadPool)httpServer.getThreadPool();
            qtp.setName("tracer-jetty-qtp" + qtp.hashCode());
        }
        httpServer.setHandler(context);
        try {
            httpServer.start();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
        }
    }

    public static void main(String[] args) {
        new JettyServer(8080);
    }
}
