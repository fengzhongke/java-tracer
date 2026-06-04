package com.ali.trace.spy.jetty;

import com.ali.trace.spy.jetty.support.HandlerConfig;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import java.net.InetSocketAddress;

/**
 * @author nkhanlang@163.com
 */
public class JettyServer {

    private static Server currentServer;

    public JettyServer(int port) {

        final ServletContextHandler context = new ServletContextHandler(null, ModuleHttpServlet.ROOT, ServletContextHandler.NO_SESSIONS);

        context.setClassLoader(JettyServer.class.getClassLoader());
        final String pathSpec = "/*";
        // Create fresh HandlerConfig instance (not using singleton) for hot reload support
        HandlerConfig handlerConfig = HandlerConfig.createNew();
        context.addServlet(new ServletHolder(new ModuleHttpServlet(handlerConfig)), pathSpec);

        Server httpServer = new Server(new InetSocketAddress(port));
        if (httpServer.getThreadPool() instanceof QueuedThreadPool) {
            final QueuedThreadPool qtp = (QueuedThreadPool)httpServer.getThreadPool();
            qtp.setName("tracer-jetty-qtp" + qtp.hashCode());
        }
        httpServer.setHandler(context);
        try {
            httpServer.start();
            currentServer = httpServer;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
        }
    }

    /**
     * Shutdown the current Jetty server (for hot reload)
     */
    public static void shutdown() {
        if (currentServer != null) {
            try {
                currentServer.stop();
                // Wait for server to fully stop
                while (!currentServer.isStopped()) {
                    Thread.sleep(100);
                }
                System.out.println("Jetty server shutdown completed");
            } catch (Exception e) {
                System.out.println("Jetty shutdown error: " + e.getMessage());
            }
            currentServer = null;
        }
    }

    /**
     * Check if server is running
     */
    public static boolean isRunning() {
        return currentServer != null && currentServer.isStarted();
    }

    public static void main(String[] args) {
        {
            int port = 8080;
            new JettyServer(port);
        }
    }
}