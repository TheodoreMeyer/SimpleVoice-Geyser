package io.github.theodoremeyer.simplevoicegeyser.velocity.proxy;

import io.github.theodoremeyer.simplevoicegeyser.core.server.servlets.ResourceServlet;
import io.github.theodoremeyer.simplevoicegeyser.velocity.VelocityPlugin;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;

import java.time.Duration;

public final class ProxyJettyServer {

    private final Server server;
    private final Duration idleTimeout;

    public ProxyJettyServer(String host, int port, Duration idleTimeout) {
        this.server = new Server();
        this.idleTimeout = idleTimeout;

        ServerConnector connector = new ServerConnector(server);
        connector.setHost(host);
        connector.setPort(port);
        connector.setIdleTimeout(idleTimeout.toMillis());

        server.addConnector(connector);
    }

    public void start(VelocityPlugin plugin) throws Exception {
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        server.setHandler(context);

        context.addServlet(new ServletHolder(new ResourceServlet()), "/*");

        JettyWebSocketServletContainerInitializer.configure(context, (servletContext, wsContainer) -> {
            wsContainer.addMapping("/ws", (req, resp) -> new ProxyWebSocket(plugin));
            wsContainer.setIdleTimeout(idleTimeout);
        });

        server.start();
    }

    public void stop() throws Exception {
        server.stop();
    }
}
