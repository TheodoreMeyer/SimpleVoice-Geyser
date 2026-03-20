package io.github.theodoremeyer.simplevoicegeyser.fabric.impl.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.SvgFile;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Platform-independent JSON implementation of SvgFile.
 * Replaces Bukkit YamlConfiguration.
 */
public class ConfigFile extends SvgFile {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Map<String, String>>() {}.getType();

    private final File file;
    private Map<String, String> data;

    public ConfigFile(File file) {
        this.file = file;

        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();

                this.data = new HashMap<>();
                save();

            } catch (IOException e) {
                throw new RuntimeException("Failed to create JSON file", e);
            }
        } else {
            load();
        }
    }

    private void load() {
        try (FileReader reader = new FileReader(file)) {
            Map<String, String> loaded = GSON.fromJson(reader, TYPE);
            this.data = (loaded != null) ? loaded : new HashMap<>();
        } catch (Exception e) {
            this.data = new HashMap<>();
        }
    }

    @Override
    public Set<String> getKeys() {
        return Collections.unmodifiableSet(data.keySet());
    }

    @Override
    public void set(String path, String value) {
        data.put(path, value);
    }

    @Override
    public String getString(String path) {
        return data.get(path);
    }

    @Override
    public String getString(String path, String def) {
        return data.getOrDefault(path, def);
    }

    @Override
    public boolean getBoolean(String path, boolean def) {
        String val = data.get(path);
        return val == null ? def : Boolean.parseBoolean(val);
    }

    @Override
    public int getInt(String path, int def) {
        try {
            return Integer.parseInt(data.get(path));
        } catch (Exception e) {
            return def;
        }
    }

    @Override
    public double getDouble(String path, double def) {
        try {
            return Double.parseDouble(data.get(path));
        } catch (Exception e) {
            return def;
        }
    }

    @Override
    public void save() {
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save JSON file", e);
        }
    }

    @Override
    public File getFile() {
        return file;
    }
}