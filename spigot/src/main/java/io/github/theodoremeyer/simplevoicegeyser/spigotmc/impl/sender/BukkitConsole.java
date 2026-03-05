package io.github.theodoremeyer.simplevoicegeyser.spigotmc.impl.sender;

import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgConsole;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

public class BukkitConsole extends SvgConsole {

    @Override
    public void sendMessage(String message) {
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }
}
