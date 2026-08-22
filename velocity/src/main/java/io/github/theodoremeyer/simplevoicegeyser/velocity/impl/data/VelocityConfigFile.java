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

public class VelocityConfigFile {

    private static final DateTimeFormatter BACKUP_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Logger LOGGER = Logger.getLogger(VelocityConfigFile.class.getName());
    private final File configFile;
    private volatile JSONObject config;
    private volatile boolean writable = true;

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

    public Set<String> getKeys() {
        return config.keySet();
    }

    public boolean has(String key) {
        return config.has(key);
    }

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

    public String getString(String path) {
        return getValue(path, null);
    }

    public String getString(String path, String def) {
        return getValue(path, def);
    }

    public String getNestedString(String object, String key, String def) {
        Object value = config.opt(object);
        return value instanceof JSONObject nested ? nested.optString(key, def) : def;
    }

    public boolean getNestedBoolean(String object, String key, boolean def) {
        Object value = config.opt(object);
        return value instanceof JSONObject nested ? nested.optBoolean(key, def) : def;
    }

    public boolean getBoolean(String path, boolean def) {
        Object value = getRawValue(path);
        return value instanceof Boolean ? (Boolean) value : def;
    }

    public int getInt(String path, int def) {
        Object value = getRawValue(path);
        return value instanceof Number ? ((Number) value).intValue() : def;
    }

    public double getDouble(String path, double def) {
        return config.optDouble(path, def);
    }

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

    public synchronized void reload() {
        this.config = load();
    }

    public File getFile() {
        return configFile;
    }

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

    public record MigrationReport(String mode, String backupPath, int addedKeys, boolean migrated) {}

    private static String generateRandomSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
