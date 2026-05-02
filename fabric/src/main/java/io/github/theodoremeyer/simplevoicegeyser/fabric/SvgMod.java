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
import io.github.theodoremeyer.simplevoicegeyser.fabric.impl.data.PasswordFile;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;

/**
 * Fabric entrypoint for SimpleVoiceGeyser
 */
public class SvgMod implements ModInitializer, Platform {

    private static SvgCore core;
    private static FabricVcBridge voiceChatBridge;

    private static LuckPermsHook luckPermsHook;

    private PasswordFile passwordFile;
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

            // Init core AFTER filesystem is ready
            core = new SvgCore(this);

            new FabricCommand();
            new SvgListener();

            luckPermsHook = new LuckPermsHook();

            ready = true;
            if (voiceChatBridge != null) {
                core.init();
            }

            logger.info(getPrefix() + "Initialization complete.");

        } catch (Exception e) {
            logger.error(getPrefix() + "Failed to initialize. ", e);
            disable();
        }
    }

    public static void injectBridge(FabricVcBridge bridge) {
        if (voiceChatBridge == null) {
            voiceChatBridge = bridge;
            if (ready) {
                core.init();
            }
        }
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
        File dir = getDataFolder();

        // 1. Guarantee base directory exists
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (!created && !dir.exists()) {
                logger.severe(getPrefix() + "Failed to create data directory: " + dir.getAbsolutePath());
            }
        }

        // 2. Optional: normalize to absolute path
        dir = dir.getAbsoluteFile();

        // 3. Now safe to construct files (NO file existence logic inside them anymore)
        this.passwordFile = new PasswordFile(dir, logger);
        this.configFile = new ConfigFile(dir, logger);
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
        } else if (type == DataType.PASSWORD) {
            return passwordFile;
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