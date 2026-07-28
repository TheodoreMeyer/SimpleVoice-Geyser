package io.github.theodoremeyer.simplevoicegeyser.core.managers;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;

public class JoinMessageHandler {

    public JoinMessageHandler() {}

    public void sendJoinMessage(SvgPlayer player) {
        if (SvgCore.getConfig().JOIN_MESSAGE_ENABLED.get()) {
            for (String line : SvgCore.getConfig().JOIN_MESSAGE_TEXT.get()) {
                player.sendMessage(line);
            }
        }
    }
}
