package io.github.theodoremeyer.spigotmc.simplevoicegeyser;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import io.github.theodoremeyer.spigotmc.simplevoicegeyser.geyser.FormHandler;
import io.github.theodoremeyer.spigotmc.simplevoicegeyser.server.JettyServer;
import io.github.theodoremeyer.spigotmc.simplevoicegeyser.server.WebSocketManager;
import io.github.theodoremeyer.spigotmc.simplevoicegeyser.thread.AudioThread;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.geyser.api.event.EventRegistrar;

import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.event.Event;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.event.bedrock.ClientEmoteEvent;

import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Main Class of Plugin
 */
public final class SVGPlugin extends JavaPlugin implements EventRegistrar {
    /**
     * The jetty server
     */
    private JettyServer jettyServer;
    /**
     * Class: VoiceChatBridge
     * @see VoiceChatBridge
     */
    private static VoiceChatBridge bridge;
    /**
     * Class: Group system
     */
    private GroupManager groupManager;
    /**
     * AudioThread
     */
    private AudioThread thread;
    /**
     * This class
     */
    private static SVGPlugin instance;
    /**
     * The timeout config
     * @apiNote Disabled
     */
    private int vcTimeout;
    /**
     * The websocket manager for controlling websocket sessions
     * @see WebSocketManager
     */
    private WebSocketManager webSocketManager;
    /**
     * Whether debug is enabled
     */
    private boolean debug = false;

    /**
     * Universal Svg Prefix
     */
    public static final String PREFIX = ChatColor.GRAY + "[" + ChatColor.AQUA + "SVG" + ChatColor.GRAY + "] " + ChatColor.RESET;

    /**
     * When the plugin loads
     */
    @Override
    public void onEnable() {
        instance = this;
        this.thread = new AudioThread();

        loadConfigProperly();
        this.debug = getConfig().getBoolean("Debug", false);

        int rawTimeout = getConfig().getInt("client.vctimeout", 30); //get config from config.yml
        this.vcTimeout = Math.max(0, Math.min(120, rawTimeout));
        int jettyServerPort = getConfig().getInt("server.port", 8080);
        String jettyServerHost = getConfig().getString("server.bind-address", "0.0.0.0");

        BukkitVoicechatService service = Bukkit.getServicesManager().load(BukkitVoicechatService.class);

        if (service != null) { //make sure bukkitvoicechatservice exists
            VoiceChatBridge voicechatBridge = new VoiceChatBridge(this);
            service.registerPlugin(voicechatBridge); //register the main api class
            bridge = voicechatBridge;
            getLogger().info("Registered plugin with Simple Voice Chat.");
        } else {
            //added this because of some early problems with registering the plugin
            getLogger().severe("Failed to register with Simple Voice Chat: service not found.");
            Collection<Class<?>> knownServices = Bukkit.getServicesManager().getKnownServices();
            if (debug) {
                if (knownServices.isEmpty()) {
                    getLogger().warning("No services registered yet.");
                } else {
                    for (Class<?> s : knownServices) {
                        RegisteredServiceProvider<?> reg = Bukkit.getServicesManager().getRegistration(s);
                        if (reg != null) {
                            Object provider = reg.getProvider();
                            getLogger().info("  ➜ " + s.getName() + " -> " + provider.getClass().getName());
                        }
                    }
                }
            }
            getLogger().severe("Disabling due to: no voice chat found.");
            Bukkit.getPluginManager().disablePlugin(this);
        }

        this.groupManager = new GroupManager(bridge);

        SvgListener listener = new SvgListener(this, groupManager);
        Bukkit.getPluginManager().registerEvents(listener, this);
        PlayerVcPswd.init(this.getDataFolder());
        Objects.requireNonNull(getCommand("svg")).setExecutor(new SvgCommand(groupManager));

        // Check to see if geyser is installed
        Plugin geyser = Bukkit.getPluginManager().getPlugin("Geyser-Spigot");
        if (geyser != null && geyser.isEnabled()) {
            GeyserApi.api().eventBus().subscribe(
                    this,
                    ClientEmoteEvent.class,
                    listener::onEmote
            );
        } else {
            log().warning("Geyser is not installed. Skipping Bedrock Events");
        }

        this.webSocketManager = new WebSocketManager();

        try {
            jettyServer = new JettyServer(this, jettyServerPort, jettyServerHost); //start the jetty server
            jettyServer.start();
            getLogger().info("Jetty server started on port: " + jettyServerPort);
        } catch (Exception e) {
            getLogger().severe("Failed to start Jetty server: " + e.getMessage());
        }
    }

    /**
     * Runs when plugin disables
     */
    @Override
    public void onDisable() {
        try {
            webSocketManager.disconnectAllClients(); //end all sessions for when the server stops
            if (jettyServer != null) {
                jettyServer.stop();
                getLogger().info("Jetty server stopped.");
            }
        } catch (Exception e) {
            getLogger().severe("Failed to stop Jetty server: " + e.getMessage());
        }
        thread.shutdown();
    }

    private void loadConfigProperly() {
        saveDefaultConfig(); // create if missing

        FileConfiguration defaults = YamlConfiguration.loadConfiguration(
                new InputStreamReader(Objects.requireNonNull(getResource("config.yml")))
        );

        getConfig().setDefaults(defaults);
        getConfig().options().copyDefaults(true);
        saveConfig();
    }

    /**
     * the timeout config
     * @return vcTimeout
     */
    public int getVcTimeout() {
        return vcTimeout;
    }

    /**
     * Main Plugin logger
     * @return The Logger
     */
    public static Logger log() {
        return instance.getLogger();
    }

    /**
     * The VC bridge class
     * @return VoiceChatBridge
     * @see VoiceChatBridge
     */
    public static VoiceChatBridge getBridge() {
        return bridge;
    }

    /**
     * Get The Group System
     * @return GroupManager
     */
    public  static GroupManager getGroupManager() { return instance.groupManager;}

    /**
     * debug option with no throwable
     * @param section the part of plugin debugging
     * @param message the message
     */
    public static void debug(String section, String message) {
        if (instance != null && instance.debug) {
            log().info("[Debug][" + section + "] " + message);
        }
    }

    /**
     * debug option with a throwable
     * @param section the part of plugin debugging
     * @param message the message
     * @param t the throwable/error thrown
     */
    public static void debug(String section, String message, Throwable t) {
        if (instance != null && instance.debug) {
            log().info("[Debug][" + section + "] " + message + ", " + t);
        }
    }
}
