package io.github.theodoremeyer.simplevoicegeyser.core.api.data;

import java.io.File;
import java.util.Set;

/**
 * The DataStore fow holding passwords
 * Can be JSON, yml, sqlite, etc
 */
public abstract class SvgFile {

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
}
