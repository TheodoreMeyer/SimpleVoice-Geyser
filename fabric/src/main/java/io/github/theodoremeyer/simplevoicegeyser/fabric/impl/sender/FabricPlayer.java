package io.github.theodoremeyer.simplevoicegeyser.fabric.impl.sender;

import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.fabric.SvgMod;
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
        try {
            return SvgMod.getLuckPerms().hasPermission(player, permission);
        } catch (Exception e) {
            System.err.println("[Permissions] Unable to determine permission for '" + permission + "', defaulting to true.");
            e.printStackTrace();
            return true;
        }
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