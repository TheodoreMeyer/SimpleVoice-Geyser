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

public final class ProxyPasswordStore {

    private final File file;
    private final Logger logger;
    private JSONObject data;

    public ProxyPasswordStore(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "accounts.json");
        this.logger = logger;
        this.data = load();
    }

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

    public synchronized boolean isPasswordSet(String username) {
        UUID uuid = getUUID(username);
        return uuid != null && data.has(uuid.toString());
    }

    public synchronized boolean validatePassword(String username, String password) {
        UUID uuid = getUUID(username);
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
                return new JSONObject();
            }

            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            if (content.isBlank()) {
                return new JSONObject();
            }
            return new JSONObject(content);
        } catch (Exception e) {
            logger.warn("Failed to load proxy accounts file, starting fresh", e);
            return new JSONObject();
        }
    }

    private synchronized void save() {
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
