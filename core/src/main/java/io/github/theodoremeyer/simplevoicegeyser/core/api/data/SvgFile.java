package io.github.theodoremeyer.simplevoicegeyser.core.api.data;

import java.io.File;
import java.util.Set;

/**
 * The DataStore fow holding passwords
 * Can be JSON, yml, sqlite, etc
 */
public abstract class SvgFile {

    /**
     * Result object for config migration routines.
     * @param mode the migration mode used (e.g. "backup_and_replace", "merge", "none")
     * @param backupPath if applicable, the path to the backup file created during migration
     * @param addedKeys the number of keys added to the config as a result of migration
     * @param migrated whether any migration actually occurred
     */
    public record MigrationReport(String mode, String backupPath, int addedKeys, boolean migrated) {}

    /**
     * Create something that represents a file
     */
    public SvgFile() {}

    /**
     * All keys in datastore
     * @return keys/values
     */
    public abstract Set<String> getKeys();

    /**
     * Does the file contain the key/value for key
     * @param key the key to check
     * @return whether the key exists
     */
    public abstract boolean has(String key);

    /**
     * Set a value
     * @param path path to set
     * @param value the value to set
     */
    public abstract void set(String path, Object value);

    /**
     * Get a String from store
     * @param path the path of value
     * @return String value
     */
    public abstract String getString(String path);

    /**
     * Get a String from store
     * @param path the path of value
     * @param def the default value
     * @return String value
     */
    public abstract String getString(String path, String def);

    /**
     * get a boolean in store
     * @param path path of value
     * @param def default
     * @return the value
     */
    public abstract boolean getBoolean(String path, boolean def);

    /**
     * get an int in store
     * @param path path of value
     * @param def default
     * @return the value
     */
    public abstract int getInt(String path, int def);

    /**
     * Save datastore
     */
    public abstract void save();

    /**
     * Reload Config
     */
    public abstract void reload();

    /**
     * The file the DataStore represents
     * @return File
     */
    public abstract File getFile();

    /**
     * Get a double
     * @param path the path
     * @param def the default
     * @return the value
     */
    public abstract double getDouble(String path, double def);

    /**
     * Regenerates defaults from bundled resources and merges user values.
     * Implementations may return a no-op report when migration is unsupported.
     *
     * @param trigger why the migration ran (for logging context)
     * @return migration report
     */
    public MigrationReport migrateFromBundledDefaults(String trigger) {
        return new MigrationReport("none", "", 0, false);
    }
}
