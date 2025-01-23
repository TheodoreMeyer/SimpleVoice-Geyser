package io.github.theodoremeyer.SimpleVoiceGeyser;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class VoiceChatGUI {

    private final SimpleVoiceChatGeyser plugin;
    private final VoiceChatHandler voiceChatHandler;

    public VoiceChatGUI(SimpleVoiceChatGeyser plugin, VoiceChatHandler voiceChatHandler) {
        this.plugin = plugin;
        this.voiceChatHandler = voiceChatHandler;
    }

    public void openChannelMenu(Player player) {
        Inventory menu = Bukkit.createInventory(null, 27, "Voice Chat Channels");

        // Leftmost column - Proximity Channel
        addChannelColumn(menu, player, 0, "Proximity Channel", "proximity");

        // Middle column - Channel 1
        addChannelColumn(menu, player, 1, "Channel 1", "channel1");

        // Rightmost column - Channel 2
        addChannelColumn(menu, player, 2, "Channel 2", "channel2");

        player.openInventory(menu);
    }

    private void addChannelColumn(Inventory menu, Player player, int columnIndex, String columnName, String channelName) {
        // Add the channel's name, players, and mute/unmute buttons
        List<Player> playersInChannel = voiceChatHandler.getPlayersInChannel(channelName);
        
        int baseSlot = columnIndex * 9;

        // Channel name & Password
        ItemStack channelInfo = new ItemStack(Material.PAPER);
        ItemMeta meta = channelInfo.getItemMeta();
        meta.setDisplayName("Channel: " + channelName);
        meta.setLore(Collections.singletonList("Password: " + (voiceChatHandler.isPlayerMuted(player.getUniqueId(), channelName) ? "Muted" : "Unmuted")));
        channelInfo.setItemMeta(meta);
        menu.setItem(baseSlot, channelInfo);

        // Show players in the channel with mute/unmute options
        for (int i = 0; i < playersInChannel.size() && i < 3; i++) {
            Player channelPlayer = playersInChannel.get(i);
            ItemStack muteButton = new ItemStack(Material.BARRIER);
            ItemMeta muteMeta = muteButton.getItemMeta();
            muteMeta.setDisplayName("Mute Player: " + channelPlayer.getName());
            muteButton.setItemMeta(muteMeta);
            menu.setItem(baseSlot + 1 + i, muteButton);
        }

        // Join Channel / Create Channel Button
        if (playersInChannel.isEmpty()) {
            ItemStack joinButton = new ItemStack(Material.GREEN_WOOL);
            ItemMeta joinMeta = joinButton.getItemMeta();
            joinMeta.setDisplayName("Join " + columnName);
            joinButton.setItemMeta(joinMeta);
            menu.setItem(baseSlot + 4, joinButton);
        }
    }

    public void handleChannelMenuClick(InventoryClickEvent event, Player player) {
        if (event.getCurrentItem() == null) return;

        String clickedItem = event.getCurrentItem().getItemMeta().getDisplayName();

        if (clickedItem.startsWith("Mute Player:")) {
            // Mute Player logic
            String playerName = clickedItem.replace("Mute Player: ", "");
            Player targetPlayer = Bukkit.getPlayer(playerName);
            if (targetPlayer != null) {
                voiceChatHandler.mutePlayer(targetPlayer.getUniqueId(), "proximity");
                player.sendMessage("Muted " + playerName);
            }
        } else if (clickedItem.startsWith("Join ")) {
            // Join Channel logic
            String channelName = clickedItem.replace("Join ", "");
            voiceChatHandler.joinChannel(channelName,
