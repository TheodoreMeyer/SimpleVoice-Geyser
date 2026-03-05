package io.github.theodoremeyer.simplevoicegeyser.core;

import io.github.theodoremeyer.simplevoicegeyser.core.api.Platform;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.DataType;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.SvgFile;
import io.github.theodoremeyer.simplevoicegeyser.core.managers.GroupManager;
import io.github.theodoremeyer.simplevoicegeyser.core.managers.PlayerManager;
import io.github.theodoremeyer.simplevoicegeyser.core.server.WebSocketManager;
import io.github.theodoremeyer.simplevoicegeyser.core.svc.VoiceChatBridge;
import io.github.theodoremeyer.simplevoicegeyser.core.thread.AudioThread;
import org.geysermc.geyser.api.event.EventRegistrar;

import java.util.logging.Logger;

public class SvgCore implements EventRegistrar {
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

    //MANAGERS
    private final PlayerManager playerManager;

    private GroupManager groupManager;

    private PlayerVcPswd playerVcPswd;

    private final WebSocketManager webSocketManager;

    /**
     * Whether debug is enabled
     */
    private boolean debug = false;

    public SvgCore(Platform platform) {
        this.platform = platform;
        instance = this;
        new AudioThread();

        //Managers
        this.playerManager = new PlayerManager();
        this.webSocketManager = new WebSocketManager();
    }

    public void init() {
        this.playerVcPswd = new PlayerVcPswd(platform.getFile(DataType.PASSWORD));

        this.vcBridge = platform.registerVcBridge();

        this.groupManager = new GroupManager(vcBridge);
    }

    public SvgFile getConfig() {
        return platform.getFile(DataType.CONFIG);
    }

    //-----
    // LOGGERS
    //-----
    public static Logger getLogger() {
        return instance.platform.getLogger();
    }

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
     */
    public static Platform getPlatform() {
        return instance.platform;
    }

    /**
     * Get Password System
     */
    public static PlayerVcPswd getPasswordManager() {
        return instance.playerVcPswd;
    }

    public static PlayerManager getPlayerManager() {
        return instance.playerManager;
    }

    public static GroupManager getGroupManager() {
        return instance.groupManager;
    }

    public static VoiceChatBridge getBridge() {
        return instance.vcBridge;
    }

    public static WebSocketManager getWsManager() {
        return instance.webSocketManager;
    }

}
