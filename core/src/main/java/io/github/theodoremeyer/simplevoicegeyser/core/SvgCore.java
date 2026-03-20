package io.github.theodoremeyer.simplevoicegeyser.core;

import io.github.theodoremeyer.simplevoicegeyser.core.api.Platform;
import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgLogger;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.DataType;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.SvgFile;
import io.github.theodoremeyer.simplevoicegeyser.core.geyser.GeyserEventHook;
import io.github.theodoremeyer.simplevoicegeyser.core.geyser.GeyserHook;
import io.github.theodoremeyer.simplevoicegeyser.core.managers.GroupManager;
import io.github.theodoremeyer.simplevoicegeyser.core.managers.PlayerManager;
import io.github.theodoremeyer.simplevoicegeyser.core.server.JettyServer;
import io.github.theodoremeyer.simplevoicegeyser.core.server.WebSocketManager;
import io.github.theodoremeyer.simplevoicegeyser.core.svc.VoiceChatBridge;
import io.github.theodoremeyer.simplevoicegeyser.core.thread.AudioThread;

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
     * The Link to the SVC system
     */
    private VoiceChatBridge vcBridge;

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

    /**
     * Whether debug is enabled
     */
    private boolean debug = false;

    /**
     * Initialize the Core of SimpleVoice-Geyser
     * @see Platform the hook to the platform
     * @param platform the platform to build with
     */
    public SvgCore(Platform platform) {

        this.platform = platform;
        instance = this;
        new AudioThread();

        //Managers
        this.playerManager = new PlayerManager();
        this.webSocketManager = new WebSocketManager();
    }

    /**
     * Start SVG server and handling with SVC
     */
    public void init() {
        this.debug = platform.getFile(DataType.CONFIG).getBoolean("debug", false);

        this.playerVcPswd = new PlayerVcPswd(platform.getFile(DataType.PASSWORD));

        this.vcBridge = platform.registerVcBridge();

        this.groupManager = new GroupManager(vcBridge);

        this.command = new Command(groupManager);

        int port = platform.getFile(DataType.CONFIG).getInt("server.port", 8080);
        String host = platform.getFile(DataType.CONFIG).getString("server.bind-address", "0.0.0.0");

        try {
            jettyServer = new JettyServer(this, port, host); //start the jetty server
            jettyServer.start();
            getLogger().info("Jetty server started on port: " + port);
        } catch (Exception e) {
            getLogger().severe("Failed to start Jetty server: " + e.getMessage());
            platform.disable();
        }

        if (GeyserHook.isGeyser()) {
            new GeyserEventHook();
        }  else {
            getLogger().warning("Geyser is not installed. Skipping Bedrock Events");
        }
    }

    /**
     * Get the config as a File
     * @return config
     */
    public SvgFile getConfig() {
        return platform.getFile(DataType.CONFIG);
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
        return instance.platform.getSvgLogger();
    }

    /**
     * Get the Log/Chat Prefix
     * @return prefix
     */
    public static String getPrefix() {
        return instance.platform.getPrefix();
    }

    /**
     * debug option with no throwable
     * @param section the part of plugin debugging
     * @param message the message
     */
    public static void debug(String section, String message) {
        if (instance != null && instance.debug) {
            getLogger().info("[Debug][" + section + "] " + message);
        }
    }

    /**
     * debug option with a throwable
     * @param section the part of plugin debugging
     * @param message the message
     * @param t the throwable/error thrown
     */
    public static void debug(String section, String message, Throwable t) {
        if (instance != null && instance.debug) {
            getLogger().info("[Debug][" + section + "] " + message + ", " + t);
        }
    }

    //-----
    //FETCHERS
    //-----
    /**
     * Get The Platform
     * @return the platform
     */
    public static Platform getPlatform() {
        return instance.platform;
    }

    /**
     * Get Password System
     * @see PlayerVcPswd
     * @return PasswordManager
     */
    public static PlayerVcPswd getPasswordManager() {
        return instance.playerVcPswd;
    }

    /**
     * Get the Player Manager
     * @see PlayerManager
     * @return PlayerManager
     */
    public static PlayerManager getPlayerManager() {
        return instance.playerManager;
    }

    /**
     * Get the Group Manager
     * @see GroupManager
     * @return groupManager
     */
    public static GroupManager getGroupManager() {
        return instance.groupManager;
    }

    /**
     * Get The Bridge with SVC
     * @see VoiceChatBridge
     * @return voiceChatBridge
     */
    public static VoiceChatBridge getBridge() {
        return instance.vcBridge;
    }

    /**
     * Get the Ws Manager
     * @see WebSocketManager
     * @return WebsocketManager
     */
    public static WebSocketManager getWsManager() {
        return instance.webSocketManager;
    }

    /**
     * Get the Svg Command
     * @see Command
     * @return SvgCommand
     */
    public static Command getCommand() { return instance.command; }
}
