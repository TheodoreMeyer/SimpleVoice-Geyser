package io.github.theodoremeyer.simplevoicegeyser.velocity.geyser;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import io.github.theodoremeyer.simplevoicegeyser.velocity.VelocityPlugin;

import org.geysermc.geyser.api.GeyserApi;

import java.util.UUID;

/**
 * Hook with Geyser MC
 */
public final class GeyserForms {

    private PasswordForm formHandler = null;
    private boolean enabled = false;

    /**
     * Block class from being made, as it is static util
     */
    public GeyserForms(VelocityPlugin plugin) {
        ProxyServer server = plugin.getServer();

        server.getPluginManager().getPlugin("geyser").ifPresent(geyserPlugin -> {
            enabled = true;
            this.formHandler = new PasswordForm(plugin.getPasswordStore(), plugin.getLogger());
            plugin.getLogger().info("Hooked with GeyserMc!");
        });

    }

    /**
     * is the Player Bedrock
     * @param uuid the player's uuid
     * @return whether the player is bedrock
     */
    public Boolean isBedrock(UUID uuid) {
        if (enabled) {
           return GeyserApi.api().isBedrockPlayer(uuid);
        }
        return false;
    }

    public boolean openBedrockPasswordForm(Player player) {
        UUID uuid = player.getUniqueId();
        if (enabled && formHandler != null) {
            if (isBedrock(uuid)) {
                return formHandler.open(player);
            }
        }
        return false;
    }
}
