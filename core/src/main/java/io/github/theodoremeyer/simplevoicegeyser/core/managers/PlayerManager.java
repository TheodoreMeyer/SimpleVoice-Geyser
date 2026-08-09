package io.github.theodoremeyer.simplevoicegeyser.core.managers;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.ConnectionStates;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Collection;
import java.util.Collections;
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
        UUID uuid = player.getUniqueId();
        String name = player.getName();

        // Replace any stale mapping for this UUID/name as one compound transition.
        SvgPlayer previousById = players.put(uuid, player);
        if (previousById != null && !previousById.getName().equals(name)) {
            playersByName.remove(previousById.getName(), previousById);
        }

        SvgPlayer previousByName = playersByName.put(name, player);
        if (previousByName != null && !previousByName.getUniqueId().equals(uuid)) {
            players.remove(previousByName.getUniqueId(), previousByName);
        }
    }

    /**
     * remove a player
     * Used when player leaves server
     * @param player the player to remove
     */
    public void removePlayer(SvgPlayer player) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();

        // Only remove if this exact instance is still mapped (session replacement safe).
        players.remove(uuid, player);
        playersByName.remove(name, player);

        SvgCore.getConnectionManager().disconnect(
                uuid,
                ConnectionStates.DisconnectCodes.PLAYER_LEAVE.getCode(),
                "Player left the game."
        );
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
