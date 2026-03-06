package io.github.theodoremeyer.simplevoicegeyser.core.api.sender;

import java.util.UUID;

/**
 * Class to 'represent' a player across multiple platforms
 */
public abstract class SvgPlayer extends Sender {

    /**
     * Get the Player's UUID
     * @return player's uuid
     */
    public abstract UUID getUniqueId();

    /**
     * Does the player have a permission
     * @apiNote please return true if unable to get whether they can or not,
     *            then log to console that you did that.
     * @param permission the permission to check
     * @return whether they have it
     */
    public abstract boolean hasPermission(String permission);

    /**
     * Have the player chat to everyone
     * @param message the message to chat
     */
    public abstract void chat(String message);

    /**
     * Allows to get the Platform's player instance if needed
     * @return the Player
     */
    public abstract Object getPlayer();
}
