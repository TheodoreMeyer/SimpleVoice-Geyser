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

/**
 * Driving class for Simple Voice Geyser
 */
public final class SvgCore {

    /**
     * The interface to server platform
     */
    public final Platform platform;
    private static SvgCore instance;

    /**
     * Version of the plugin.
     */
    public static final String VERSION = BuildInfo.PROJECT_VERSION;

    /**
     * Build Git Commit ID. Generated during Gradle Build. This is always the latest commit hash of the branch.
     * <p>
     * Please note that 'Gradle clean' may have to get run if caches don't fix themselves,
     */
    @SuppressWarnings("ConstantConditions")
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
    private State state = State.NEW;

    /**
     * Create the Core engine running the project
     * @param platform interface to server
     */
    public SvgCore(Platform platform) {
        this.platform = platform;
        instance = this;
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

    /**
     * Initialize the project
     * @return success
     */
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

    /**
     * Disable the project
     */
    public static void disable() {
        if (getInstance() != null) {
            getInstance().shutdown();
        }
    }

    /**
     * Shutdown the project
     */
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

    /**
     * Get the logger for the project
     * @return Logger
     */
    public static SvgLogger getLogger() {
        return getInstance().platform.getSvgLogger();
    }

    /**
     * Get prefix of the system
     * @return prefix
     */
    public static String getPrefix() {
        return getInstance().platform.getPrefix();
    }

    /**
     * Get interface to minecraft server platform
     * @return platform
     */
    public static Platform getPlatform() {
        return getInstance().platform;
    }

    /**
     * Get Config system of the project
     * @return SvgConfig instance
     */
    public static SvgConfig getConfig() {
        return getInstance().config;
    }

    /**
     * Get the Password Manager
     * @return password manager
     */
    public static PlayerVcPswd getPasswordManager() {
        return getInstance().playerVcPswd;
    }

    /**
     * Get class managing player connections
     * @return player manager
     */
    public static PlayerManager getPlayerManager() {
        return getInstance().playerManager;
    }

    /**
     * Get class handling all websocket connection lifetime.
     * @return connection Manager
     */
    public static ConnectionManager getConnectionManager() {
        return getInstance().connectionManager;
    }

    /**
     * Get class managing groups
     * @return group manager
     */
    public static GroupManager getGroupManager() {
        return getInstance().groupManager;
    }

    /**
     * Get the interface to SVC
     * @return voice chat bridge
     */
    public static VoiceChatBridge getBridge() {
        return getInstance().vcBridge;
    }

    /**
     * Get the command handler
     * @return command handler
     */
    public static Command getCommand() {
        return getInstance().command;
    }

    /**
     * get the compiler for audio packets
     * @return audio Byte Compiler
     */
    public static AudioByteCompiler getAudioByteCompiler() {
        return getInstance().audioByteCompiler;
    }

    private enum State {
        NEW, RUNNING, FAILED, SHUTDOWN
    }
}
