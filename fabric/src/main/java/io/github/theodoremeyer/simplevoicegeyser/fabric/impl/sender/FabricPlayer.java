package io.github.theodoremeyer.simplevoicegeyser.fabric.impl.sender;

import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class FabricPlayer extends SvgPlayer {

    private final ServerPlayer player;

    public FabricPlayer(ServerPlayer player) {
        this.player = player;
    }

    @Override
    public UUID getUniqueId() {
        return player.getUUID();
    }

    @Override
    public boolean hasPermission(String permission) {
        // Fabric has no built-in permissions system
        return true; // TODO: integrate LuckPerms Fabric later
    }

    @Override
    public void chat(String message) {
        player.level().getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("<" + getName() + "> " + message),
                false
        );
    }

    @Override
    public Object getPlayer() {
        return player;
    }

    @Override
    public void sendMessage(String message) {
        player.sendSystemMessage(Component.literal(message));
    }

    @Override
    public String getName() {
        return player.getName().getString();
    }


}