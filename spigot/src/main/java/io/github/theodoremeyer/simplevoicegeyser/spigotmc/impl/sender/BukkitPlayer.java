package io.github.theodoremeyer.simplevoicegeyser.spigotmc.impl.sender;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.schedule.TaskScheduler;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Bukkit/Paper/Folia player bridge.
 * <p>
 * Identity and permission snapshots are retained as immutable/thread-safe data so
 * browser, Geyser, and audio threads do not touch live entity state off-region.
 * Mutations such as chat and messages are marshalled onto the entity scheduler.
 */
public class BukkitPlayer extends SvgPlayer {

    private static final String[] CACHED_PERMISSIONS = {
            "svg.admin",
            "svg.vc",
            "svg.vc.join",
            "svg.vc.group",
            "svg.vc.group.create",
            "svg.vc.group.join",
            "svg.vc.group.type",
            "svg.vc.group.type.isolated",
            "svg.vc.group.setpersistent",
            "svg.vc.group.setpersistant"
    };

    private final Player player;
    private final UUID uniqueId;
    private final String name;
    private final AtomicBoolean online = new AtomicBoolean(true);
    private final AtomicReference<Double> cachedYawDegrees = new AtomicReference<>();
    private final Map<String, Boolean> permissionCache = new ConcurrentHashMap<>();

    /**
     * @param player online Bukkit player; must be called on the owning region/thread
     */
    public BukkitPlayer(Player player) {
        this.player = player;
        this.uniqueId = player.getUniqueId();
        this.name = player.getName();
        refreshOwnedState(player);
    }

    /**
     * Refresh yaw/permission snapshots. Must run on the player's owning region.
     * @param player live player
     */
    public void refreshOwnedState(Player player) {
        if (player == null || !player.getUniqueId().equals(uniqueId)) {
            return;
        }
        cachedYawDegrees.set((double) player.getLocation().getYaw());
        for (String permission : CACHED_PERMISSIONS) {
            permissionCache.put(permission, player.hasPermission(permission));
        }
    }

    /**
     * Update only the cached look yaw. Must run on the player's owning region.
     * @param yawDegrees yaw in degrees
     */
    public void updateCachedYaw(float yawDegrees) {
        cachedYawDegrees.set((double) yawDegrees);
    }

    /**
     * Mark the player offline for async readers.
     */
    public void markOffline() {
        online.set(false);
    }

    @Override
    public UUID getUniqueId() {
        return uniqueId;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean hasPermission(String permission) {
        if (permission == null) {
            return false;
        }

        Boolean cached = permissionCache.get(permission);
        if (cached != null) {
            return cached;
        }

        // Off-thread / uncached: schedule a refresh and follow SvgPlayer's
        // documented fallback (allow + log) rather than blocking a WS/audio thread.
        TaskScheduler scheduler = SvgCore.getTaskScheduler();
        scheduler.executeForEntity(
                "svg/player/permission-refresh",
                uniqueId,
                () -> {
                    Player live = resolveOnlinePlayer();
                    if (live != null) {
                        permissionCache.put(permission, live.hasPermission(permission));
                    }
                },
                null
        );

        SvgCore.getLogger().warning(
                "[Permissions] Off-thread permission check for '"
                        + permission
                        + "' without cache for "
                        + name
                        + "; defaulting to true until entity refresh completes."
        );
        return true;
    }

    @Override
    public void chat(String message) {
        String outbound = message;
        SvgCore.getTaskScheduler().executeForEntity(
                "svg/player/chat",
                uniqueId,
                () -> {
                    Player live = resolveOnlinePlayer();
                    if (live != null) {
                        live.chat(outbound);
                    }
                },
                null
        );
    }

    @Override
    public boolean isOnline() {
        return online.get();
    }

    @Override
    public Object getPlayer() {
        // Live Bukkit entity. Callers must not touch entity state off the owning region;
        // prefer UUID snapshots and {@link #getLookYawDegrees()} across threads.
        return player;
    }

    @Override
    public void sendMessage(String message) {
        String translated = translate(message);
        SvgCore.getTaskScheduler().executeForEntity(
                "svg/player/send-message",
                uniqueId,
                () -> {
                    Player live = resolveOnlinePlayer();
                    if (live != null) {
                        live.sendMessage(translated);
                    }
                },
                null
        );
    }

    @Override
    public Double getLookYawDegrees() {
        return cachedYawDegrees.get();
    }

    private Player resolveOnlinePlayer() {
        if (!online.get()) {
            return null;
        }
        org.bukkit.entity.Player live = org.bukkit.Bukkit.getPlayer(uniqueId);
        if (live == null || !live.isOnline()) {
            online.set(false);
            return null;
        }
        return live;
    }

    private String translate(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
