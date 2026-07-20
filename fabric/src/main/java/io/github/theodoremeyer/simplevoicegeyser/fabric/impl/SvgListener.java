package io.github.theodoremeyer.simplevoicegeyser.fabric.impl;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.fabric.impl.sender.FabricPlayer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
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

            FabricPlayer fabricPlayer = new FabricPlayer(player);
            SvgCore.getJoinMessageHandler().sendJoinMessage(fabricPlayer);

            SvgCore.getPlayerManager().addPlayer(fabricPlayer);
        });

        // LEAVE
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {

            UUID uuid = handler.player.getUUID();

            FabricPlayer player = (FabricPlayer) SvgCore.getPlayerManager().getPlayer(uuid);

            if (player != null) {
                player.isOnline = false;
                SvgCore.getPlayerManager().removePlayer(player);
            }
        });
    }
}