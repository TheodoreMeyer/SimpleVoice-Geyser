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
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JSON-backed configuration file for the Velocity proxy module.
 * <p>
 * Values are read via dotted paths (e.g. {@code clients.lobby.url}) that
 * resolve into nested JSON objects; a missing file falls back to the bundled
 * {@code config.json} resource or built-in defaults. If the existing file is
 * unreadable the config becomes read-only to avoid overwriting user data.
 */
public class VelocityConfigFile {

    private static final DateTimeFormatter BACKUP_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Logger LOGGER = Logger.getLogger(VelocityConfigFile.class.getName());
    private final File configFile;
    private volatile JSONObject config;
    private volatile boolean writable = true;

    /**
     * Load the configuration from the given file.
     *
     * @param configFile the {@code config.json} file to load and save
     */
    public VelocityConfigFile(File configFile) {
        this.configFile = configFile;
        this.config = load();
    }

    private JSONObject load() {
        if (!configFile.exists()) {
            try (var resource = VelocityConfigFile.class.getClassLoader().getResourceAsStream("config.json")) {
                if (resource != null) {
                    return new JSONObject(new String(resource.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to load bundled config.json", e);
            }
            return nestedDefaults();
        }
        try {
            String content = Files.readString(configFile.toPath());
            return new JSONObject(content);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load config.json, preserving existing configuration", e);
            writable = false;
            return new JSONObject();
        }
    }

    /**
     * Get all top-level keys of the config.
     * @return set of top-level key names
     */
    public Set<String> getKeys() {
        return config.keySet();
    }

    /**
     * Check whether a top-level key exists.
     *
     * @param key top-level key name
     * @return {@code true} if the key is present at the root level
     */
    public boolean has(String key) {
        return config.has(key);
    }

    /**
     * Set a value at a dotted path, creating intermediate objects as needed.
     *
     * @param path  dotted config path (e.g. {@code clients.default.url})
     * @param value value to store; may be any JSON-serializable object
     */
    public synchronized void set(String path, Object value) {
        String[] parts = path.split("\\.");
        JSONObject target = config;
        for (int i = 0; i < parts.length - 1; i++) {
            Object child = target.opt(parts[i]);
            if (!(child instanceof JSONObject)) {
                child = new JSONObject();
                target.put(parts[i], child);
            }
            target = (JSONObject) child;
        }
        target.put(parts[parts.length - 1], value);
    }

    /**
     * Get a string value at a dotted path, or {@code null} if missing.
     *
     * @param path dotted config path
     * @return the stored string, or {@code null}
     */
    public String getString(String path) {
        return getValue(path, null);
    }

    /**
     * Get a string value at a dotted path with a fallback.
     *
     * @param path dotted config path
     * @param def  value returned when the key is missing or null
     * @return the stored string, or {@code def}
     */
    public String getString(String path, String def) {
        return getValue(path, def);
    }

    /**
     * Get a string from inside one nested object (e.g. {@code clients.lobby} + {@code url}).
     *
     * @param object name of the parent JSON object at the root level
     * @param key    key inside that object
     * @param def    value returned when either level is missing
     * @return the stored string, or {@code def}
     */
    public String getNestedString(String object, String key, String def) {
        Object value = config.opt(object);
        return value instanceof JSONObject nested ? nested.optString(key, def) : def;
    }

    /**
     * Get a boolean from inside one nested object (e.g. {@code clients.lobby} + {@code enabled}).
     *
     * @param object name of the parent JSON object at the root level
     * @param key    key inside that object
     * @param def    value returned when either level is missing
     * @return the stored boolean, or {@code def}
     */
    public boolean getNestedBoolean(String object, String key, boolean def) {
        Object value = config.opt(object);
        return value instanceof JSONObject nested ? nested.optBoolean(key, def) : def;
    }

    /**
     * Get a boolean value at a dotted path.
     *
     * @param path dotted config path
     * @param def  value returned when the key is missing or not a boolean
     * @return the stored boolean, or {@code def}
     */
    public boolean getBoolean(String path, boolean def) {
        Object value = getRawValue(path);
        return value instanceof Boolean ? (Boolean) value : def;
    }

    /**
     * Get an integer value at a dotted path.
     *
     * @param path dotted config path
     * @param def  value returned when the key is missing or not numeric
     * @return the stored integer, or {@code def}
     */
    public int getInt(String path, int def) {
        Object value = getRawValue(path);
        return value instanceof Number ? ((Number) value).intValue() : def;
    }

    /**
     * Get a double value at a dotted path.
     *
     * @param path dotted config path
     * @param def  value returned when the key is missing or not numeric
     * @return the stored double, or {@code def}
     */
    public double getDouble(String path, double def) {
        return config.optDouble(path, def);
    }

    /**
     * Write the current config to disk as pretty-printed JSON.
     * Skipped if the existing file could not be loaded (read-only mode).
     *
     * @throws RuntimeException if writing fails and the file was writable
     */
    public synchronized void save() {
        if (!writable) {
            LOGGER.warning("Skipping save because the existing config.json could not be loaded");
            return;
        }
        try {
            Files.writeString(configFile.toPath(), config.toString(2));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Reload the config from disk, discarding unsaved in-memory changes.
     */
    public synchronized void reload() {
        this.config = load();
    }

    /**
     * Get the underlying config file.
     * @return the file this config was loaded from
     */
    public File getFile() {
        return configFile;
    }

    /**
     * Run any migrations needed to bring an older bundled config up to date.
     * Currently generates a random {@code proxy.shared_secret} when missing or
     * blank, backing up the previous file first.
     *
     * @param trigger short label describing what invoked the migration
     * @return a report describing whether anything was changed
     */
    public MigrationReport migrateFromBundledDefaults(String trigger) {
        JSONObject proxy = config.optJSONObject("proxy");
        if (proxy == null || (!proxy.has("shared_secret")
                || proxy.optString("shared_secret", "").isBlank()
                || "GENERATED_ON_FIRST_START".equals(proxy.optString("shared_secret")))) {
            if (proxy == null) {
                proxy = new JSONObject();
                config.put("proxy", proxy);
            }
            proxy.put("shared_secret", generateRandomSecret());
            String backupPath = backupCurrentConfig();
            save();
            return new MigrationReport("json", backupPath, 1, true);
        }
        return new MigrationReport("json", "", 0, false);
    }

    private Object getRawValue(String path) {
        Object nested = getNestedValue(path);
        if (nested != null) return nested;
        return config.opt(path);
    }

    private Object getNestedValue(String path) {
        Object current = config;
        for (String part : path.split("\\.")) {
            if (!(current instanceof JSONObject object) || !object.has(part)) return null;
            current = object.get(part);
        }
        return current;
    }

    private <T> T getValue(String path, T def) {
        Object value = getRawValue(path);
        return value == null || JSONObject.NULL.equals(value) ? def : (T) value;
    }

    private static JSONObject nestedDefaults() {
        JSONObject defaults = new JSONObject();
        defaults.put("clients", new JSONObject()
                .put("default", new JSONObject()
                        .put("enabled", true)
                        .put("url", "ws://127.0.0.1:8001/ws")
                        .put("verify_ssl", false)
                        .put("auth", new JSONObject().put("global", true)))
                .put("lobby", new JSONObject()
                        .put("enabled", false)
                        .put("url", "ws://127.0.0.1:8002/ws")
                        .put("verify_ssl", false)
                        .put("auth", new JSONObject()
                                .put("global", false)
                                .put("secret", ""))));
        defaults.put("proxy", new JSONObject()
                .put("bind_address", "0.0.0.0")
                .put("port", 8080)
                .put("shared_secret", generateRandomSecret())
                .put("token-ttl-seconds", 120));
        defaults.put("ssl", new JSONObject()
                .put("type", "none")
                .put("file", new JSONObject()
                        .put("cert", "ssl/cert.pem")
                        .put("key", "ssl/key.pem")));
        defaults.put("config_version", "0.1.4");
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

    /**
     * Outcome of a config migration.
     *
     * @param mode       config format the migration ran against (always {@code "json"})
     * @param backupPath absolute path of the pre-migration backup, or {@code ""} if none was made
     * @param addedKeys  number of keys added during migration
     * @param migrated   whether any changes were written
     */
    public record MigrationReport(String mode, String backupPath, int addedKeys, boolean migrated) {}

    private static String generateRandomSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
