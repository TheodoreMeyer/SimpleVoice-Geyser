package io.github.theodoremeyer.simplevoicegeyser.velocity.impl.data;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class VelocityConfigFile {

    private static final DateTimeFormatter BACKUP_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Logger LOGGER = Logger.getLogger(VelocityConfigFile.class.getName());
    private final File configFile;
    private volatile JSONObject config;

    public VelocityConfigFile(File configFile) {
        this.configFile = configFile;
        this.config = load();
    }

    private JSONObject load() {
        if (!configFile.exists()) {
            return new JSONObject();
        }
        try {
            String content = Files.readString(configFile.toPath());
            return new JSONObject(content);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load config.json, using defaults", e);
            return new JSONObject();
        }
    }

    public Set<String> getKeys() {
        return config.keySet();
    }

    public boolean has(String key) {
        return config.has(key);
    }

    public synchronized void set(String path, Object value) {
        config.put(path, value);
    }

    public String getString(String path) {
        return config.optString(path, null);
    }

    public String getString(String path, String def) {
        return config.optString(path, def);
    }

    public boolean getBoolean(String path, boolean def) {
        return config.optBoolean(path, def);
    }

    public int getInt(String path, int def) {
        return config.optInt(path, def);
    }

    public double getDouble(String path, double def) {
        return config.optDouble(path, def);
    }

    public synchronized void save() {
        try {
            Files.writeString(configFile.toPath(), config.toString(2));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized void reload() {
        this.config = load();
    }

    public File getFile() {
        return configFile;
    }

    public MigrationReport migrateFromBundledDefaults(String trigger) {
        JSONObject defaults = loadBundledDefaults();
        if (defaults.isEmpty()) {
            return new MigrationReport("json", "", 0, false);
        }

        int addedKeys = 0;
        for (String key : defaults.keySet()) {
            if (!config.has(key)) {
                config.put(key, defaults.get(key));
                addedKeys++;
            }
        }

        if (addedKeys == 0) {
            return new MigrationReport("json", "", 0, false);
        }

        String backupPath = backupCurrentConfig();
        save();
        return new MigrationReport("json", backupPath, addedKeys, true);
    }

    private JSONObject loadBundledDefaults() {
        JSONObject defaults = new JSONObject();
        codeDefaults().forEach(defaults::put);
        return defaults;
    }

    private String backupCurrentConfig() {
        if (!configFile.exists()) {
            return "";
        }
        String ts = LocalDateTime.now().format(BACKUP_TS);
        File backup = new File(configFile.getParentFile(), "config-" + ts + ".json.bak");
        try {
            Files.copy(configFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return backup.getAbsolutePath();
        } catch (IOException e) {
            throw new RuntimeException("Failed backing up config.json", e);
        }
    }

    private static Map<String, Object> codeDefaults() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("config-info", "This file is used to configure Simple Voice Geyser. "
                + "For more information, see the wiki: https://theodoremeyer.github.io/projects/simplevoicegeyser/");
        defaults.put("client.vctimeout", 30);
        defaults.put("client.idletimeout", 2);
        defaults.put("client.requireBedrock", false);
        defaults.put("client.useEmoteForSVG", true);
        defaults.put("client.web-chat-enabled", true);
        defaults.put("server.group.default.enabled", true);
        defaults.put("server.group.default.password", "1a2b");
        defaults.put("server.group.default.force-on-web-join", false);
        defaults.put("server.port", 8080);
        defaults.put("server.bind-address", "0.0.0.0");
        defaults.put("server.context-path", "/");
        defaults.put("server.security.max-auth-failures", 5);
        defaults.put("server.security.auth-fail-duration", 3);
        defaults.put("server.security.auth-lock-duration", 8);
        defaults.put("server.audio.transport-mode", "auto");
        defaults.put("server.audio.allow-legacy-fallback", true);
        defaults.put("proxy.enabled", false);
        defaults.put("proxy.shared-secret", generateRandomSecret());
        defaults.put("proxy.token-ttl-seconds", 120);
        defaults.put("debug", false);
        defaults.put("updatechecker.enable", true);
        defaults.put("config-version", "0.1.1-dev-migration1");
        return defaults;
    }

    public record MigrationReport(String mode, String backupPath, int addedKeys, boolean migrated) {}

    private static String generateRandomSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
