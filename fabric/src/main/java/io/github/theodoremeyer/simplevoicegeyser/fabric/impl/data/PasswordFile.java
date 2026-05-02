package io.github.theodoremeyer.simplevoicegeyser.fabric.impl.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.SvgFile;
import io.github.theodoremeyer.simplevoicegeyser.fabric.impl.FabricLogger;

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
 */
public class PasswordFile extends SvgFile {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Map<player, Map<field, value>>
    private static final Type TYPE =
            new TypeToken<Map<String, Map<String, String>>>() {}.getType();

    private final File file;
    private Map<String, Map<String, String>> data;
    
    private final FabricLogger logger;

    public PasswordFile(File dataFolder, FabricLogger logger) {
        this.logger = logger;
        this.file = new File(dataFolder, "password.json");

        try {
            if (!file.exists()) {
                boolean created = file.createNewFile();
                if (!created && !file.exists()) {
                    logger.error("Failed to create password.json: " + file.getAbsolutePath());
                }

                this.data = new HashMap<>();
                save();
            } else {
                load();
            }
        } catch (Exception e) {
            logger.error("[Password] Failed to initialize password.json", e);
            this.data = new HashMap<>();
        }
    }

    private void load() {
        try (FileReader reader = new FileReader(file)) {
            Map<String, Map<String, String>> loaded = GSON.fromJson(reader, TYPE);
            this.data = (loaded != null) ? loaded : new HashMap<>();
        } catch (Exception e) {
            this.data = new HashMap<>();
            logger.error("Failed to load JSON file, starting with empty data", e);
        }
    }

    @Override
    public Set<String> getKeys() {
        return Collections.unmodifiableSet(data.keySet());
    }

    @Override
    public boolean has(String key) {
        String[] parts = splitPath(key);
        if (parts == null) return false;

        Map<String, String> section = data.get(parts[0]);
        if (section == null) return false;

        return section.containsKey(parts[1]);
    }

    @Override
    public void set(String path, Object value) {
        if (value == null) return;

        String[] parts = splitPath(path);
        if (parts == null) {
            logger.warning("Invalid path for set: " + path);
            return;
        }

        // Force string storage (this is correct for passwords)
        String str = String.valueOf(value);

        data.computeIfAbsent(parts[0], k -> new HashMap<>())
                .put(parts[1], str);

        save();
    }

    @Override
    public String getString(String path) {
        String[] parts = splitPath(path);
        if (parts == null) return null;

        Map<String, String> section = data.get(parts[0]);
        if (section == null) return null;

        return section.get(parts[1]);
    }

    @Override
    public String getString(String path, String def) {
        String val = getString(path);
        return val != null ? val : def;
    }

    @Override
    public boolean getBoolean(String path, boolean def) {
        String val = getString(path);
        return val == null ? def : Boolean.parseBoolean(val);
    }

    @Override
    public int getInt(String path, int def) {
        String val = getString(path);
        if (val == null) return def;

        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @Override
    public double getDouble(String path, double def) {
        String val = getString(path);
        if (val == null) return def;

        try {
            return Double.parseDouble(val);
        } catch (NumberFormatException e) {
            SvgCore.debug("Pswd", "Failed to parse double for path '" + path + "': " + val, e);
            return def;
        }
    }

    @Override
    public void save() {
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            logger.error("[Password] Failed to save JSON file", e);
        }
    }

    @Override
    public File getFile() {
        return file;
    }

    /**
     * Splits a path like "player.password" into ["player", "password"]
     */
    private String[] splitPath(String path) {
        if (path == null) return null;

        String[] parts = path.split("\\.", 2);
        if (parts.length != 2) {
            logger.warning("Invalid path: " + path);
            return null;
        }

        return parts;
    }
}