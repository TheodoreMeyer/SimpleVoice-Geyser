package io.github.theodoremeyer.simplevoicegeyser.core.managers;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;

/**
 * Allows Customization of the SVG Join Message in config
 */
public class JoinMessageHandler {

    /**
     * Create the Handler
     */
    public JoinMessageHandler() {}

    /**
     * Send a join message to the player if enabled
     * @param player {@link SvgPlayer} to send the message to
     */
    public void sendJoinMessage(SvgPlayer player) {
        if (SvgCore.getConfig().JOIN_MESSAGE_ENABLED.get()) {
            for (String line : SvgCore.getConfig().JOIN_MESSAGE_TEXT.get()) {
                player.sendMessage(line);
            }
        }
    }
}
