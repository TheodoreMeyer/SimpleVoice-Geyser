package io.github.theodoremeyer.simplevoicegeyser.fabric.impl;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.fabric.impl.sender.FabricPlayer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class SvgListener {

    public SvgListener() {
        register();
    }

    public void register() {

        // JOIN
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {

            ServerPlayer player = handler.player;

            player.sendSystemMessage(
                    Component.literal("This Server Uses SimpleVoice-Geyser")
            );

            player.sendSystemMessage(
                    Component.literal("To set it up, run /svg pswd [password]")
            );

            player.sendSystemMessage(
                    Component.literal("Then join via the server SVG website")
            );

            SvgCore.getPlayerManager().addPlayer(
                    new FabricPlayer(player)
            );
        });

        // LEAVE
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {

            UUID uuid = handler.player.getUUID();

            SvgPlayer player = SvgCore.getPlayerManager().getPlayer(uuid);

            if (player != null) {
                SvgCore.getPlayerManager().removePlayer(player);
            }
        });
    }
}