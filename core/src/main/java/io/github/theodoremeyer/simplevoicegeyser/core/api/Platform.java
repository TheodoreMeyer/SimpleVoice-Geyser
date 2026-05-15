package io.github.theodoremeyer.simplevoicegeyser.core.api;

import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgLogger;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.DataType;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.SvgFile;
import io.github.theodoremeyer.simplevoicegeyser.core.svc.VoiceChatBridge;

import java.io.File;

/**
 * Represents the bridge between a platform like spigot and SVG core
 */
public interface Platform {

    /**
     * Disable in case of error
     */
    void disable();

    /**
     * The Prefix for Logging
     * @return the prefix
     */
    String getPrefix();

    /**
     * The server's mc version
     * @return mc version (x.x.x)
     */
    String getServerMcVersion();

    /**
     * The Server platform
     * @return platform: bukkit, fabric, etc
     */
    String getServerPlatform();

    /**
     * Register VoiceChatBridge
     * @return the registered bridge
     */
    VoiceChatBridge registerVcBridge();

    /**
     * Logger
     * @return logger
     */
    SvgLogger getSvgLogger();

    /**
     * Get a file saved to disk
     * @param type which file to get
     * @return the file
     */
    SvgFile getFile(DataType type);

    /**
     * Get the DataFolder to save internal data in
     * @return datafolder
     */
    File getDataFolder();

    /**
     * Figure out if a mod/plugin is enabled
     * @param name the name of the dependency
     * @return if its enabled
     */
    boolean isDependencyEnabled(String name);


}
