package io.github.theodoremeyer.simplevoicegeyser.spigotmc;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.Platform;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.DataType;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.SvgFile;
import io.github.theodoremeyer.simplevoicegeyser.core.svc.VoiceChatBridge;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

public class SvgPlugin extends JavaPlugin implements Platform {

    private SvgCore core;

    //JAVA PLUGIN
    @Override
    public void onLoad() {
        this.core = new SvgCore(this);
    }

    @Override
    public void onEnable() {
        core.init();
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
            VoiceChatBridge voicechatBridge = new VoiceChatBridge(this);
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
        return null;
    }

    @Override
    public boolean isDependencyEnabled(String name) {
        return Bukkit.getPluginManager().isPluginEnabled(name);
    }
}
