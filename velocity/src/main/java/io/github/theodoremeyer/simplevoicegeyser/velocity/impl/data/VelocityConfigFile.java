package io.github.theodoremeyer.simplevoicegeyser.velocity.impl.data;

import io.github.theodoremeyer.simplevoicegeyser.core.api.data.SvgConfig;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.SvgFile;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

public class VelocityConfigFile extends SvgFile {

    private static final DateTimeFormatter BACKUP_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private final File configFile;
    private JSONObject config;

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
            return new JSONObject();
        }
    }

    @Override
    public Set<String> getKeys() {
        return config.keySet();
    }

    @Override
    public boolean has(String key) {
        return config.has(key);
    }

    @Override
    public void set(String path, Object value) {
        config.put(path, value);
    }

    @Override
    public String getString(String path) {
        return config.optString(path, null);
    }

    @Override
    public String getString(String path, String def) {
        return config.optString(path, def);
    }

    @Override
    public boolean getBoolean(String path, boolean def) {
        return config.optBoolean(path, def);
    }

    @Override
    public int getInt(String path, int def) {
        return config.optInt(path, def);
    }

    @Override
    public double getDouble(String path, double def) {
        return config.optDouble(path, def);
    }

    @Override
    public void save() {
        try {
            Files.writeString(configFile.toPath(), config.toString(2));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void reload() {
        this.config = load();
    }

    @Override
    public File getFile() {
        return configFile;
    }

    @Override
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
        SvgConfig.codeDefaults().forEach(defaults::put);
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
}
