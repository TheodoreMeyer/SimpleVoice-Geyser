package io.github.theodoremeyer.simplevoicegeyser.velocity.impl.sender;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;

import java.util.UUID;

public class VelocityPlayer {

    private final Player player;

    public VelocityPlayer(Player player) {
        this.player = player;
    }

    public UUID getUniqueId() {
        return player.getUniqueId();
    }

    public String getName() {
        return player.getUsername();
    }

    public boolean hasPermission(String permission) {
        return player.hasPermission(permission);
    }

    public void chat(String message) {
        player.spoofChatInput(message);
    }

    public Object getPlayer() {
        return player;
    }

    public void sendMessage(String message) {
        player.sendMessage(Component.text(message));
    }
}
