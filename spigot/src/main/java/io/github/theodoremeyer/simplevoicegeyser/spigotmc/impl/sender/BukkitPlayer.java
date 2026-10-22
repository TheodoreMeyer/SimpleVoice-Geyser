package io.github.theodoremeyer.simplevoicegeyser.spigotmc.impl.sender;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.spigotmc.SvgPlugin;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Bukkit/Paper/Folia player wrapper.
 * Off-region access (Jetty, audio) is hopped onto the player's entity/region thread when
 * Folia EntityScheduler is available; otherwise the Bukkit global scheduler is used (Spigot).
 */
public class BukkitPlayer extends SvgPlayer {

    private static final long SPATIAL_CACHE_PERIOD_TICKS = 2L;

    private final Player player;
    private final AtomicReference<Double> cachedLookYaw = new AtomicReference<>();
    private final EntityThreadAccess.Cancellable spatialTask = new EntityThreadAccess.Cancellable();

    public BukkitPlayer(Player player) {
        this.player = player;
    }

    /**
     * Start periodic spatial cache updates on the entity/global scheduler.
     */
    public void startSpatialCache() {
        Plugin plugin = plugin();
        if (plugin == null || !player.isOnline()) {
            return;
        }
        cancelSpatialCache();
        refreshSpatialCacheOnCurrentThread();
        spatialTask.set(EntityThreadAccess.runAtFixedRate(
                player,
                plugin,
                this::refreshSpatialCacheOnCurrentThread,
                1L,
                SPATIAL_CACHE_PERIOD_TICKS
        ));
    }

    /**
     * Stop spatial cache updates (call on quit).
     */
    public void cancelSpatialCache() {
        spatialTask.cancel();
        cachedLookYaw.set(null);
    }

    @Override
    public UUID getUniqueId() {
        return player.getUniqueId();
    }

    @Override
    public String getName() {
        return player.getName();
    }

    @Override
    public boolean hasPermission(String permission) {
        try {
            return EntityThreadAccess.supply(player, plugin(), () -> player.hasPermission(permission));
        } catch (Exception e) {
            SvgCore.getLogger().error(
                    "[Permissions] Unable to determine permission for '" + permission
                            + "' on entity thread, defaulting to true.",
                    e
            );
            return true;
        }
    }

    @Override
    public void chat(String message) {
        EntityThreadAccess.run(player, plugin(), () -> player.chat(message));
    }

    @Override
    public boolean isOnline() {
        try {
            return EntityThreadAccess.supply(player, plugin(), player::isOnline);
        } catch (Exception e) {
            return player.isOnline();
        }
    }

    @Override
    public Object getPlayer() {
        return player;
    }

    @Override
    public void sendMessage(String message) {
        EntityThreadAccess.run(player, plugin(), () -> player.sendMessage(translate(message)));
    }

    @Override
    public Double getCachedLookYaw() {
        return cachedLookYaw.get();
    }

    private void refreshSpatialCacheOnCurrentThread() {
        if (!player.isOnline()) {
            cachedLookYaw.set(null);
            return;
        }
        cachedLookYaw.set((double) player.getLocation().getYaw());
    }

    private Plugin plugin() {
        try {
            return SvgPlugin.getPlugin(SvgPlugin.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String translate(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
