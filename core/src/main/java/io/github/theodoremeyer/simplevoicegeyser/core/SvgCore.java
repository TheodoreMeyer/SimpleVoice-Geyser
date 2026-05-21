package io.github.theodoremeyer.simplevoicegeyser.core;

import io.github.theodoremeyer.simplevoicegeyser.core.api.Platform;
import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgLogger;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.DataType;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.SvgConfig;
import io.github.theodoremeyer.simplevoicegeyser.core.audio.AudioThread;
import io.github.theodoremeyer.simplevoicegeyser.core.commands.Command;
import io.github.theodoremeyer.simplevoicegeyser.core.data.PlayerVcPswd;
import io.github.theodoremeyer.simplevoicegeyser.core.geyser.GeyserEventHook;
import io.github.theodoremeyer.simplevoicegeyser.core.geyser.GeyserHook;
import io.github.theodoremeyer.simplevoicegeyser.core.managers.GroupManager;
import io.github.theodoremeyer.simplevoicegeyser.core.managers.PlayerManager;
import io.github.theodoremeyer.simplevoicegeyser.core.server.JettyServer;
import io.github.theodoremeyer.simplevoicegeyser.core.server.WebSocketManager;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.ConnectionManager;
import io.github.theodoremeyer.simplevoicegeyser.core.svc.VoiceChatBridge;
import io.github.theodoremeyer.simplevoicegeyser.core.update.UpdateChecker;

import java.util.logging.Logger;

/**
 * Driving class for Simple Voice Geyser
 */
public final class SvgCore {
    /**
     * The Platform
     */
    public final Platform platform;

    /**
     * Instance
     */
    private static SvgCore instance;

    /**
     * Project Version
     */
    public static final String VERSION = "0.1.1-Dev";

    /**
     * Config system
     */
    private final SvgConfig config;

    /**
     * The Link to the SVC system
     */
    private VoiceChatBridge vcBridge;

    /**
     * Connection System
     */
    private final ConnectionManager connectionManager;

    /**
     * Server
     */
    private JettyServer jettyServer;

    //MANAGERS
    private final PlayerManager playerManager;

    private GroupManager groupManager;

    private PlayerVcPswd playerVcPswd;

    private final WebSocketManager webSocketManager;

    private Command command;

    private State state = State.NEW;

    /**
     * Initialize the Core of SimpleVoice-Geyser
     * @see Platform the hook to the platform
     * @param platform the platform to build with
     */
    public SvgCore(Platform platform) {

        this.platform = platform;
        instance = this;

        this.config = new SvgConfig(platform.getFile(DataType.CONFIG));

        Boolean checkUpdate = config.UPDATE_CHECKER_ENABLED.get();
        if (Boolean.TRUE.equals(checkUpdate)) {
            new UpdateChecker(VERSION, platform).check();
        }

        new AudioThread();

        //Managers
        this.playerManager = new PlayerManager();
        this.webSocketManager = new WebSocketManager();
        this.connectionManager = new ConnectionManager();
    }
    
    private static SvgCore getInstance() {
        return instance;
    }

    /**
     * Start SVG server and handling with SVC
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

            this.playerVcPswd = new PlayerVcPswd(this);

            int port = getConfig().PORT.get();
            String host = getConfig().BIND_ADDRESS.get();

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
     * Disable SVG
     */
    public static void disable() {
        if (getInstance() != null) {
            getInstance().shutdown();
        }
    }

    /**
     * Stops itself
     */
    private synchronized void shutdown() {

        if (state == State.SHUTDOWN) {
            return;
        }

        state = State.SHUTDOWN;

        webSocketManager.disconnectAllClients();

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

    //-----
    // LOGGERS
    //-----

    /**
     * Get the Logger
     * @see Logger
     * @return logger
     */
    public static SvgLogger getLogger() {
        return getInstance().platform.getSvgLogger();
    }

    /**
     * Get the Log/Chat Prefix
     * @return prefix
     */
    public static String getPrefix() {
        return getInstance().platform.getPrefix();
    }

    //-----
    //FETCHERS
    //-----
    /**
     * Get The Platform
     * @return the platform
     */
    public static Platform getPlatform() {
        return getInstance().platform;
    }

    /**
     * Get Config
     * @return config
     */
    public static SvgConfig getConfig() {
        return getInstance().config;
    }

    /**
     * Get Password System
     * @see PlayerVcPswd
     * @return PasswordManager
     */
    public static PlayerVcPswd getPasswordManager() {
        return getInstance().playerVcPswd;
    }

    /**
     * Get the Player Manager
     * @see PlayerManager
     * @return PlayerManager
     */
    public static PlayerManager getPlayerManager() {
        return getInstance().playerManager;
    }

    /**
     * Gets the Connection Manager
     * @see ConnectionManager
     * @return connectionManager
     */
    public static ConnectionManager getConnectionManager() {
        return getInstance().connectionManager;
    }

    /**
     * Get the Group Manager
     * @see GroupManager
     * @return groupManager
     */
    public static GroupManager getGroupManager() {
        return getInstance().groupManager;
    }

    /**
     * Get The Bridge with SVC
     * @see VoiceChatBridge
     * @return voiceChatBridge
     */
    public static VoiceChatBridge getBridge() {
        return getInstance().vcBridge;
    }

    /**
     * Get the Ws Manager
     * @see WebSocketManager
     * @return WebsocketManager
     */
    public static WebSocketManager getWsManager() {
        return getInstance().webSocketManager;
    }

    /**
     * Get the Svg Command
     * @see Command
     * @return SvgCommand
     */
    public static Command getCommand() { return getInstance().command; }

    private enum State {
        NEW, RUNNING, FAILED, SHUTDOWN
    }
}
