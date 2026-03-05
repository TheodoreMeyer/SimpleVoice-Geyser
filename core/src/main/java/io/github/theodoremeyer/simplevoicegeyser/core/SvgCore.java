package io.github.theodoremeyer.simplevoicegeyser.core;

import io.github.theodoremeyer.simplevoicegeyser.core.api.Platform;
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
     * Whether debug is enabled
     */
    private boolean debug = false;

    public SvgCore(Platform platform) {
        this.platform = platform;
        instance = this;
        new AudioThread();
    }


    //-----
    // LOGGERS
    //-----
    public static Logger getLogger() {
        return instance.platform.getLogger();
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

}
