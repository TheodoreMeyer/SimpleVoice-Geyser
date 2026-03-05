package io.github.theodoremeyer.simplevoicegeyser.core.managers;

import com.saicone.ezlib.Ezlib;

/**
 * Minimal runtime dependency loader using Vankka's DependencyDownload.
 */
public final class SvgLibraryLoader {

    public SvgLibraryLoader() {
    }

    public void loadDependencies() {
        // Initialize Ezlib
        Ezlib ezlib = new Ezlib();
        ezlib.init();

        // Load JSON
        ezlib.dependency("org.json:json:20250107")
                .parent(true)
                .load();

        // Load Jetty Server
        ezlib.dependency("org.eclipse.jetty:jetty-server:11.0.25")
                .parent(true)
                .load();

        // Load Jetty Servlet
        ezlib.dependency("org.eclipse.jetty:jetty-servlet:11.0.25")
                .parent(true)
                .load();

        // Load WebSocket Servlet
        ezlib.dependency("org.eclipse.jetty.websocket:websocket-servlet:11.0.25")
                .parent(true)
                .load();

        // Load WebSocket Server
        ezlib.dependency("org.eclipse.jetty.websocket:websocket-jetty-server:11.0.25")
                .parent(true)
                .load();

        // Load BCrypt
        ezlib.dependency("org.mindrot:jbcrypt:0.4")
                .parent(true)
                .load();
    }
}