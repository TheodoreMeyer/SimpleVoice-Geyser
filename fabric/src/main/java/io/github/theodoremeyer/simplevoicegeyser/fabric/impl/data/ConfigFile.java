package io.github.theodoremeyer.simplevoicegeyser.fabric.impl.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.SvgFile;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ConfigFile extends SvgFile {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    private final File file;
    private Map<String, Object> data;

    public ConfigFile(File dataFolder) {
        this.file = new File(dataFolder, "config.json");

        try {
            if (!file.exists()) {
                boolean created = copyDefaultFromResources();

                if (!created) {
                    file.createNewFile();
                    this.data = new HashMap<>();
                    save();
                }
            }

            load();

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize ConfigFile", e);
        }
    }

    private boolean copyDefaultFromResources() {
        try (InputStream in = getClass().getResourceAsStream("/config.json")) {

            if (in == null) {
                SvgCore.getLogger().warning("[Config] No default config.json found.");
                return false;
            }

            Files.copy(in, file.toPath());
            return true;

        } catch (Exception e) {
            SvgCore.getLogger().error("[Config] Failed to copy default config.json", e);
            return false;
        }
    }

    private void load() {
        try (FileReader reader = new FileReader(file)) {
            Map<String, Object> loaded = GSON.fromJson(reader, TYPE);
            this.data = (loaded != null) ? loaded : new HashMap<>();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load config.json", e);
        }
    }

    @Override
    public Set<String> getKeys() {
        return Collections.unmodifiableSet(data.keySet());
    }

    @Override
    public void set(String path, String value) {
        setValue(path, value);
    }

    @Override
    public String getString(String path) {
        Object val = getValue(path);
        return val != null ? String.valueOf(val) : null;
    }

    @Override
    public String getString(String path, String def) {
        String val = getString(path);
        return val != null ? val : def;
    }

    @Override
    public boolean getBoolean(String path, boolean def) {
        Object val = getValue(path);
        return val instanceof Boolean ? (Boolean) val : def;
    }

    @Override
    public int getInt(String path, int def) {
        Object val = getValue(path);
        if (val instanceof Number) return ((Number) val).intValue();

        try {
            return val != null ? Integer.parseInt(val.toString()) : def;
        } catch (Exception e) {
            return def;
        }
    }

    @Override
    public double getDouble(String path, double def) {
        Object val = getValue(path);
        if (val instanceof Number) return ((Number) val).doubleValue();

        try {
            return val != null ? Double.parseDouble(val.toString()) : def;
        } catch (Exception e) {
            return def;
        }
    }

    @Override
    public void save() {
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save config.json", e);
        }
    }

    @Override
    public File getFile() {
        return file;
    }

    // ------------------------
    // Internal path traversal
    // ------------------------

    private Object getValue(String path) {
        String[] parts = path.split("\\.");
        Object current = data;

        for (String part : parts) {
            if (!(current instanceof Map)) return null;
            current = ((Map<?, ?>) current).get(part);
            if (current == null) return null;
        }

        return current;
    }

    private void setValue(String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = data;

        for (int i = 0; i < parts.length - 1; i++) {
            current = (Map<String, Object>) current.computeIfAbsent(parts[i], k -> new HashMap<>());
        }

        current.put(parts[parts.length - 1], value);
    }
}