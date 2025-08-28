package io.github.theodoremeyer.spigotmc.simplevoicegeysertoo;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Main Class of Plugin
 */
public class SVGPlugin extends JavaPlugin {
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
    private Boolean debug;

    /**
     * When the plugin loads
     */
    @Override
    public void onEnable() {
        BukkitVoicechatService service = Bukkit.getServicesManager().load(BukkitVoicechatService.class);
        this.debug = getConfig().getBoolean("Debug", false);

        if (service != null) { //make sure bukkitvoicechatservice exists
            VoiceChatBridge voicechatBridge = new VoiceChatBridge();
            service.registerPlugin(voicechatBridge); //register the main api class
            bridge = voicechatBridge;
            getLogger().info("Registered plugin with Simple Voice Chat.");
        } else {
            //added this because of some early problems with registering the plugin
            getLogger().severe("Failed to register with Simple Voice Chat: service not found.");
            Collection<Class<?>> knownServices = Bukkit.getServicesManager().getKnownServices();
            if (debug == true) {
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
        }

        instance = this;
        Bukkit.getPluginManager().registerEvents(new SvgListener(), this);
        PlayerVcPswd.init(this.getDataFolder());
        Objects.requireNonNull(getCommand("svg")).setExecutor(new SvgCommand());
        saveResource("playerpasswords.yml", false);

        saveDefaultConfig();
        int rawTimeout = getConfig().getInt("client.vctimeout", 30); //get config from config.yml
        this.vcTimeout = Math.max(0, Math.min(120, rawTimeout));
        int jettyServerPort = getConfig().getInt("server.port", 8080);
        this.webSocketManager = new WebSocketManager();

        try {
            jettyServer = new JettyServer(jettyServerPort); //start the jetty server
            jettyServer.start();
            getLogger().info("Jetty server started on port 8080");
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
    }

    /**
     * create an easy way for other classes to access what they need to work.
     * @return SVGPlugin
     * @see SVGPlugin
     */
    protected static SVGPlugin getInstance() {
        return instance;
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
        return getInstance().getLogger();
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
     * debug option with no throwable
     * @param section the part of plugin debugging
     * @param message the message
     */
    public void debug(String section, String message) {
        if (debug) {
            log().info("[Debug][" + section + "] " + message);
        }
    }

    /**
     * debug option with a throwable
     * @param section the part of plugin debugging
     * @param message the message
     * @param t the throwable/error thrown
     */
    public void debug(String section, String message, Throwable t) {
        if (debug) {
            log().info( "[Debug][" + section + "] " + message + "," + t);
        }
    }

}
