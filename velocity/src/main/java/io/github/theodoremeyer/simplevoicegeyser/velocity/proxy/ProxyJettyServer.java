package io.github.theodoremeyer.simplevoicegeyser.velocity.proxy;

import io.github.theodoremeyer.simplevoicegeyser.velocity.VelocityPlugin;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;

import java.time.Duration;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.KeyFactory;

public final class ProxyJettyServer {

    private final Server server;
    private final Duration idleTimeout;

    public ProxyJettyServer(String host, int port, Duration idleTimeout, File certificate, File key) throws Exception {
        this.server = new Server();
        this.idleTimeout = idleTimeout;

        ServerConnector connector;
        if (certificate != null && key != null) {
            SslContextFactory.Server ssl = new SslContextFactory.Server();
            char[] password = UUID.randomUUID().toString().toCharArray();
            KeyStore keyStore = createKeyStore(certificate, key, password);
            ssl.setKeyStore(keyStore);
            ssl.setKeyStorePassword(new String(password));
            HttpConfiguration https = new HttpConfiguration();
            https.addCustomizer(new org.eclipse.jetty.server.SecureRequestCustomizer());
            connector = new ServerConnector(server, new SslConnectionFactory(ssl, "http/1.1"),
                    new HttpConnectionFactory(https));
        } else {
            connector = new ServerConnector(server);
        }
        connector.setHost(host);
        connector.setPort(port);
        connector.setIdleTimeout(idleTimeout.toMillis());

        server.addConnector(connector);
    }

    private static KeyStore createKeyStore(File certificate, File key, char[] password) throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        X509Certificate cert;
        try (var input = java.nio.file.Files.newInputStream(certificate.toPath())) {
            cert = (X509Certificate) factory.generateCertificate(input);
        }
        String pem = java.nio.file.Files.readString(key.toPath(), StandardCharsets.US_ASCII);
        Matcher matcher = Pattern.compile("-----BEGIN PRIVATE KEY-----(.*?)-----END PRIVATE KEY-----", Pattern.DOTALL).matcher(pem);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Private key must be an unencrypted PKCS#8 PEM key");
        }
        byte[] encoded = Base64.getMimeDecoder().decode(matcher.group(1));
        PrivateKey privateKey = KeyFactory.getInstance(cert.getPublicKey().getAlgorithm())
                .generatePrivate(new PKCS8EncodedKeySpec(encoded));
        KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
        store.load(null, password);
        store.setKeyEntry("proxy", privateKey, password, new X509Certificate[]{cert});
        return store;
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
