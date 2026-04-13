package io.github.theodoremeyer.simplevoicegeyser.core;

import io.github.theodoremeyer.simplevoicegeyser.core.api.data.SvgFile;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import org.mindrot.jbcrypt.BCrypt;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Auth System for Player.
 */
public final class PlayerVcPswd {

    private final Map<String, String> playerPasswords = new ConcurrentHashMap<>();
    private final Map<String, UUID> playerUUIDs = new ConcurrentHashMap<>();

    private final SvgFile config;

    /**
     * Creating the file, or registering it to memory.
     * @param pswdFile the file to use for storage
     */
    protected PlayerVcPswd(SvgFile pswdFile) {
        this.config = pswdFile;
        loadPasswords();
    }

    /**
     * load from YAML
     */
    private void loadPasswords() {
        for (String key : config.getKeys()) {
            String password = config.getString(key + ".password");
            String uuidStr = config.getString(key + ".uuid");
            if (password != null) {
                playerPasswords.put(key.toLowerCase(), password);
            }
            if (uuidStr != null) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    playerUUIDs.put(key.toLowerCase(), uuid);
                } catch (IllegalArgumentException e) {
                    SvgCore.getLogger().warning("[PlayerData] Invalid UUID for player " + key);
                }
            }
        }
    }

    /**
     * Save one password plus uuid identifier to file
     * @param playerName name of player to save to
     * @param password password to save
     * @param uuid players uuid
     */
    private void saveToFile(String playerName, String password, UUID uuid) {
        String key = playerName.toLowerCase();
        config.set(key + ".password", password);
        config.set(key + ".uuid", uuid);
        
        config.save();
    }

    /**
     * Is password set for player
     * @param playerName player's name
     * @return whether password is set
     */
    public boolean isPasswordSet(String playerName) {
        return playerPasswords.containsKey(playerName.toLowerCase());
    }

    /**
     * sets players password
     * USES Bcrypt for security.
     * @param player player to set for
     * @param password the password to set
     */
    public void setPassword(SvgPlayer player, String password) {
        if (!isPasswordLengthValid(password)) {
            player.sendMessage(SvgCore.getPrefix() + "Password must be between 8 and 32 characters.");
            return;
        }

        String key = player.getName().toLowerCase();

        String hash = BCrypt.hashpw(password, BCrypt.gensalt(12));

        playerPasswords.put(key, hash);
        playerUUIDs.put(key, player.getUniqueId());
        saveToFile(key, hash, player.getUniqueId());

        player.sendMessage(SvgCore.getPrefix() + "Set password: " + password);
    }

    /**
     * Validates the player's password.
     * @param playerName the player's name for validation
     * @param password the password to check against
     * @return whether it is correct or not
     */
    public boolean validatePassword(String playerName, String password) {
        String stored = playerPasswords.get(playerName.toLowerCase());
        if (stored == null) return false; // no password set

        try {
            return BCrypt.checkpw(password, stored);
        } catch (IllegalArgumentException e) {
            // This happens if the stored password is not a valid bcrypt hash
            SvgCore.getLogger().warning("[PlayerData] Invalid bcrypt hash for player " + playerName);
            return false;
        }
    }

    /**
     * Is the password length right
     * @param password password to check
     * @return whether password length is valid
     */
    public boolean isPasswordLengthValid(String password) {
        return password.length() >= 8 && password.length() <= 32;
    }

    /**
     * Get players uuid
     * @param playerName the player name to get uuid from
     * @return the Players uuid
     */
    public UUID getStoredUUID(String playerName) {
        return playerUUIDs.get(playerName.toLowerCase());
    }

    /**
     * Gets player name from players UUID
     * @param uuid players uuid
     * @return The player's name
     */
    public String getUsernameFromUUID(UUID uuid) {
        for (Map.Entry<String, UUID> entry : playerUUIDs.entrySet()) {
            if (entry.getValue().equals(uuid)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
