package io.github.theodoremeyer.spigotmc.simplevoicegeyser;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Auth System for Player.
 * The password needs to be more securely saved.
 */
public class PlayerVcPswd {

    private static final Map<String, String> playerPasswords = new HashMap<>();
    private static final Map<String, UUID> playerUUIDs = new HashMap<>();
    private static File file;
    private static FileConfiguration config;

    /**
     * Creating the folder, or registering it to memory.
     * May be moved to class constructor
     * @param pluginDataFolder folder to put the file in
     */
    protected static void init(File pluginDataFolder) {
        file = new File(pluginDataFolder, "playerpasswords.yml");
        if (!file.exists()) { //make sure the file exists for later use
            try {
                file.createNewFile();
            } catch (IOException e) {
                SVGPlugin.log().warning("[PlayerData] Couldn't create playerpasswords.yml");
                SVGPlugin.log().warning(e.getMessage());
                return;
            }
        }

        config = YamlConfiguration.loadConfiguration(file);
        loadPasswords();
    }

    /**
     * load from YAML
     */
    private static void loadPasswords() {
        for (String key : config.getKeys(false)) {
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
                    SVGPlugin.log().warning("[PlayerData] Invalid UUID for player " + key);
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
    private static void saveToFile(String playerName, String password, UUID uuid) {
        String key = playerName.toLowerCase();
        config.set(key + ".password", password);
        config.set(key + ".uuid", uuid.toString());
        try {
            config.save(file);
        } catch (IOException e) {
            SVGPlugin.log().warning("[PlayerData] Failed to save password for " + playerName);
            SVGPlugin.log().warning(e.getMessage());
        }
    }

    /**
     * Is password set for player
     * @param playerName player's name
     * @return whether password is set
     */
    public static boolean isPasswordSet(String playerName) {
        return playerPasswords.containsKey(playerName.toLowerCase());
    }

    /**
     * sets players password
     * @param player player to set for
     * @param password the password to set
     */
    public static void setPassword(Player player, String password) {
        String key = player.getName().toLowerCase();
        playerPasswords.put(key, password);
        playerUUIDs.put(key, player.getUniqueId());
        saveToFile(key, password, player.getUniqueId());
    }

    /**
     * Validates the player's password.
     * @param playerName the player's name for validation
     * @param password the password to check against
     * @return whether it is correct or not
     */
    public static boolean validatePassword(String playerName, String password) {
        String stored = playerPasswords.get(playerName.toLowerCase());
        return stored != null && stored.equals(password);
    }

    /**
     * Is the password length right
     * @param password password to check
     * @return whether password length is valid
     */
    public static boolean isPasswordLengthValid(String password) {
        return password.length() >= 5 && password.length() <= 20;
    }

    /**
     * Get players uuid
     * @param playerName the player name to get uuid from
     * @return the Players uuid
     */
    public static UUID getStoredUUID(String playerName) {
        String uuidStr = config.getString(playerName.toLowerCase() + ".uuid");
        if (uuidStr != null) {
            try {
                return UUID.fromString(uuidStr);
            } catch (IllegalArgumentException e) {
                SVGPlugin.log().warning("[PlayerData] Invalid UUID format for " + playerName);
            }
        }
        return null;
    }

    /**
     * If there is an uuid for player
     * @param playerName player to check for
     * @return whether uuid is linked for player
     */
    public static boolean isUUIDLinked(String playerName) {
        return playerUUIDs.containsKey(playerName.toLowerCase());
    }

    /**
     * Gets player name from players UUID
     * @param uuid players uuid
     * @return The player's name
     */
    public static String getUsernameFromUUID(UUID uuid) {
        for (Map.Entry<String, UUID> entry : playerUUIDs.entrySet()) {
            if (entry.getValue().equals(uuid)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
