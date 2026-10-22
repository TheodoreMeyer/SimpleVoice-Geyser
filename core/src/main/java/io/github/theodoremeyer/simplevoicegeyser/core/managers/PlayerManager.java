package io.github.theodoremeyer.simplevoicegeyser.core.managers;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.ConnectionStates;
import io.github.theodoremeyer.simplevoicegeyser.core.server.packets.GroupsBroadcaster;
import io.github.theodoremeyer.simplevoicegeyser.core.server.packets.VoicePlayersBroadcaster;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Platform independent Player system
 */
public final class PlayerManager {

    /**
     * Create the player manager
     */
    public PlayerManager() {}

    //PLAYER CACHE
    /**
     * Players by UUID
     */
    public final Map<UUID, SvgPlayer> players = new ConcurrentHashMap<>();

    /**
     * Players by name
     */
    public final Map<String, SvgPlayer> playersByName = new ConcurrentHashMap<>();

    //Get Players
    /**
     * Get a player by uuid
     * @param uuid player's uuid
     * @return SvgPlayer the player
     */
    @Nullable
    public SvgPlayer getPlayer(UUID uuid) {
        return players.get(uuid);
    }

    /**
     * Get a player by name
     * @param name player's name
     * @return SvgPlayer: the player
     */
    @Nullable
    public SvgPlayer getPlayer(String name) {
        return playersByName.get(name);
    }

    /**
     * Find an online player by username, ignoring case and an optional Floodgate leading dot.
     * Used when accounts.json UUID is stale but the Bedrock player is already on the server.
     *
     * @param username login / stored username
     * @return online player or null
     */
    @Nullable
    public SvgPlayer findOnlineByUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        String trimmed = username.trim();
        SvgPlayer exact = playersByName.get(trimmed);
        if (exact != null) {
            return exact;
        }

        String lower = trimmed.toLowerCase(Locale.ROOT);
        String withoutDot = lower.startsWith(".") ? lower.substring(1) : lower;
        String withDot = lower.startsWith(".") ? lower : "." + lower;

        for (Map.Entry<String, SvgPlayer> entry : playersByName.entrySet()) {
            String online = entry.getKey().toLowerCase(Locale.ROOT);
            String onlineWithoutDot = online.startsWith(".") ? online.substring(1) : online;
            if (online.equals(lower)
                    || online.equals(withDot)
                    || online.equals(withoutDot)
                    || onlineWithoutDot.equals(withoutDot)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Get a Collection of all online players
     * @return players
     */
    public Collection<SvgPlayer> getAllPlayers() {
        return Collections.unmodifiableCollection(players.values());
    }

    // Add/Remove Players
    /**
     * Add a player
     * @param player the player to add
     */
    public void addPlayer(SvgPlayer player) {
        addPlayer(player, true);
    }

    /**
     * Add a player, optionally deferring web roster broadcasts (e.g. bulk seed on enable).
     * @param player the player to add
     * @param broadcast whether to push voice_players / groups to web clients
     */
    public void addPlayer(SvgPlayer player, boolean broadcast) {
        players.put(player.getUniqueId(), player);
        playersByName.put(player.getName(), player);
        SvgCore.getPendingJoinWaiter().notifyPlayerJoined(player.getUniqueId());
        if (broadcast) {
            VoicePlayersBroadcaster.broadcast();
            GroupsBroadcaster.broadcast();
        }
    }

    /**
     * remove a player
     * Used when player leaves server
     * @param player the player to remove
     */
    public void removePlayer(SvgPlayer player) {
        SvgCore.getConnectionManager().disconnect(
                player.getUniqueId(),
                ConnectionStates.DisconnectCodes.PLAYER_LEAVE.getCode(),
                "Player left the game."
        );
        //SvgCore.getWsManager().playerLeave(player);

        players.remove(player.getUniqueId());
        playersByName.remove(player.getName());
        VoicePlayersBroadcaster.broadcast();
        GroupsBroadcaster.broadcast();
    }

    // Is Player Online
    /**
     * If a player is online, by name
     * @param name the player's name
     * @return whether the player is online or not
     */
    public boolean isPlayerOnline(String name) {
        return playersByName.containsKey(name);
    }

    /**
     * If a player is online, by uuid
     * @param uuid player's uuid
     * @return whether the player is online or not
     */
    public boolean isPlayerOnline(UUID uuid) {
        return players.containsKey(uuid);
    }
}
