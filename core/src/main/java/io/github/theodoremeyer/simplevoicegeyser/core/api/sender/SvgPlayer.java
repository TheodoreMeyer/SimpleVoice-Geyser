package io.github.theodoremeyer.simplevoicegeyser.core.api.sender;

import java.util.UUID;

/**
 * Class to 'represent' a player across multiple platforms
 */
public abstract class SvgPlayer extends Sender {

    /**
     * Create a representation of a player
     */
    public SvgPlayer() {}

    /**
     * Get the Player's UUID
     * @return player's uuid
     */
    public abstract UUID getUniqueId();

    /**
     * Does the player have a permission
     * please return true if unable to get whether they can or not,
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
     * Is the player online
     * @return whether they are online
     */
    public abstract boolean isOnline();

    /**
     * Allows to get the Platform's player instance if needed
     * @return the Player
     */
    public abstract Object getPlayer();

    /**
     * Cached look yaw in degrees for spatial audio and other off-thread readers.
     * <p>
     * Platforms that cannot expose a thread-safe yaw return {@code null}.
     *
     * @return yaw degrees, or {@code null} when unavailable
     */
    public Double getLookYawDegrees() {
        return null;
    }
}
