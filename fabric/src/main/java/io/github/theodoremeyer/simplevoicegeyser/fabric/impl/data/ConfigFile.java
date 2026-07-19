package io.github.theodoremeyer.simplevoicegeyser.fabric.impl.data;

import com.google.gson.*;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.SvgFile;
import io.github.theodoremeyer.simplevoicegeyser.fabric.impl.FabricLogger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class ConfigFile extends SvgFile {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter BACKUP_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final File file;
    private JsonObject data;

    private final FabricLogger logger;

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

        switch (value) {
            case String s -> element = new JsonPrimitive(s);
            case Number n -> element = new JsonPrimitive(n);
            case Boolean b -> element = new JsonPrimitive(b);
            case Character c -> element = new JsonPrimitive(c);
            case Iterable<?> iterable -> {
                JsonArray array = new JsonArray();
                iterable.forEach(item -> array.add(String.valueOf(item)));
                element = array;
            }
            case null -> element = JsonNull.INSTANCE;
            default -> {
                logger.warning("Unsupported type: " + value.getClass());
                return;
            }
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
    public List<String> getStringList(String path, List<String> def) {
        JsonElement el = getValue(path);
        if (el == null || !el.isJsonArray()) {
            return def;
        }

        List<String> values = new ArrayList<>();
        for (JsonElement value : el.getAsJsonArray()) {
            if (value != null && value.isJsonPrimitive()) {
                values.add(value.getAsString());
            }
        }
        return values;
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

    @Override
    public String backup() {
        if (!file.exists()) {
            return "";
        }
        String ts = LocalDateTime.now().format(BACKUP_TS);
        File backup = new File(file.getParentFile(), "config-" + ts + ".json.bak");
        try {
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return backup.getAbsolutePath();
        } catch (IOException e) {
            logger.error("[Config] Failed backing up config.json", e);
            return "";
        }
    }
}
