package io.github.theodoremeyer.simplevoicegeyser.spigotmc.impl.data;

import io.github.theodoremeyer.simplevoicegeyser.core.api.data.SvgFile;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

public class ConfigFile extends SvgFile {

    private static final DateTimeFormatter BACKUP_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private final FileConfiguration config;

    private final File configFile;

    public ConfigFile(File configFile) {
        this.configFile = configFile;
        this.config = YamlConfiguration.loadConfiguration(configFile);
    }

    @Override
    public Set<String> getKeys() {
        return config.getKeys(false);
    }

    @Override
    public boolean has(String key) {
        return config.contains(key);
    }

    @Override
    public void set(String path, Object value) {
        config.set(path, value);
    }

    @Override
    public String getString(String path) {
        return config.getString(path);
    }

    @Override
    public String getString(String path, String def) {
        return config.getString(path, def);
    }

    @Override
    public boolean getBoolean(String path, boolean def) {
        return config.getBoolean(path, def);
    }

    @Override
    public int getInt(String path, int def) {
        return config.getInt(path, def);
    }

    @Override
    public void save() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void reload() {
        FileConfiguration newConfig = YamlConfiguration.loadConfiguration(configFile);

        config.getKeys(false).forEach(k -> config.set(k, null));

        for (String key : newConfig.getKeys(false)) {
            config.set(key, newConfig.get(key));
        }
    }

    @Override
    public File getFile() {
        return configFile;
    }

    @Override
    public double getDouble(String path, double def) {
        return config.getDouble(path, def);
    }

    @Override
    public List<String> getStringList(String path, List<String> def) {
        return config.isList(path) ? config.getStringList(path) : def;
    }

    @Override
    public String backup() {
        if (!configFile.exists()) {
            return "";
        }
        String ts = LocalDateTime.now().format(BACKUP_TS);
        File backup = new File(configFile.getParentFile(), "config-" + ts + ".yml.bak");
        try {
            Files.copy(configFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return backup.getAbsolutePath();
        } catch (IOException e) {
            throw new RuntimeException("Failed backing up config.yml", e);
        }
    }
}
