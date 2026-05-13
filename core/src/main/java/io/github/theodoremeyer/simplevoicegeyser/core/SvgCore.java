package io.github.theodoremeyer.simplevoicegeyser.core;

import io.github.theodoremeyer.simplevoicegeyser.core.api.Platform;
import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgLogger;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.DataType;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.SvgConfig;
import io.github.theodoremeyer.simplevoicegeyser.core.audio.AudioByteCompiler;
import io.github.theodoremeyer.simplevoicegeyser.core.audio.AudioThread;
import io.github.theodoremeyer.simplevoicegeyser.core.commands.Command;
import io.github.theodoremeyer.simplevoicegeyser.core.data.PlayerVcPswd;
import io.github.theodoremeyer.simplevoicegeyser.core.geyser.GeyserEventHook;
import io.github.theodoremeyer.simplevoicegeyser.core.geyser.GeyserHook;
import io.github.theodoremeyer.simplevoicegeyser.core.managers.GroupManager;
import io.github.theodoremeyer.simplevoicegeyser.core.managers.PlayerManager;
import io.github.theodoremeyer.simplevoicegeyser.core.server.JettyServer;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.ConnectionManager;
import io.github.theodoremeyer.simplevoicegeyser.core.svc.VoiceChatBridge;
import io.github.theodoremeyer.simplevoicegeyser.core.update.UpdateChecker;

import java.util.logging.Logger;

/**
 * Driving class for Simple Voice Geyser
 */
public final class SvgCore {
    public final Platform platform;
    private static SvgCore instance;
    public static final String VERSION = "0.1.1-Dev";

    /**
     * Build Git Commit ID
     */
    public static final String BUILD_ID = BuildInfo.BUILD_ID;

    /**
     * Config system
     */
    private final SvgConfig config;
    private VoiceChatBridge vcBridge;
    private final ConnectionManager connectionManager;
    private JettyServer jettyServer;
    private final PlayerManager playerManager;
    private GroupManager groupManager;
    private PlayerVcPswd playerVcPswd;
    private Command command;
    private final AudioByteCompiler audioByteCompiler;
    private final String buildToken;
    private State state = State.NEW;

    public SvgCore(Platform platform) {
        this.platform = platform;
        instance = this;
        this.buildToken = createBuildToken();
        this.config = new SvgConfig(platform.getFile(DataType.CONFIG));

        Boolean checkUpdate = config.UPDATE_CHECKER_ENABLED.get();
        if (Boolean.TRUE.equals(checkUpdate)) {
            new UpdateChecker(VERSION, BUILD_ID, platform).check();
        }

        new AudioThread();
        this.playerManager = new PlayerManager();
        this.connectionManager = new ConnectionManager();
        this.audioByteCompiler = new AudioByteCompiler();
    }

    private static SvgCore getInstance() {
        return instance;
    }

    public synchronized boolean init() {
        if (state == State.RUNNING) {
            return true;
        }
        if (state == State.SHUTDOWN || state == State.FAILED) {
            return false;
        }

        try {
            if (getConfig().DEBUG.get()) {
                getLogger().info("Debug mode enabled.");
                getLogger().setDebug(true);
            }

            getLogger().info("Web UI build token: " + getBuildToken());
            getLogger().info("client.vctimeout is currently documented but inactive in this dev build.");

            this.playerVcPswd = new PlayerVcPswd(this);

            int port = getConfig().PORT.get();
            String host = getConfig().BIND_ADDRESS.get();

            if (Boolean.TRUE.equals(getConfig().AUDIO_ALLOW_LEGACY_FALLBACK.get())) {
                getLogger().warning("Audio legacy fallback is enabled. This is recommended during svg-v2 transition only.");
            }

            this.jettyServer = new JettyServer(host, port);
            this.jettyServer.start();
            getLogger().info("Jetty server started on port: " + port);

            this.vcBridge = platform.registerVcBridge();
            if (this.vcBridge == null) {
                getLogger().severe("Failed to register VoiceChatBridge.");
                shutdown();
                state = State.FAILED;
                return false;
            }

            this.groupManager = new GroupManager(vcBridge);
            this.command = new Command(groupManager, this);

            if (GeyserHook.isGeyser()) {
                new GeyserEventHook();
            } else {
                getLogger().warning("Geyser is not installed. Skipping Bedrock events.");
            }

            state = State.RUNNING;
            return true;
        } catch (Exception e) {
            getLogger().severe("Init failed: " + e.getMessage());
            shutdown();
            state = State.FAILED;
            return false;
        }
    }

    public static void disable() {
        if (getInstance() != null) {
            getInstance().shutdown();
        }
    }

    private synchronized void shutdown() {
        if (state == State.SHUTDOWN) {
            return;
        }

        state = State.SHUTDOWN;
        connectionManager.disconnectAll();

        try {
            if (jettyServer != null) {
                jettyServer.stop();
                getLogger().info("Jetty server stopped.");
            }
        } catch (Exception e) {
            getLogger().severe("Failed to stop Jetty server: " + e.getMessage());
            getLogger().debug("Jetty stop failed", e);
        }

        AudioThread.shutdown();

        if (playerVcPswd != null) {
            playerVcPswd.shutdown();
        }

        vcBridge = null;
        jettyServer = null;
        groupManager = null;
        command = null;
    }

    public static SvgLogger getLogger() {
        return getInstance().platform.getSvgLogger();
    }

    public static String getPrefix() {
        return getInstance().platform.getPrefix();
    }

    public static Platform getPlatform() {
        return getInstance().platform;
    }

    public static SvgConfig getConfig() {
        return getInstance().config;
    }

    public static PlayerVcPswd getPasswordManager() {
        return getInstance().playerVcPswd;
    }

    public static PlayerManager getPlayerManager() {
        return getInstance().playerManager;
    }

    public static ConnectionManager getConnectionManager() {
        return getInstance().connectionManager;
    }

    public static GroupManager getGroupManager() {
        return getInstance().groupManager;
    }

    public static VoiceChatBridge getBridge() {
        return getInstance().vcBridge;
    }

    public static Command getCommand() {
        return getInstance().command;
    }

    public static AudioByteCompiler getAudioByteCompiler() {
        return getInstance().audioByteCompiler;
    }

    public static String getBuildToken() {
        return getInstance().buildToken;
    }

    private String createBuildToken() {
        String version = null;
        Package pkg = getClass().getPackage();
        if (pkg != null) {
            version = pkg.getImplementationVersion();
        }
        if (version == null || version.isBlank()) {
            version = "dev";
        }
        version = version.replaceAll("[^A-Za-z0-9._-]", "_");
        return version + "-" + Long.toString(System.currentTimeMillis(), 36);
    }

    private enum State {
        NEW, RUNNING, FAILED, SHUTDOWN
    }
}
