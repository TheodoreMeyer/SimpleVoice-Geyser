package io.github.theodoremeyer.simplevoicegeyser.spigotmc.impl;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.spigotmc.impl.sender.BukkitPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class SvgListener implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        BukkitPlayer bukkitPlayer = new BukkitPlayer(player);
        SvgCore.getJoinMessageHandler().sendJoinMessage(bukkitPlayer);

        SvgCore.getPlayerManager().addPlayer(bukkitPlayer);
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();
        SvgPlayer player = SvgCore.getPlayerManager().getPlayer(playerUuid);

        if (player != null) {
            SvgCore.getPlayerManager().removePlayer(player);
        }
    }
}
