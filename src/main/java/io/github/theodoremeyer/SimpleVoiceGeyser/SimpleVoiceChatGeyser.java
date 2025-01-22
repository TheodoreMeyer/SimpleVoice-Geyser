package io.github.theodoremeyer.SimpleVoiceGeyser;

import io.github.theodoremeyer.SimpleVoiceGeyser.VoiceChatGUI;
import io.github.theodoremeyer.SimpleVoiceGeyser.WebServer;
import io.github.theodoremeyer.SimpleVoiceGeyser.voiceChatHandler;
import org.bukkit.entity.Player;
import org.geysermc.geyser.extension.Extension;

import java.io.File;
import java.util.HashMap;
import java.util.UUID;

public class SimpleVoiceChatGeyser extends Extension {

    private WebServer webServer;
    private VoiceChatHandler voiceChatHandler;
    private VoiceChatGUI voiceChatGUI;

    private final HashMap<UUID, PlayerSettings> playerSettings = new HashMap<>();

    @Override
    public void onEnable() {
        getLogger().info("SimpleVoiceChat Geyser Extension is enabling...");

        // Load configuration
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveResource("config.yml", false);
        }
        getConfig().load(configFile);

        // Initialize voice chat handler
        String voiceChatHost = getConfig().getString("voicechat.server_host", "localhost");
        int voiceChatPort = getConfig().getInt("voicechat.server_port", 25566);
        double defaultProximityRadius = getConfig().getDouble("proximity.default_radius", 50.0);
        voiceChatHandler = new VoiceChatHandler(voiceChatHost, voiceChatPort, defaultProximityRadius);

        // Initialize GUI
        voiceChatGUI = new VoiceChatGUI(this, voiceChatHandler);
        
        // Register the PlayerJoinListener
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(voiceChatHandler), this);


        // Start the web server
        int webServerPort = getConfig().getInt("webserver.port", 8080);
        webServer = new WebServer(webServerPort, voiceChatHandler, playerSettings);
        webServer.start();

        // Register GUI command
        getGeyserCommandManager().register("set", (source, args) -> {
            if (source.isConsole()) {
                source.sendMessage("§cThis command is only available in-game.");
                return;
            }
            Player player = getServer().getPlayer(source.uuid());
            if (player != null) {
                voiceChatGUI.openMainMenu(player);
            }
        });

        getLogger().info("SimpleVoiceChat Geyser Extension is now enabled!");
    }

    @Override
    public void onDisable() {
        if (webServer != null) {
            webServer.stop();
        }
        getLogger().info("SimpleVoiceChat Geyser Extension is now disabled!");
    }

    public HashMap<UUID, PlayerSettings> getPlayerSettings() {
        return playerSettings;
    }
}
