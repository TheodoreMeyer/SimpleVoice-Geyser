package io.github.theodoremeyer.simplevoicegeyser.core.geyser;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import org.geysermc.cumulus.form.Form;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.geyser.api.GeyserApi;

import java.util.Optional;
import java.util.UUID;

/**
 * Hook with Geyser MC
 */
public final class GeyserHook {

    /**
     * Block class from being made, as it is static util
     */
    private GeyserHook() {}

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
        return SvgCore.getPlatform().isDependencyEnabled("Geyser-Spigot");
    }

    /**
     * Is Floodgate active
     * @return if its enabled
     */
    public static boolean isFloodgate() {
        return SvgCore.getPlatform().isDependencyEnabled("floodgate");
    }

    /**
     * is the Player Bedrock
     * @param uuid the player's uuid
     * @return whether the player is bedrock
     */
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
     * Floodgate username prefix when Floodgate is present.
     * <p>
     * Never hardcode {@code "."}; servers may configure a custom prefix.
     *
     * @return prefix, or empty when Floodgate is unavailable
     */
    public static Optional<String> getFloodgatePrefix() {
        if (!isFloodgate()) {
            return Optional.empty();
        }

        try {
            String prefix = FloodgateApi.getInstance().getPlayerPrefix();
            if (prefix == null || prefix.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(prefix);
        } catch (Throwable t) {
            SvgCore.getLogger().debug("GeyserHook: Failed to read Floodgate player prefix", t);
            return Optional.empty();
        }
    }

    /**
     * Layer for sending Forms to bedrock players.
     * @param uuid player's uuid
     * @param form the form
     */
    public static void sendForm(UUID uuid, Form form) {
        Boolean bedrock = isBedrock(uuid);

        if (bedrock == null || !bedrock) return;

        if (isFloodgate()) {
            FloodgateApi.getInstance().sendForm(uuid, form);
        } else if (isGeyser()) {
            GeyserApi.api().sendForm(uuid, form);
        }
    }
}
