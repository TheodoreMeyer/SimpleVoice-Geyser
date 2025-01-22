package io.github.theodoremeyer.SimpleVoiceGeyser;

import io.github.theodoremeyer.SimpleVoiceGeyser.VoiceChatHandler;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final VoiceChatHandler voiceChatHandler;

    public PlayerJoinListener(VoiceChatHandler voiceChatHandler) {
        this.voiceChatHandler = voiceChatHandler;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        voiceChatHandler.handlePlayerJoin(event.getPlayer());
    }
}
