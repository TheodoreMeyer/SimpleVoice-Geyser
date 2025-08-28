package io.github.theodoremeyer.spigotmc.simplevoicegeysertoo;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * The Event Listener for the plugin
 * is in Beta
 */
public class SvgListener implements Listener {

    /**
     * When a player joins
     * @param e the event
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        player.sendMessage(ChatColor.DARK_GREEN + "This Server Uses SimpleVoice-Geyser");
    }

    /**
     * When a player leaves
     * @param e the event
     */
    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent e) {
        Player player = e.getPlayer();
        WebSocketManager.disconnectClient(player.getUniqueId()); //make sure the client is disconnected because they left minecraft
    }
}
