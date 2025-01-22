package io.github.theodoremeyer.SimpleVoiceGeyser;

import io.github.theodoremeyer.SimpleVoiceGeyser.SimpleVoiceChatGeyser;
import io.github.theodoremeyer.SimpleVoiceGeyser.VoiceChatHandler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

public class VoiceChatGUI {

    private final SimpleVoiceChatGeyserExtension plugin;
    private final VoiceChatHandler voiceChatHandler;

    public VoiceChatGUI(SimpleVoiceChatGeyserExtension plugin, VoiceChatHandler voiceChatHandler) {
        this.plugin = plugin;
        this.voiceChatHandler = voiceChatHandler;
    }

    public void openMainMenu(Player player) {
        Inventory menu = Bukkit.createInventory(null, 27, "Voice Chat Settings");

        // Proximity chat toggle
        ItemStack proximityToggle = createMenuItem(Material.COMPASS, "Proximity Chat", "Toggle proximity chat.");
        menu.setItem(10, proximityToggle);

        // Join channel
        ItemStack joinChannel = createMenuItem(Material.BOOK, "Join Channel", "Join a voice chat channel.");
        menu.setItem(12, joinChannel);

        // Leave channel
        ItemStack leaveChannel = createMenuItem(Material.BARRIER, "Leave Channel", "Leave a voice chat channel.");
        menu.setItem(14, leaveChannel);

        // Exit
        ItemStack exit = createMenuItem(Material.REDSTONE, "Exit", "Close the menu.");
        menu.setItem(16, exit);

        player.openInventory(menu);
    }

    private ItemStack createMenuItem(Material material, String name, String description) {
        ItemStack item = new ItemStack(material);
        item.getItemMeta().setDisplayName(name);
        item.getItemMeta().setLore(Collections.singletonList(description));
        return item;
    }

    public void handleMenuClick(InventoryClickEvent event) {
        // Handle clicks on GUI items and trigger actions
    }
}
