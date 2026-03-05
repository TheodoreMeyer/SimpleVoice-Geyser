package io.github.theodoremeyer.simplevoicegeyser.core.server;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
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
     * set server port
     * @param port port to run server on
     */
    public JettyServer(SvgCore core, int port, String host) {
        this.server = new Server();
        this.core = core;

        ServerConnector connector = new ServerConnector(server);
        connector.setHost(host);
        connector.setPort(port);

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

        String htmlType = "text/html";
        String jSType = "application/javascript";

        // Add HTML page at root
        context.addServlet(new ServletHolder(new ResourceServlet(
                "/web/index.html", htmlType)), "/");

        // Add driving JavaScript
        context.addServlet(new ServletHolder(new ResourceServlet(
                "/web/js/client.js", jSType)), "/client.js");

        //add audio servlet
        context.addServlet(new ServletHolder(new ResourceServlet(
                "/web/js/audio-worklet-processor.js", jSType)), "/audio-worklet-processor.js");

        // add mic servlet
        context.addServlet(new ServletHolder(new ResourceServlet(
                "/web/js/mic-capture-processor.js", jSType)), "/mic-capture-processor.js");




        double idleTimeoutMinutes =
                core.getConfig().getDouble("client.idletimeout", 2.0);

        idleTimeoutMinutes = Math.max(0.5, Math.min(idleTimeoutMinutes, 10.0));

        SvgCore.getLogger().info("Idle timeout: " + idleTimeoutMinutes + " minutes.");

        Duration idleTimeout = Duration.ofSeconds(
                Math.round(idleTimeoutMinutes * 60)
        );

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
