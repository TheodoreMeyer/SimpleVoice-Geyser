package io.github.theodoremeyer.simplevoicegeyser.fabric.impl.data;

import com.google.gson.*;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.SvgFile;
import io.github.theodoremeyer.simplevoicegeyser.fabric.impl.FabricLogger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
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
    public MigrationReport migrateFromBundledDefaults(String trigger) {
        JsonObject defaults = loadBundledDefaults();
        if (defaults == null) {
            return new MigrationReport("json", "", 0, false);
        }

        JsonObject existing = data != null ? data : new JsonObject();
        AddedCounter counter = new AddedCounter();
        JsonObject merged = mergeObject(defaults, existing, counter);

        String backupPath = backupCurrentConfig();
        this.data = merged;
        save();
        return new MigrationReport("json", backupPath, counter.count, true);
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

    private JsonObject loadBundledDefaults() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.json")) {
            if (in == null) {
                logger.info("[Config] No bundled config.json found. Using SvgConfig code defaults for migration.");
                return buildCodeDefaults();
            }
            try (InputStreamReader reader = new InputStreamReader(in)) {
                JsonElement parsed = JsonParser.parseReader(reader);
                if (parsed == null || !parsed.isJsonObject()) {
                    logger.warning("[Config] Bundled config.json is invalid. Using SvgConfig code defaults.");
                    return buildCodeDefaults();
                }
                return parsed.getAsJsonObject();
            }
        } catch (Exception e) {
            logger.error("[Config] Failed loading bundled config.json. Using SvgConfig code defaults.", e);
            return buildCodeDefaults();
        }
    }

    private JsonObject buildCodeDefaults() {
        JsonObject root = new JsonObject();
        root.addProperty("config-info",
                "This file is used to configure Simple Voice Geyser. For more information, see the wiki: "
                        + "https://theodoremeyer.github.io/projects/simplevoicegeyser/");

        JsonObject client = new JsonObject();
        client.addProperty("vctimeout", 30);
        client.addProperty("idletimeout", 2);
        client.addProperty("requireBedrock", false);
        client.addProperty("useEmoteForSVG", true);
        root.add("client", client);

        JsonObject server = new JsonObject();
        JsonObject group = new JsonObject();
        JsonObject defaults = new JsonObject();
        defaults.addProperty("enabled", true);
        defaults.addProperty("password", "1a2b");
        defaults.addProperty("force-on-web-join", false);
        group.add("default", defaults);
        server.add("group", group);
        server.addProperty("port", 8080);
        server.addProperty("bind-address", "0.0.0.0");
        server.addProperty("context-path", "/");
        JsonObject audio = new JsonObject();
        audio.addProperty("transport-mode", "auto");
        audio.addProperty("allow-legacy-fallback", true);
        server.add("audio", audio);
        root.add("server", server);

        root.addProperty("debug", false);
        JsonObject updateChecker = new JsonObject();
        updateChecker.addProperty("enable", true);
        root.add("updatechecker", updateChecker);
        root.addProperty("config-version", "0.1.1-dev-migration1");
        return root;
    }

    private String backupCurrentConfig() {
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

    private JsonObject mergeObject(JsonObject defaults, JsonObject existing, AddedCounter counter) {
        JsonObject merged = new JsonObject();

        for (String key : defaults.keySet()) {
            JsonElement defaultValue = defaults.get(key);
            if (existing.has(key)) {
                JsonElement existingValue = existing.get(key);
                if (defaultValue != null && defaultValue.isJsonObject()
                        && existingValue != null && existingValue.isJsonObject()) {
                    merged.add(key, mergeObject(defaultValue.getAsJsonObject(), existingValue.getAsJsonObject(), counter));
                } else {
                    merged.add(key, existingValue.deepCopy());
                }
            } else {
                merged.add(key, defaultValue.deepCopy());
                counter.count += countLeafNodes(defaultValue);
            }
        }

        for (String key : existing.keySet()) {
            if (!merged.has(key)) {
                merged.add(key, existing.get(key).deepCopy());
            }
        }
        return merged;
    }

    private int countLeafNodes(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return 1;
        }
        if (element.isJsonPrimitive() || element.isJsonArray()) {
            return 1;
        }
        if (!element.isJsonObject()) {
            return 1;
        }
        int total = 0;
        JsonObject obj = element.getAsJsonObject();
        for (String key : obj.keySet()) {
            total += countLeafNodes(obj.get(key));
        }
        return total;
    }

    private static final class AddedCounter {
        private int count = 0;
    }
}
