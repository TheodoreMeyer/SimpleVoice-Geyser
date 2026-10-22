package io.github.theodoremeyer.simplevoicegeyser.spigotmc.impl;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.server.packets.ChatBroadcaster;
import io.github.theodoremeyer.simplevoicegeyser.spigotmc.impl.sender.BukkitPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class SvgListener implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Spigot/Paper PlayerManager always stores BukkitPlayer instances.
        BukkitPlayer bukkitPlayer = new BukkitPlayer(event.getPlayer());
        bukkitPlayer.startSpatialCache();
        SvgCore.getJoinMessageHandler().sendJoinMessage(bukkitPlayer);
        SvgCore.getPlayerManager().addPlayer(bukkitPlayer);
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();
        SvgPlayer player = SvgCore.getPlayerManager().getPlayer(playerUuid);
        if (player instanceof BukkitPlayer bukkitPlayer) {
            bukkitPlayer.cancelSpatialCache();
        }
        if (player != null) {
            SvgCore.getPlayerManager().removePlayer(player);
        }
    }

    /**
     * Mirror public Minecraft chat into authenticated web clients.
     * Runs MONITOR so cancelled chat (mute plugins, etc.) is not forwarded.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        String message = event.getMessage();
        if (message == null || message.isBlank()) {
            return;
        }
        ChatBroadcaster.onMinecraftChat(player.getName(), player.getUniqueId(), message);
    }
}
