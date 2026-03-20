package io.github.theodoremeyer.simplevoicegeyser.core.geyser;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.bedrock.ClientEmoteEvent;

import java.util.UUID;

/**
 * EventHook System
 */
public final class GeyserEventHook implements EventRegistrar {

    /**
     * Start Event Listener
     */
    public GeyserEventHook() {
        GeyserApi.api().eventBus().subscribe(
                this,
                ClientEmoteEvent.class,
                this::onEmote
        );
    }

    /**
     * Used to check for emote event
     * @param event the emoteEvent
     */
    public void onEmote(ClientEmoteEvent event) {

        UUID uuid = event.connection().playerUuid();
        String playerName = event.connection().name();

        FormHandler formHandler = new FormHandler(SvgCore.getGroupManager());

        SvgCore.getLogger().info("UUID for Emote: " + uuid);

        if (uuid == null) {
            SvgCore.getLogger().warning("Could not resolve UUID for: " + playerName);
            return;
        }

        SvgPlayer player = SvgCore.getPlayerManager().getPlayer(uuid);

        if (player == null) {
            SvgCore.getLogger().warning("Player not online for: " + playerName + " UUID: " + uuid);
            return;
        }

        formHandler.openCommand(player);
    }
}