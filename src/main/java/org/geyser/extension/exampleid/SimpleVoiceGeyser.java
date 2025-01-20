package io.github.theodoremeyer.Geyser.simplevoicegeyser;

import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.api.event.lifecycle.GeyserLoadResourcePacksEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.api.extension.Extension;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

public class SimpleVoiceGeyser implements Extension {
    
    private static final int JETTY_PORT = 25566; // needs to get changed to get config\
    private JettyServer jettyServer;
    
    public final Set<WebSocketEndpoint> endpoints = new HashSet<>();
    public final Map<UUID, WebSocketEndpoint> endpointsByPlayer = new HashMap<>();

    // You can use the GeyserPostInitializeEvent to run anything after Geyser fully initialized and is ready to accept bedrock player connections.
    @Subscribe
    public void onPostInitialize(GeyserPostInitializeEvent event) {
        
        //show that the extension is loading.
        this.logger().info("Loading SimpleVoiceChat for Geyser extension...");

        Path ConfigPath = this.dataFolder().resolve("config.yml");
            this.saveDefaultConfig(ConfigPath);

         jettyServer = new JettyServer(JETTY_PORT, this);
        try {
            jettyServer.start();
            log.info("Jetty started on port " + JETTY_PORT);
        } catch (Exception e) {
            log.severe("Failed to start Jetty: " + e.getMessage());
            e.printStackTrace();
        }
        
    private void saveDefaultConfig(Path ConfigPath) throws IOException {
        if (Files.exists(ConfigPath)) {
            return;
        }
        if (Files.notExists(this.dataFolder())) {
            Files.createDirectory(this.dataFolder());
        }

        try {
            URI uri = this.getClass().getResource("/commands.yml").toURI();
            try (FileSystem fileSystem = FileSystems.newFileSystem(uri, new HashMap<>(), null)) {
                Path path = fileSystem.getPath("Config.yml");
                Files.copy(path, ConfigPath);
            }
        } catch (IOException | URISyntaxException ex) {
            this.logger().error("Failed to create Config.yml!", ex);
            this.setEnabled(false);
        }
    }
}
