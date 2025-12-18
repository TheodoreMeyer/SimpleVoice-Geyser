package io.github.theodoremeyer.spigotmc.simplevoicegeyser;

import io.github.theodoremeyer.spigotmc.simplevoicegeyser.server.WebSocketManager;
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
        if (player.hasPlayedBefore()) {
            player.sendMessage(ChatColor.DARK_GREEN + "This Server Uses SimpleVoice-Geyser");
            player.sendMessage(ChatColor.GREEN + "To set it up, run /svg pswd [password]");
            player.sendMessage(ChatColor.DARK_GREEN + "Then join Via the server's SVG website");
        }
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
