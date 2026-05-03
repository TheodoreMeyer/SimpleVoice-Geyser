package io.github.theodoremeyer.simplevoicegeyser.spigotmc;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.Platform;
import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgLogger;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.DataType;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.SvgFile;
import io.github.theodoremeyer.simplevoicegeyser.core.svc.VoiceChatBridge;
import io.github.theodoremeyer.simplevoicegeyser.spigotmc.impl.BukkitLogger;
import io.github.theodoremeyer.simplevoicegeyser.spigotmc.impl.SvgCommand;
import io.github.theodoremeyer.simplevoicegeyser.spigotmc.impl.SvgListener;
import io.github.theodoremeyer.simplevoicegeyser.spigotmc.impl.data.ConfigFile;
import io.github.theodoremeyer.simplevoicegeyser.spigotmc.impl.data.PasswordFile;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class SvgPlugin extends JavaPlugin implements Platform {

    private SvgCore core;

    private ConfigFile configFile;

    private PasswordFile passwordFile;

    private BukkitLogger logger;

    //JAVA PLUGIN
    @Override
    public void onLoad() {
        logger = new BukkitLogger(getLogger());

        // Ensure plugin folder exists
        if (!getDataFolder().exists()) {
            boolean success = getDataFolder().mkdirs();
            if (!success) {
                logger.severe("Failed to create plugin data folder at " + getDataFolder().getAbsolutePath());
            }
        }

        // Define config file location
        File file = new File(getDataFolder(), "config.yml");

        // If the config file doesn't exist yet, copy defaults from resources
        if (!file.exists()) {
            saveResource("config.yml", false);
        }

        // Initialize ConfigFile wrapper
        this.configFile = new ConfigFile(file);
        this.passwordFile = new PasswordFile(new File(getDataFolder(), "playerpasswords.yml"));

        this.core = new SvgCore(this);
    }

    @Override
    public void onEnable() {
        core.init();

        PluginCommand command = getCommand("svg");
        if (command == null) {
            logger.severe("Failed to register command: 'svg' not found in plugin.yml");
            SvgCore.disable();
            return;
        }
        command.setExecutor(new SvgCommand());

        SvgListener listener = new SvgListener();
        Bukkit.getPluginManager().registerEvents(listener, this);
    }

    @Override
    public void onDisable() {
        SvgCore.getWsManager().disconnectAllClients();
    }

    @Override
    public void disable() {
        Bukkit.getPluginManager().disablePlugin(this);
    }

    @Override
    public String getPrefix() {
        return ChatColor.GRAY + "[" + ChatColor.AQUA + "SVG" + ChatColor.GRAY + "] " + ChatColor.RESET;
    }

    @Override
    public String getServerMcVersion() {
        return Bukkit.getBukkitVersion().split("-")[0]; //e.g. "1.20.4"
    }

    @Override
    public String getServerPlatform() {
        return "spigot";
    }

    @Override
    public VoiceChatBridge registerVcBridge() {
        BukkitVoicechatService service = Bukkit.getServicesManager().load(BukkitVoicechatService.class);
        VoiceChatBridge bridge = null;

        if (service != null) { //make sure BukkitVoicechatService exists
            VoiceChatBridge voicechatBridge = new VoiceChatBridge();
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
    public SvgLogger getSvgLogger() {
        return logger;
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
