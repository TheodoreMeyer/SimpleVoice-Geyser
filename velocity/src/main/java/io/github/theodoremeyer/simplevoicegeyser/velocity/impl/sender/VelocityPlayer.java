package io.github.theodoremeyer.simplevoicegeyser.velocity.impl.sender;

import com.velocitypowered.api.proxy.Player;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import net.kyori.adventure.text.Component;

import java.util.UUID;

public class VelocityPlayer extends SvgPlayer {

    private final Player player;

    public VelocityPlayer(Player player) {
        this.player = player;
    }

    @Override
    public UUID getUniqueId() {
        return player.getUniqueId();
    }

    @Override
    public String getName() {
        return player.getUsername();
    }

    @Override
    public boolean hasPermission(String permission) {
        return player.hasPermission(permission);
    }

    @Override
    public void chat(String message) {
        player.spoofChatInput(message);
    }

    @Override
    public Object getPlayer() {
        return player;
    }

    @Override
    public void sendMessage(String message) {
        player.sendMessage(Component.text(message));
    }
}
