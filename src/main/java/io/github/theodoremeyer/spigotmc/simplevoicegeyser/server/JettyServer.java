package io.github.theodoremeyer.spigotmc.simplevoicegeyser.server;

import io.github.theodoremeyer.spigotmc.simplevoicegeyser.SVGPlugin;
import io.github.theodoremeyer.spigotmc.simplevoicegeyser.server.servlets.AudioWorkletServlet;
import io.github.theodoremeyer.spigotmc.simplevoicegeyser.server.servlets.ClientWorkletServlet;
import io.github.theodoremeyer.spigotmc.simplevoicegeyser.server.servlets.JettyHtmlServlet;
import io.github.theodoremeyer.spigotmc.simplevoicegeyser.server.servlets.MicWorkletServlet;
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
public class JettyServer {
    private final Server server;

    /**
     * set server port
     * @param port port to run server on
     */
    public JettyServer(int port, String host) {
        this.server = new Server();

        ServerConnector connector = new ServerConnector(server);
        connector.setHost(host);
        connector.setPort(port);

        server.addConnector(connector);
        SVGPlugin.log().info("Started on: " + connector.getDefaultProtocol() + " " + connector.getHost() + ":" + connector.getPort());
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

        // Add driving javascript
        context.addServlet(ClientWorkletServlet.class, "/client.js");

        //add audio/mic servlet
        context.addServlet(AudioWorkletServlet.class, "/audio-worklet-processor.js");
        context.addServlet(MicWorkletServlet.class, "/mic-capture-processor.js");

        double idleTimeoutMinutes =
                SVGPlugin.getInstance().getConfig().getDouble("client.idletimeout", 2.0);

        idleTimeoutMinutes = Math.max(0.5, Math.min(idleTimeoutMinutes, 10.0));

        SVGPlugin.log().info("Idle timeout: " + idleTimeoutMinutes + " minutes.");

        Duration idleTimeout = Duration.ofSeconds(
                Math.round(idleTimeoutMinutes * 60)
        );

        // Register WebSocket at /ws
        JettyWebSocketServletContainerInitializer.configure(context, (servletContext, wsContainer) -> {
            wsContainer.addMapping("/ws", (req, resp) -> new JettyWebSocket());
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
