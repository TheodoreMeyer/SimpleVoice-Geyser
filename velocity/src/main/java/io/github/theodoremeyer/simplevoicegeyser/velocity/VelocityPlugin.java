package io.github.theodoremeyer.simplevoicegeyser.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.Platform;
import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgLogger;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.DataType;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.SvgFile;
import io.github.theodoremeyer.simplevoicegeyser.core.svc.VoiceChatBridge;
import io.github.theodoremeyer.simplevoicegeyser.velocity.impl.data.VelocityConfigFile;
import io.github.theodoremeyer.simplevoicegeyser.velocity.impl.sender.VelocityPlayer;
import org.slf4j.Logger;

import java.io.File;
import java.nio.file.Path;

@Plugin(
        id = "simplevoice-geyser",
        name = "SimpleVoice-Geyser",
        version = "0.1.2",
        description = "Plugin to allow Bedrock clients to join Simple Voice Chat.",
        authors = {"TheodoreMeyer"}
)
public class VelocityPlugin implements Platform {

    private final ProxyServer server;
    private final Logger slf4jLogger;
    private final Path dataDirectory;

    private SvgCore core;
    private VelocityLogger svgLogger;
    private VelocityConfigFile configFile;

    @Inject
    public VelocityPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.slf4jLogger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        svgLogger = new VelocityLogger(slf4jLogger);

        File dataDir = dataDirectory.toFile();
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        File configFileObj = new File(dataDir, "config.json");
        this.configFile = new VelocityConfigFile(configFileObj);
        SvgFile.MigrationReport migration = this.configFile.migrateFromBundledDefaults("startup");
        svgLogger.info("[Config] migration trigger=startup mode=" + migration.mode()
                + " addedKeys=" + migration.addedKeys()
                + " backup=" + (migration.backupPath().isBlank() ? "none" : migration.backupPath()));

        this.core = new SvgCore(this);

        if (!core.init()) {
            svgLogger.severe("Core initialization failed. Disabling plugin.");
            return;
        }

        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("svg").build(),
                new VelocityCommand()
        );

        server.getEventManager().register(this, new EventListener());
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        SvgCore.disable();
    }

    @Override
    public void disable() {
        server.shutdown();
    }

    @Override
    public String getPrefix() {
        return "<gray>[<aqua>SVG<gray>] <reset>";
    }

    @Override
    public String getServerMcVersion() {
        String version = server.getVersion().getVersion();
        return version.contains("/") ? version.split("/")[0] : version;
    }

    @Override
    public String getServerPlatform() {
        return "velocity";
    }

    @Override
    public VoiceChatBridge registerVcBridge() {
        VoiceChatBridge bridge = new VoiceChatBridge();
        svgLogger.info("Registered plugin with Simple Voice Chat (ServiceLoader).");
        return bridge;
    }

    @Override
    public SvgLogger getSvgLogger() {
        return svgLogger;
    }

    @Override
    public SvgFile getFile(DataType type) {
        if (type == DataType.CONFIG) {
            return configFile;
        }
        return null;
    }

    @Override
    public File getDataFolder() {
        return dataDirectory.toFile();
    }

    @Override
    public boolean isDependencyEnabled(String name) {
        return server.getPluginManager().isLoaded(name);
    }

    private class EventListener {

        @Subscribe
        public void onLogin(LoginEvent event) {
            SvgCore.getPlayerManager().addPlayer(
                    new VelocityPlayer(event.getPlayer())
            );
        }

        @Subscribe
        public void onDisconnect(DisconnectEvent event) {
            java.util.UUID uuid = event.getPlayer().getUniqueId();
            io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer player =
                    SvgCore.getPlayerManager().getPlayer(uuid);
            if (player != null) {
                SvgCore.getPlayerManager().removePlayer(player);
            }
        }
    }
}
