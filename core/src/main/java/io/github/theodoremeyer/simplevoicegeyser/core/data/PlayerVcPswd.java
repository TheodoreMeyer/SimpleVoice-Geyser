package io.github.theodoremeyer.simplevoicegeyser.core.data;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.mindrot.jbcrypt.BCrypt;

import java.util.Locale;
import java.util.UUID;

/**
 * Class that Handles SVG Passwords
 * TODO: Test
 */
public final class PlayerVcPswd {

    private final PasswordFile store;

    /**
     * Create the password Handler
     * @param core SvgCore
     */
    public PlayerVcPswd(SvgCore core) {
        this.store = new PasswordFile(core.platform.getDataFolder(), SvgCore.getLogger());
    }

    // =========================
    // Queries
    // =========================

    /**
     * Check id a password is set
     * @param username username to check
     * @return if password is set
     */
    public boolean isPasswordSet(String username) {
        UUID uuid = getUUID(username);
        return uuid != null && store.exists(uuid);
    }

    /**
     * Get username from UUID
     * @param uuid uuid to check
     * @return username if found
     */
    @Nullable
    public String getUsername(UUID uuid) {
        return store.getUsername(uuid);
    }

    /**
     * Get uuid from username
     * @param username username to check
     * @return uuid if found
     */
    @Nullable
    public UUID getUUID(String username) {
        return store.getUUID(normalize(username));
    }

    // =========================
    // Password handling
    // =========================

    /**
     * Set a player's password
     * @param player player to set
     * @param password password to set
     */
    public void setPassword(SvgPlayer player, String password) {

        if (!isPasswordLengthValid(password)) {
            player.sendMessage(SvgCore.getPrefix() +
                    "Password must be between 8 and 32 characters.");
            return;
        }

        UUID uuid = player.getUniqueId();
        String username = player.getName();
        String hash = BCrypt.hashpw(password, BCrypt.gensalt(12));

        store.set(uuid, username, hash);

        player.sendMessage(SvgCore.getPrefix() + "Password set successfully.");
    }

    /**
     * Check if a password is correct
     * @param username username to check
     * @param password password to check
     * @return if it is correct
     */
    public boolean validatePassword(String username, String password) {
        UUID uuid = getUUID(username);
        if (uuid == null) return false;

        String stored = store.getPasswordHash(uuid);
        if (stored == null) return false;

        try {
            return BCrypt.checkpw(password, stored);
        } catch (IllegalArgumentException e) {
            SvgCore.getLogger().warning(
                    "[PlayerData] Invalid bcrypt hash for user " + username
            );
            return false;
        }
    }

    /**
     * See if the password length is valid
     * @param password password to check
     * @return if it follows the rules
     */
    public boolean isPasswordLengthValid(String password) {
        return password != null && password.length() >= 8 && password.length() <= 32;
    }

    // =========================
    // Shutdown
    // =========================

    /**
     * Save system
     */
    public void shutdown() {
        store.save();
        store.cleanup();
    }

    /**
     * Normalize a string for consistent lookups
     * @param name name to normalize
     * @return the name
     */
    private String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}