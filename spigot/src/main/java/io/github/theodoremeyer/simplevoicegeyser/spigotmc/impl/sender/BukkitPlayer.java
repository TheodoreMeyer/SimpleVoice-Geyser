package io.github.theodoremeyer.simplevoicegeyser.spigotmc.impl.sender;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.spigotmc.SvgPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.UUID;

public class BukkitPlayer extends SvgPlayer {

    private final Player player;

    public BukkitPlayer(Player player) {
        this.player = player;
    }

    //Svg Player Impl
    @Override
    public UUID getUniqueId() {
        return player.getUniqueId();
    }

    @Override
    public String getName() {
        return player.getName();
    }

    @Override
    public boolean hasPermission(String permission) {
        return player.hasPermission(permission);
    }

    @Override
    public void chat(String message) {
        if (Bukkit.isPrimaryThread()) {
            player.chat(message);
        } else {
            SvgPlugin plugin = (SvgPlugin) SvgCore.getPlatform();

            Bukkit.getScheduler().runTask(plugin, () -> player.chat(message));
        }
    }

    @Override
    public Object getPlayer() {
        return player;
    }

    @Override
    public void sendMessage(String message) {
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }
}
