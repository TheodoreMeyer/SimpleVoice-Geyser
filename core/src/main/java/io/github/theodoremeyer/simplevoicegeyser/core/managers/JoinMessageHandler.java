package io.github.theodoremeyer.simplevoicegeyser.core.managers;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.proxy.ProxyControlClient;

import java.util.concurrent.CompletableFuture;

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
     * @return completion of the asynchronous proxy-state check and any resulting message dispatch
     */
    public CompletableFuture<Void> sendJoinMessage(SvgPlayer player) {
        return ProxyControlClient.updatePlayerState(player.getUniqueId(), player.getName(), true)
                .thenAccept(proxyState -> {
                    // Do not send a delayed join message after this player has left or reconnected.
                    if (SvgCore.getPlayerManager().getPlayer(player.getUniqueId()) != player) {
                        return;
                    }
                    if (proxyState.passwordSet()
                            || SvgCore.getPasswordManager().isPasswordSet(player.getName())) {
                        return;
                    }
                    if (SvgCore.getConfig().JOIN_MESSAGE_ENABLED.get()) {
                        for (String line : SvgCore.getConfig().JOIN_MESSAGE_TEXT.get()) {
                            player.sendMessage(line);
                        }
                    }
                });
    }
}
