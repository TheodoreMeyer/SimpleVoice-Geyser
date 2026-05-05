package io.github.theodoremeyer.simplevoicegeyser.fabric.impl.data;

import com.google.gson.*;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.SvgFile;
import io.github.theodoremeyer.simplevoicegeyser.fabric.impl.FabricLogger;

import java.io.*;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Set;

public class ConfigFile extends SvgFile {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File file;
    private JsonObject data;
    
    private FabricLogger logger;

    public ConfigFile(File dataFolder, FabricLogger logger) {
        this.logger = logger;
        this.file = new File(dataFolder, "config.json");

        try {
            if (!file.exists()) {
                boolean created = file.createNewFile();
                if (!created && !file.exists()) {
                    logger.error("Failed to create config.json: " + file.getAbsolutePath());
                }

                // Only responsibility: initialize empty + let higher layer decide defaults
                this.data = new JsonObject();
                save();
            } else {
                load();
            }
        } catch (Exception e) {
            logger.error("[Config] Failed to initialize config.json", e);
            this.data = new JsonObject();
        }
    }

    private boolean copyDefaultFromResources() {
        try (InputStream in = getClass().getResourceAsStream("/config.json")) {

            if (in == null) {
                logger.warning("[Config] No default config.json found.");
                return false;
            }

            Files.copy(in, file.toPath());
            return true;

        } catch (Exception e) {
            logger.error("[Config] Failed to copy default config.json", e);
            return false;
        }
    }

    private void load() {
        try (FileReader reader = new FileReader(file)) {
            JsonElement element = JsonParser.parseReader(reader);
            this.data = element != null && element.isJsonObject()
                    ? element.getAsJsonObject()
                    : new JsonObject();
        } catch (Exception e) {
            logger.error("Failed to load config.json", e);
        }
    }

    @Override
    public Set<String> getKeys() {
        return Collections.unmodifiableSet(data.keySet());
    }

    @Override
    public boolean has(String key) {
        return getValue(key) != null;
    }

    @Override
    public void set(String path, Object value) {

        JsonElement element;

        if (value instanceof String s) {
            element = new JsonPrimitive(s);
        } else if (value instanceof Number n) {
            element = new JsonPrimitive(n);
        } else if (value instanceof Boolean b) {
            element = new JsonPrimitive(b);
        } else if (value instanceof Character c) {
            element = new JsonPrimitive(c);
        } else if (value == null) {
            element = JsonNull.INSTANCE;
        } else {
            logger.warning("Unsupported type: " + value.getClass());
            return;
        }

        setValue(path, element);
    }

    @Override
    public String getString(String path) {
        JsonElement el = getValue(path);
        return el != null && el.isJsonPrimitive() ? el.getAsString() : null;
    }

    @Override
    public String getString(String path, String def) {
        String val = getString(path);
        return val != null ? val : def;
    }

    @Override
    public boolean getBoolean(String path, boolean def) {
        JsonElement el = getValue(path);
        return el != null && el.isJsonPrimitive() ? el.getAsBoolean() : def;
    }

    @Override
    public int getInt(String path, int def) {
        JsonElement el = getValue(path);
        return el != null && el.isJsonPrimitive() ? el.getAsInt() : def;
    }

    @Override
    public double getDouble(String path, double def) {
        JsonElement el = getValue(path);
        return el != null && el.isJsonPrimitive() ? el.getAsDouble() : def;
    }

    @Override
    public void save() {
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            logger.error("Failed to save config.json", e);
        }
    }

    @Override
    public void reload() {
        if (!file.exists()) {
            logger.warning("[Config] Reload failed: file does not exist.");
            return;
        }

        try (FileReader reader = new FileReader(file)) {

            JsonElement element = JsonParser.parseReader(reader);

            if (element == null || !element.isJsonObject()) {
                logger.warning("[Config] Reload failed: invalid JSON, keeping current state.");
                return;
            }

            this.data = element.getAsJsonObject();

        } catch (Exception e) {
            logger.error("[Config] Failed to reload config.json", e);
        }
    }

    @Override
    public File getFile() {
        return file;
    }

    // ------------------------
    // Path traversal
    // ------------------------

    private JsonElement getValue(String path) {
        String[] parts = path.split("\\.");
        JsonElement current = data;

        for (String part : parts) {
            if (!current.isJsonObject()) return null;

            JsonObject obj = current.getAsJsonObject();
            current = obj.get(part);

            if (current == null) return null;
        }

        return current;
    }

    private void setValue(String path, JsonElement value) {
        String[] parts = path.split("\\.");
        JsonObject current = data;

        for (int i = 0; i < parts.length - 1; i++) {
            String key = parts[i];

            if (!current.has(key) || !current.get(key).isJsonObject()) {
                JsonObject newObj = new JsonObject();
                current.add(key, newObj);
                current = newObj;
            } else {
                current = current.getAsJsonObject(key);
            }
        }

        current.add(parts[parts.length - 1], value);
        save();
    }
}