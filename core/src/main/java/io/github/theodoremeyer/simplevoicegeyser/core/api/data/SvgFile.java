package io.github.theodoremeyer.simplevoicegeyser.core.api.data;

import java.io.File;
import java.util.Set;

/**
 * The DataStore fow holding passwords
 * Can be JSON, yml, sqlite, etc
 */
public interface SvgFile {

    /**
     * All keys in datastore
     * @return keys/values
     */
    Set<String> getKeys();

    /**
     * Set a value
     * @param path path to set
     * @param value the value to set
     */
    void set(String path, String value);

    /**
     * Get a String from store
     * @param path the path of value
     * @return String value
     */
    String getString(String path);

    /**
     * Get a String from store
     * @param path the path of value
     * @param def the default value
     * @return String value
     */
    String getString(String path, String def);

    /**
     * get a boolean in store
     * @param path path of value
     * @param def default
     * @return the value
     */
    boolean getBoolean(String path, boolean def);

    /**
     * get an int in store
     * @param path path of value
     * @param def default
     * @return the value
     */
    int getInt(String path, int def);

    /**
     * Save datastore
     */
    void save();

    /**
     * The file the DataStore represents
     * @return File
     */
    File getFile();

    /**
     * Get a double
     * @param path the path
     * @param def the default
     * @return the value
     */
    double getDouble(String path, double def);
}
