package io.github.theodoremeyer.simplevoicegeyser.core.managers;

import com.saicone.ezlib.EzlibLoader;

/**
 * Runtime dependency loader for Jetty 11 + WebSocket + Servlet API + utilities.
 * Resolves and caches all required dependencies so no large shaded jar is needed.
 */
public final class SvgLibraryLoader {

    /**
     * Load the core's dependencies using Ez-Lib
     */
    public void loadDependencies() {

        EzlibLoader loader = new EzlibLoader();
        loader.init();
        loader.initDefaultOptions();

        loader.loadRepository(
                new EzlibLoader.Repository()
                        .name("MavenCentral")
                        .url("https://repo1.maven.org/maven2/")
        );

        // ------------------------------
        // JSON
        // ------------------------------
        loader.applyDependency(
                EzlibLoader.Dependency.valueOf("org.json:json:20250107").transitive(true)
        );

        // ------------------------------
        // BCrypt
        // ------------------------------
        loader.applyDependency(
                EzlibLoader.Dependency.valueOf("org.mindrot:jbcrypt:0.4").transitive(true)
        );

        // ------------------------------
        // Servlet API
        // ------------------------------
        loader.applyDependency(
                EzlibLoader.Dependency.valueOf("jakarta.servlet:jakarta.servlet-api:5.0.0").transitive(true)
        );

        // ------------------------------
        // Jetty Server (pulls core transitives)
        // ------------------------------
        loader.applyDependency(
                EzlibLoader.Dependency.valueOf("org.eclipse.jetty:jetty-server:11.0.25").transitive(true)
        );

        loader.applyDependency(
                EzlibLoader.Dependency.valueOf("org.eclipse.jetty:jetty-servlet:11.0.25").transitive(true)
        );

        loader.applyDependency(EzlibLoader.Dependency.valueOf("org.eclipse.jetty:jetty-util:11.0.25").transitive(true));
        loader.applyDependency(EzlibLoader.Dependency.valueOf("org.eclipse.jetty:jetty-io:11.0.25").transitive(true));
        loader.applyDependency(EzlibLoader.Dependency.valueOf("org.eclipse.jetty:jetty-http:11.0.25").transitive(true));
        loader.applyDependency(EzlibLoader.Dependency.valueOf("org.eclipse.jetty:jetty-security:11.0.25").transitive(true));

        // ------------------------------
        // Jetty WebSocket
        // ------------------------------
        loader.applyDependency(
                EzlibLoader.Dependency.valueOf("org.eclipse.jetty.websocket:websocket-jetty-server:11.0.25").transitive(true)
        );

        loader.applyDependency(
                EzlibLoader.Dependency.valueOf("org.eclipse.jetty.websocket:websocket-jetty-api:11.0.25")
                        .transitive(true)
        );

        loader.load();
    }
}