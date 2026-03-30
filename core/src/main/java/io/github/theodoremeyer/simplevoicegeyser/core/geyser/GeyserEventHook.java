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

    private final FormHandler formHandler;

    /**
     * Start Event Listener
     */
    public GeyserEventHook() {
        this.formHandler = new FormHandler(SvgCore.getGroupManager());
        if (SvgCore.getConfig().USE_EMOTE.get()) {
            SvgCore.getLogger().info("Using Emote for SVG");
        } else {
            SvgCore.getLogger().info("Not using Emote for SVG");
            return;
        }

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

        SvgCore.debug("GEYSER", "UUID for Emote: " + uuid);

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