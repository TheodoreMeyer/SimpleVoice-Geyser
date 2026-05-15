package io.github.theodoremeyer.simplevoicegeyser.core.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgLogger;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents the password file, which stores player credentials (UUID as primary key).
 * This class handles loading and saving the password data to a JSON file, as well as providing methods to query and modify the stored credentials.
 */
final class PasswordFile {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Type TYPE =
            new TypeToken<Map<UUID, UserCredentials>>() {}.getType();

    private final File file;
    private final File tempFile;
    private final SvgLogger logger;

    // PRIMARY STORAGE: UUID → credentials
    private final Map<UUID, UserCredentials> data = new ConcurrentHashMap<>();

    // SECONDARY INDEX: username → UUID
    private final Map<String, UUID> usernameIndex = new ConcurrentHashMap<>();

    PasswordFile(File dataFolder, SvgLogger logger) {
        this.file = new File(dataFolder, "accounts.json");
        this.tempFile = new File(dataFolder, "accounts.json.tmp");
        this.logger = logger;

        if (!file.exists()) {
            try {
                file.createNewFile();
                save();
            } catch (IOException e) {
                logger.error("Failed to create accounts file", e);
            }
        }

        load();
    }

    // =========================
    // LOAD
    // =========================

    private void load() {
        try (Reader reader = new FileReader(file)) {

            Map<UUID, UserCredentials> loaded = GSON.fromJson(reader, TYPE);

            if (loaded == null) {
                logger.warning("Accounts file empty or corrupted. Starting fresh.");
                return;
            }

            data.clear();
            usernameIndex.clear();

            for (var entry : loaded.entrySet()) {
                UUID uuid = entry.getKey();
                UserCredentials cred = entry.getValue();

                data.put(uuid, cred);
                usernameIndex.put(cred.username.toLowerCase(Locale.ROOT), uuid);
            }

        } catch (Exception e) {
            logger.error("Failed to load accounts file", e);
        }
    }

    // =========================
    // SAVE (atomic)
    // =========================

    synchronized void save() {
        try (Writer writer = new FileWriter(tempFile)) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            logger.error("Failed writing temp accounts file", e);
            return;
        }

        try {
            Files.move(
                    tempFile.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (IOException e) {
            logger.error("Failed atomic replace of accounts file", e);
        }
    }

    // =========================
    // API
    // =========================

    boolean exists(UUID uuid) {
        return data.containsKey(uuid);
    }

    @Nullable
    String getPasswordHash(UUID uuid) {
        UserCredentials cred = data.get(uuid);
        return cred != null ? cred.passwordHash : null;
    }

    @Nullable
    String getUsername(UUID uuid) {
        UserCredentials cred = data.get(uuid);
        return cred != null ? cred.username : null;
    }

    @Nullable
    UUID getUUID(String username) {
        return usernameIndex.get(username.toLowerCase(Locale.ROOT));
    }

    void set(UUID uuid, String username, String bcryptHash) {

        String normalized = username.toLowerCase(Locale.ROOT);

        // remove old username mapping if overwriting
        UserCredentials existing = data.get(uuid);
        if (existing != null) {
            usernameIndex.remove(existing.username.toLowerCase(Locale.ROOT));
        }

        UserCredentials cred = new UserCredentials(username, bcryptHash);

        data.put(uuid, cred);
        usernameIndex.put(normalized, uuid);

        save();
    }

    void cleanup() {
        if (tempFile.exists() && tempFile.length() == 0) {
            tempFile.delete();
        }
    }
}