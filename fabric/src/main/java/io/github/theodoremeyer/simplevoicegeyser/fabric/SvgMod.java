package io.github.theodoremeyer.simplevoicegeyser.fabric;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.Platform;
import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgColor;
import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgLogger;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.DataType;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.SvgFile;
import io.github.theodoremeyer.simplevoicegeyser.core.svc.VoiceChatBridge;
import io.github.theodoremeyer.simplevoicegeyser.fabric.hooks.LuckPermsHook;
import io.github.theodoremeyer.simplevoicegeyser.fabric.impl.FabricCommand;
import io.github.theodoremeyer.simplevoicegeyser.fabric.impl.FabricLogger;
import io.github.theodoremeyer.simplevoicegeyser.fabric.impl.FabricVcBridge;
import io.github.theodoremeyer.simplevoicegeyser.fabric.impl.SvgListener;
import io.github.theodoremeyer.simplevoicegeyser.fabric.impl.data.ConfigFile;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Fabric entrypoint for SimpleVoiceGeyser
 */
public class SvgMod implements ModInitializer, Platform {

    private static SvgCore core;
    private static FabricVcBridge voiceChatBridge;

    private static LuckPermsHook luckPermsHook;

    private ConfigFile configFile;

    private final FabricLogger logger = new FabricLogger();

    private static boolean ready = false;

    @Override
    public void onInitialize() {

        if (ready) {
            logger.severe("Already Initialized!");
            return;
        }

        logger.info(getPrefix() + "Initializing Fabric platform...");

        try {

            createFiles();

            core = new SvgCore(this);

            luckPermsHook = new LuckPermsHook();

            ready = true;

            logger.info(getPrefix() + "Fabric bootstrap complete.");

            if (voiceChatBridge != null) {

                if (!core.init()) {
                    logger.severe(getPrefix() + "Core init failed.");
                    disable();
                    return;
                }

                new FabricCommand();
                new SvgListener();
            }

        } catch (Exception e) {
            logger.error(getPrefix() + "Failed to initialize.", e);
            disable();
        }
    }

    public static void injectBridge(FabricVcBridge bridge) {
        if (voiceChatBridge != null) { return; }
        voiceChatBridge = bridge;

        if (!ready || core == null) {
            return;
        }

        if (!core.init()) {
            SvgCore.getLogger().severe("Core init failed after VC injection.");
            SvgCore.disable();
            return;
        }

        new FabricCommand();
        new SvgListener();
    }

    //Permissions
    public static LuckPermsHook getLuckPerms() {
        return luckPermsHook;
    }

    // -----------------------------
    // Platform implementation
    // -----------------------------

    public File getDataFolder() {
        return FabricLoader.getInstance()
                .getConfigDir()
                .resolve("SimpleVoice-Geyser")
                .toFile();
    }

    public void createFiles() {
        File dir = getDataFolder().getAbsoluteFile();

        try {
            Files.createDirectories(dir.toPath());
        } catch (IOException e) {
            logger.error(getPrefix() + "Failed to create data directory: " + dir.getAbsolutePath(), e);
            return;
        }

        this.configFile = new ConfigFile(dir, logger);
        SvgFile.MigrationReport migration = this.configFile.migrateFromBundledDefaults("startup");
        logger.info("[Config] migration trigger=startup mode=" + migration.mode()
                + " addedKeys=" + migration.addedKeys()
                + " backup=" + (migration.backupPath().isBlank() ? "none" : migration.backupPath()));
    }

    @Override
    public void disable() {
        logger.severe(getPrefix() + "Disabling SimpleVoiceGeyser (Fabric) due to fatal error.");
    }

    @Override
    public String getPrefix() {
        return SvgColor.GREEN + "[" + SvgColor.AQUA + "SVG" + SvgColor.GREEN + "] " + SvgColor.RESET;
    }

    @Override
    public String getServerMcVersion() {
        return FabricLoader.getInstance()
                .getModContainer("minecraft")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse(null);
    }

    @Override
    public String getServerPlatform() {
        return "fabric";
    }

    @Override
    public VoiceChatBridge registerVcBridge() {
        return voiceChatBridge;
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
        if (name.equalsIgnoreCase("LuckPerms")) {
            return isClassPresent("net.luckperms.api.LuckPerms");
        } else if (name.equalsIgnoreCase("Geyser-Spigot")) {
            return isClassPresent("org.geysermc.api.GeyserApi");
        } else if (name.equalsIgnoreCase("floodgate")) {
            return isClassPresent("org.geysermc.floodgate.api.FloodgateApi");
        }
        return false;
    }

    private boolean isClassPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
