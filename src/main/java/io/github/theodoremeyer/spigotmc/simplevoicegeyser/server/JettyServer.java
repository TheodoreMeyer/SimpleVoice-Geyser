package io.github.theodoremeyer.spigotmc.simplevoicegeyser.server;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;

import java.time.Duration;

/**
 * Starts and Stops the Jetty server for SVG.
 * May be moved to a new thread.
 */
public class JettyServer {
    private final Server server;

    /**
     * set server port
     * @param port port to run server on
     */
    public JettyServer(int port) {
        this.server = new Server(port);
    }

    /**
     * starts Jetty server
     * @throws Exception start server error
     */
    public void start() throws Exception {
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        server.setHandler(context);

        // Add HTML page at root
        context.addServlet(new ServletHolder(new JettyHtmlServlet()), "/");

        // Register WebSocket at /ws
        JettyWebSocketServletContainerInitializer.configure(context, (servletContext, wsContainer) -> {
            wsContainer.addMapping("/ws", (req, resp) -> new JettyWebSocket());
            wsContainer.setIdleTimeout(Duration.ofMinutes(4));
        });

        server.start();
    }

    /**
     * Stops Jetty Server
     * @throws Exception stop error
     */
    public void stop() throws Exception {
        server.stop();
    }
}
