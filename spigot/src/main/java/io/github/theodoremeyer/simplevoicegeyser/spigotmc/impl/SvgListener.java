package io.github.theodoremeyer.simplevoicegeyser.spigotmc.impl;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.spigotmc.impl.sender.BukkitPlayer;
import org.bukkit.ChatColor;
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
        if (!player.hasPlayedBefore()) {
            player.sendMessage(ChatColor.DARK_GREEN + "This Server Uses SimpleVoice-Geyser");
            player.sendMessage(ChatColor.GREEN + "To set it up, run /svg pswd [password]");
            player.sendMessage(ChatColor.DARK_GREEN + "Then join Via the server's SVG website");
        }
        SvgCore.getPlayerManager().addPlayer(new BukkitPlayer(player));
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
