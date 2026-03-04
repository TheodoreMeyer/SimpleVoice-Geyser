package io.github.theodoremeyer.spigotmc.simplevoicegeyser.geyser;

import org.bukkit.Bukkit;
import org.geysermc.cumulus.form.Form;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.geyser.api.GeyserApi;

import javax.annotation.Nullable;
import java.util.UUID;

public class GeyserHook {

    /**
     * Whether Geyser is enabled in some form
     * @return geyser enabled
     */
    public static boolean isEnabled() {
        boolean enabled = isFloodgate();

        if (isGeyser()) enabled = true;

        return enabled;
    }

    /**
     * Is Geyser-Spigot Enabled
     * @return if its enabled
     */
    public static boolean isGeyser() {
        return Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot");
    }

    /**
     * Is Floodgate active
     * @return if its enabled
     */
    public static boolean isFloodgate() {
        return Bukkit.getPluginManager().isPluginEnabled("floodgate");
    }

    /**
     * is the Player Bedrock
     * @param uuid the player's uuid
     * @return whether the player is bedrock
     */
    @Nullable
    public static Boolean isBedrock(UUID uuid) {

        if (isFloodgate()) {
            return FloodgateApi.getInstance().isFloodgatePlayer(uuid);
        } else if (isGeyser()) {
            return GeyserApi.api().isBedrockPlayer(uuid);
        } else {
            return null;
        }
    }

    /**
     * Layer for sending Forms to bedrock players.
     * @param uuid player's uuid
     * @param form the form
     */
    public static void sendForm(UUID uuid, Form form) {
        if (!isBedrock(uuid).booleanValue()) return;

        if (isFloodgate()) {
            FloodgateApi.getInstance().sendForm(uuid, form);
        } else if (isGeyser()) {
            GeyserApi.api().sendForm(uuid, form);
        }
    }
}
