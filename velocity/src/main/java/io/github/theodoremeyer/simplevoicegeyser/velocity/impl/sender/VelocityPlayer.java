package io.github.theodoremeyer.simplevoicegeyser.velocity.impl.sender;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;

import java.util.UUID;

/**
 * Thin wrapper around a Velocity {@link Player}, exposing the small subset of
 * actions the plugin needs (identity, permissions, chat spoofing, messaging).
 */
public class VelocityPlayer {

    private final Player player;

    /**
     * Wrap the given online player.
     *
     * @param player Velocity player to wrap
     */
    public VelocityPlayer(Player player) {
        this.player = player;
    }

    /**
     * Get the player's unique id.
     * @return Mojang/Velocity UUID of the player
     */
    public UUID getUniqueId() {
        return player.getUniqueId();
    }

    /**
     * Get the player's username.
     * @return current username
     */
    public String getName() {
        return player.getUsername();
    }

    /**
     * Check a permission node on behalf of the player.
     *
     * @param permission permission node to test
     * @return whether the player holds the permission
     */
    public boolean hasPermission(String permission) {
        return player.hasPermission(permission);
    }

    /**
     * Send chat as if typed by the player.
     *
     * @param message message to spoof
     */
    public void chat(String message) {
        player.spoofChatInput(message);
    }

    /**
     * Get the underlying platform object.
     * @return the wrapped Velocity {@link Player} instance
     */
    public Object getPlayer() {
        return player;
    }

    /**
     * Send a plain-text message to the player.
     *
     * @param message text to send
     */
    public void sendMessage(String message) {
        player.sendMessage(Component.text(message));
    }
}
