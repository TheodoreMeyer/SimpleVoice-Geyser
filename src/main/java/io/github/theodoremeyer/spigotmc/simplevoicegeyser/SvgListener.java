package io.github.theodoremeyer.spigotmc.simplevoicegeyser;

import io.github.theodoremeyer.spigotmc.simplevoicegeyser.geyser.FormHandler;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.geyser.api.event.bedrock.ClientEmoteEvent;

import java.util.UUID;

/**
 * The Event Listener for the plugin
 * is in Beta
 */
public class SvgListener implements Listener {
    /**
     * Java Plugin
     */
    private final JavaPlugin plugin;
    /**
     * Group Manager
     */
    private final GroupManager groupManager;
    /**
     * Constructor
     * @param groupManager the group Manager
     */
    public SvgListener(JavaPlugin plugin, GroupManager groupManager) {
        this.plugin = plugin;
        this.groupManager = groupManager;
    }
    /**
     * When a player joins
     * @param e the event
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        if (!player.hasPlayedBefore()) {
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
        SVGPlugin.getWsManager().disconnectClient(player.getUniqueId()); //make sure the client is disconnected because they left minecraft
    }

    /**
     * Used to check for emote event
     * @param event the EmoteEvent
     */
    public void onEmote(ClientEmoteEvent event) {
        UUID uuid = event.connection().playerUuid();
        String playerName = event.connection().name();
        FormHandler formHandler = new FormHandler(this.groupManager);

        plugin.getLogger().info("UUID for Emote: " + uuid);
        if (uuid == null) {
            plugin.getLogger().warning("Could not resolve UUID for: " + playerName);
            return;
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            plugin.getLogger().warning("Player not online for:" + playerName + " UUID: " + uuid);
            return;
        }
        formHandler.openCommand(player);
    }
}
