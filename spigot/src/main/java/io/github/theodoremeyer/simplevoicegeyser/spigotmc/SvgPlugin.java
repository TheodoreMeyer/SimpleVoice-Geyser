package io.github.theodoremeyer.simplevoicegeyser.spigotmc;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.Platform;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.DataType;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.SvgFile;
import io.github.theodoremeyer.simplevoicegeyser.core.svc.VoiceChatBridge;
import io.github.theodoremeyer.simplevoicegeyser.spigotmc.impl.SvgCommand;
import io.github.theodoremeyer.simplevoicegeyser.spigotmc.impl.SvgListener;
import io.github.theodoremeyer.simplevoicegeyser.spigotmc.impl.data.ConfigFile;
import io.github.theodoremeyer.simplevoicegeyser.spigotmc.impl.data.PasswordFile;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.bedrock.ClientEmoteEvent;

import java.io.File;

public class SvgPlugin extends JavaPlugin implements Platform, EventRegistrar {

    private SvgCore core;

    private ConfigFile configFile;

    private PasswordFile passwordFile;

    //JAVA PLUGIN
    @Override
    public void onLoad() {

        // Ensure plugin folder exists
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // Define config file location
        File file = new File(getDataFolder(), "config.yml");

        // If the config file doesn't exist yet, copy defaults from resources
        if (!file.exists()) {
            saveResource("config.yml", false);
        }

        // Initialize your ConfigFile wrapper
        this.configFile = new ConfigFile(file);
        this.passwordFile = new PasswordFile(new File(getDataFolder(), "playerpasswords.yml"));

        this.core = new SvgCore(this);

    }

    @Override
    public void onEnable() {
        core.init();

        getCommand("svg").setExecutor(new SvgCommand());


        SvgListener listener = new SvgListener();
        Bukkit.getPluginManager().registerEvents(listener, this);

        Plugin geyser = Bukkit.getPluginManager().getPlugin("Geyser-Spigot");
        if (geyser != null && geyser.isEnabled()) {
            GeyserApi.api().eventBus().subscribe(
                    this,
                    ClientEmoteEvent.class,
                    listener::onEmote
            );
        } else {
            getLogger().warning("Geyser is not installed. Skipping Bedrock Events");
        }
    }

    @Override
    public void onDisable() {

    }

    @Override
    public void disable() {

    }

    @Override
    public String getPrefix() {
        return ChatColor.GRAY + "[" + ChatColor.AQUA + "SVG" + ChatColor.GRAY + "] " + ChatColor.RESET;
    }

    @Override
    public VoiceChatBridge registerVcBridge() {
        BukkitVoicechatService service = Bukkit.getServicesManager().load(BukkitVoicechatService.class);
        VoiceChatBridge bridge = null;

        if (service != null) { //make sure BukkitVoicechatService exists
            VoiceChatBridge voicechatBridge = new VoiceChatBridge(core);
            service.registerPlugin(voicechatBridge); //register the main api class
            bridge = voicechatBridge;
            getLogger().info("Registered plugin with Simple Voice Chat.");
        } else {
            getLogger().severe("Disabling due to: no voice chat found.");
            Bukkit.getPluginManager().disablePlugin(this);
        }

        return bridge;
    }

    @Override
    public SvgFile getFile(DataType type) {
        if (type == DataType.CONFIG) {
            return configFile;
        } else if (type == DataType.PASSWORD) {
            return passwordFile;
        }
        return null;
    }

    @Override
    public boolean isDependencyEnabled(String name) {
        return Bukkit.getPluginManager().isPluginEnabled(name);
    }
}
