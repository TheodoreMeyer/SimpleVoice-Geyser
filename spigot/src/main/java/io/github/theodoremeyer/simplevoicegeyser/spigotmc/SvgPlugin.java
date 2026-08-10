package io.github.theodoremeyer.simplevoicegeyser.spigotmc;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.Platform;
import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgLogger;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.DataType;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.SvgFile;
import io.github.theodoremeyer.simplevoicegeyser.core.schedule.TaskScheduler;
import io.github.theodoremeyer.simplevoicegeyser.core.svc.VoiceChatBridge;
import io.github.theodoremeyer.simplevoicegeyser.spigotmc.impl.BukkitLogger;
import io.github.theodoremeyer.simplevoicegeyser.spigotmc.impl.SvgCommand;
import io.github.theodoremeyer.simplevoicegeyser.spigotmc.impl.SvgListener;
import io.github.theodoremeyer.simplevoicegeyser.spigotmc.impl.data.ConfigFile;
import io.github.theodoremeyer.simplevoicegeyser.spigotmc.schedule.PlatformSchedulers;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class SvgPlugin extends JavaPlugin implements Platform {

    private SvgCore core;

    private ConfigFile configFile;

    private BukkitLogger logger;

    private TaskScheduler taskScheduler;

    //JAVA PLUGIN
    @Override
    public void onLoad() {
        logger = new BukkitLogger(getLogger());
        this.taskScheduler = PlatformSchedulers.create(this);

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

        if (taskScheduler.isRegionThreaded()) {
            logger.info("Detected regionized threading (Folia/Canvas). Using region/entity schedulers.");
        } else if (PlatformSchedulers.hasRegionSchedulers(getServer())) {
            logger.info("Using Paper region scheduler API (mapped to the server tick thread).");
        } else {
            logger.info("Using classic Bukkit scheduler.");
        }

        this.core = new SvgCore(this);
    }

    @Override
    public void onEnable() {

        // core is already constructed in onLoad()

        if (!core.init()) {

            logger.severe("Core initialization failed. Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        PluginCommand command = getCommand("svg");

        if (command == null) {

            logger.severe("Failed to register command: 'svg' not found in plugin.yml");

            SvgCore.disable();
            return;
        }

        command.setExecutor(new SvgCommand());

        Bukkit.getPluginManager().registerEvents(new SvgListener(), this);
        seedOnlinePlayers();
    }

    /**
     * Players already online when the plugin enables never fire PlayerJoinEvent.
     * Snapshot them on each player's entity scheduler so Folia ownership is respected.
     */
    private void seedOnlinePlayers() {
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            java.util.UUID uuid = player.getUniqueId();
            taskScheduler.executeForEntity(
                    "svg/player/seed-online",
                    uuid,
                    () -> {
                        org.bukkit.entity.Player live = Bukkit.getPlayer(uuid);
                        if (live == null || !live.isOnline()) {
                            return;
                        }
                        if (SvgCore.getPlayerManager().getPlayer(uuid) != null) {
                            return;
                        }
                        SvgCore.getPlayerManager().addPlayer(
                                new io.github.theodoremeyer.simplevoicegeyser.spigotmc.impl.sender.BukkitPlayer(live)
                        );
                        logger.debug("Seeded online player into PlayerManager: " + live.getName());
                    },
                    () -> logger.debug("Skipped seeding retired player " + uuid)
            );
        }
    }

    @Override
    public void onDisable() {
        SvgCore.disable();
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

        BukkitVoicechatService service =
                Bukkit.getServicesManager().load(BukkitVoicechatService.class);

        if (service == null) {
            logger.severe("No Voice Chat service found. Disabling plugin.");
            SvgCore.disable();
            return null;
        }

        VoiceChatBridge bridge = new VoiceChatBridge();

        service.registerPlugin(bridge);

        logger.info("Registered plugin with Simple Voice Chat.");

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
        }
        return null;
    }

    @Override
    public boolean isDependencyEnabled(String name) {
        return Bukkit.getPluginManager().isPluginEnabled(name);
    }

    @Override
    public TaskScheduler getTaskScheduler() {
        return taskScheduler;
    }
}
