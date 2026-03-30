package io.github.theodoremeyer.simplevoicegeyser.core.server;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.SvgConfig;
import io.github.theodoremeyer.simplevoicegeyser.core.server.servlets.JettyWebSocket;
import io.github.theodoremeyer.simplevoicegeyser.core.server.servlets.ResourceServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;

import java.time.Duration;

/**
 * Starts and Stops the Jetty server for SVG.
 * May be moved to a new thread.
 */
public final class JettyServer {

    /**
     * The Server
     */
    private final Server server;
    /**
     * The Plugin
     */
    private final SvgCore core;

    /**
     * Idle Timeout
     */
    private final Duration idleTimeout;

    /**
     * set server port
     * @param core svg core
     * @param port port to run server on
     * @param host the host address to bind the server to.
     */
    public JettyServer(SvgCore core, int port, String host) {
        this.server = new Server();
        this.core = core;

        double idleTimeoutMinutes = SvgConfig.IDLE_TIMEOUT.get();
                //core.getConfig().getDouble("client.idletimeout", 2.0);

        idleTimeoutMinutes = Math.clamp(idleTimeoutMinutes, 0.5, 10.0);

        SvgCore.getLogger().info("Idle timeout: " + idleTimeoutMinutes + " minutes.");

        this.idleTimeout = Duration.ofSeconds(
                Math.round(idleTimeoutMinutes * 60)
        );



        ServerConnector connector = new ServerConnector(server);
        connector.setHost(host);
        connector.setPort(port);
        connector.setIdleTimeout(idleTimeout.toMillis());

        server.addConnector(connector);
        SvgCore.getLogger().info("Started on: " + connector.getDefaultProtocol() + " " + connector.getHost() + ":" + connector.getPort());
    }

    /**
     * starts Jetty server
     * @throws Exception start server error
     */
    public void start() throws Exception {
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        server.setHandler(context);

        // Serve all static resources from /web
        context.addServlet(new ServletHolder(new ResourceServlet()), "/*");

        // Register WebSocket at /ws
        JettyWebSocketServletContainerInitializer.configure(context, (servletContext, wsContainer) -> {
            wsContainer.addMapping("/ws", (req, resp) -> new JettyWebSocket(core));
            wsContainer.setIdleTimeout(idleTimeout);
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
