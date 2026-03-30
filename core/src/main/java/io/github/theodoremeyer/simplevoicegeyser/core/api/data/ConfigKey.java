package io.github.theodoremeyer.simplevoicegeyser.core.api.data;

/**
 * Represents a config key with a path and default value
 * @param path path of key
 * @param def key default
 * @param <T> the type of key (like Boolean or String)
 */
public record ConfigKey<T>(String path, T def) {

    /**
     * Get the value of this key from the config file, or the default if not set
     * @return the key value
     */
    @SuppressWarnings("unchecked")
    public T get() {
        var file = SvgConfig.getFile();

        Object value = switch (def) {
            case String s -> file.getString(path, s);
            case Integer i -> file.getInt(path, i);
            case Boolean b -> file.getBoolean(path, b);
            case Double d -> file.getDouble(path, d);
            default -> throw new IllegalStateException("Unsupported type: " + def.getClass());
        };

        return (T) value;
    }

    /**
     * Set The value of this key in the config file
     * @param value value to set to
     * @throws IllegalArgumentException if value is not the same type as default
     * This does not save the config file, you must call SvgConfig.getFile().save() to save changes
     * Don't use very often
     */
    public void set(T value) {
        var file = SvgConfig.getFile();
        file.set(path, value);
    }
}