package io.github.theodoremeyer.simplevoicegeyser.spigotmc.impl.sender;

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
        runOnMainThread(() -> Bukkit.broadcastMessage(translate(message)));
    }

    @Override
    public Object getPlayer() {
        return player;
    }

    @Override
    public void sendMessage(String message) {
        runOnMainThread(() -> player.sendMessage(translate(message)));
    }

    private void runOnMainThread(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return;
        }

        Bukkit.getScheduler().runTask(
                SvgPlugin.getPlugin(SvgPlugin.class),
                task
        );
    }

    private String translate(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
