package io.github.theodoremeyer.simplevoicegeyser.core.api;

import io.github.theodoremeyer.simplevoicegeyser.core.api.data.DataType;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.SvgFile;
import io.github.theodoremeyer.simplevoicegeyser.core.svc.VoiceChatBridge;

import java.util.logging.Logger;

public interface Platform {

    /**
     * Disable in case of error
     */
    void disable();

    /**
     * The Prefix for Logging
     */
    String getPrefix();

    /**
     * Register VoiceChatBridge
     */
    VoiceChatBridge registerVcBridge();

    /**
     * Logger
     * @return logger
     */
    Logger getLogger();

    /**
     * Get a file saved to disk
     * @param type which file to get
     * @return the file
     */
    SvgFile getFile(DataType type);

    /**
     * Figure out if a mod/plugin is enabled
     */
    boolean isDependencyEnabled(String name);


}
