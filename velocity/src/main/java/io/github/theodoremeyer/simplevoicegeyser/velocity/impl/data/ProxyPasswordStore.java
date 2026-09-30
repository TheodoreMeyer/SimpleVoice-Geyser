package io.github.theodoremeyer.simplevoicegeyser.velocity.impl.data;

import org.json.JSONObject;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.UUID;

/**
 * Persistent store of BCrypt-hashed web client passwords, keyed by player UUID
 * in {@code accounts.json} inside the plugin data folder.
 */
public final class ProxyPasswordStore {

    private final File file;
    private final Logger logger;
    private JSONObject data;
    private boolean writable;

    /**
     * Create the store and load (or initialize) {@code accounts.json}.
     *
     * @param dataFolder plugin data directory containing the accounts file
     * @param logger     logger used for load/save diagnostics
     */
    public ProxyPasswordStore(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "accounts.json");
        this.logger = logger;
        this.data = load();
    }

    /**
     * Look up the UUID an account was registered under, matching usernames
     * case-insensitively.
     *
     * @param username account username to search for
     * @return the stored UUID, or {@code null} if no matching account exists
     */
    public synchronized UUID getUUID(String username) {
        String normalized = normalize(username);
        for (String key : data.keySet()) {
            JSONObject entry = data.optJSONObject(key);
            if (entry != null && normalized.equals(normalize(entry.optString("username", "")))) {
                try {
                    return UUID.fromString(key);
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Check whether an account with the given username exists.
     *
     * @param username account username to check
     * @return {@code true} if a password is stored for this username
     */
    public synchronized boolean isPasswordSet(String username) {
        UUID uuid = getUUID(username);
        return uuid != null && data.has(uuid.toString());
    }

    /**
     * Validate credentials without checking which player is asking.
     * Prefer {@link #validatePassword(String, String, UUID)} so a player can
     * only authenticate as themselves.
     *
     * @param username account username
     * @param password plaintext password to check
     * @return {@code true} if the password matches the stored hash
     */
    public synchronized boolean validatePassword(String username, String password) {
        UUID uuid = getUUID(username);
        return validatePassword(username, password, uuid);
    }

    /**
     * Validate that the given password belongs to the given username and that
     * the account's stored UUID matches the expected player UUID.
     *
     * @param username    account username
     * @param password    plaintext password to check
     * @param expectedUuid UUID of the online player attempting authentication
     * @return {@code true} if all three match
     */
    public synchronized boolean validatePassword(String username, String password, UUID expectedUuid) {
        UUID uuid = getUUID(username);
        if (expectedUuid == null || !expectedUuid.equals(uuid)) {
            return false;
        }
        if (uuid == null) {
            return false;
        }

        JSONObject entry = data.optJSONObject(uuid.toString());
        if (entry == null) {
            return false;
        }

        String hash = entry.optString("passwordHash", null);
        if (hash == null || hash.isBlank()) {
            return false;
        }

        try {
            return BCrypt.checkpw(password, hash);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid bcrypt hash for user {}", username);
            return false;
        }
    }

    /**
     * Set or replace the password for a player, hashing it with BCrypt and
     * persisting the store immediately.
     *
     * @param uuid     UUID of the player the account belongs to
     * @param username account username to register
     * @param password new plaintext password
     */
    public synchronized void setPassword(UUID uuid, String username, String password) {
        String hash = BCrypt.hashpw(password, BCrypt.gensalt(12));
        JSONObject entry = new JSONObject();
        entry.put("username", username);
        entry.put("passwordHash", hash);
        data.put(uuid.toString(), entry);
        save();
    }

    private JSONObject load() {
        try {
            if (!file.exists()) {
                if (file.getParentFile() != null) {
                    file.getParentFile().mkdirs();
                }
                Files.writeString(file.toPath(), "{}", StandardCharsets.UTF_8);
                writable = true;
                return new JSONObject();
            }

            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            if (content.isBlank()) {
                writable = true;
                return new JSONObject();
            }
            writable = true;
            return new JSONObject(content);
        } catch (Exception e) {
            logger.warn("Failed to load proxy accounts file; password updates are disabled", e);
            writable = false;
            return new JSONObject();
        }
    }

    private synchronized void save() {
        if (!writable) {
            logger.warn("Skipping save because the existing proxy accounts file could not be loaded");
            return;
        }
        try {
            Files.writeString(file.toPath(), data.toString(2), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("Failed to save proxy accounts file", e);
        }
    }

    private String normalize(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }
}
