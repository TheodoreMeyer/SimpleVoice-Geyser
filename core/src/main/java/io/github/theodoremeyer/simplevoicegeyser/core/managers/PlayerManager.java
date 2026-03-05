package io.github.theodoremeyer.simplevoicegeyser.core.managers;

import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Platform independant Player system
 */
public final class PlayerManager {

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
     * Get a Collection of all online players
     * @return players
     */
    public Collection<SvgPlayer> getAllPlayers() {
        return Collections.unmodifiableCollection(players.values());
    }

    //Add/Remove Players
    /**
     * Add a player
     * @param player the player to add
     */
    public void addPlayer(SvgPlayer player) {
        players.put(player.getUniqueId(), player);
        playersByName.put(player.getName(), player);
    }

    /**
     * remove a player
     * Used when player leaves server
     * @param player the player to remove
     */
    public void removePlayer(SvgPlayer player) {
        players.remove(player.getUniqueId());
        playersByName.remove(player.getName());
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
