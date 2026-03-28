package io.github.theodoremeyer.simplevoicegeyser.core.commands;

import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.Sender;

import java.util.HashMap;
import java.util.Map;

/**
 * Command Arg wrapper for parsing and passing around command arguments in a more flexible way.
 */
public class CommandArgs {
    private final Map<String, Object> values = new HashMap<>();
    private final String sub;
    private final Sender sender;

    /**
     * Create a new CommandArgs instance with the given subcommand and sender.
     * @param sub The subcommand is the first argument of the command, and is used to determine which SubCommand to execute.
     * @param sender the executor
     */
    public CommandArgs(String sub, Sender sender) {
        this.sub = sub;
        this.sender = sender;
    }

    /**
     * Get the sub command
     * @return sub
     */
    public String sub() {
        return sub;
    }

    /**
     * get Executor
     * @return sender
     */
    public Sender getSender() {
        return sender;
    }

    /**
     * Add multiple args at once
     * @param args the map to add
     */
    public void putAll(Map<String, Object> args) {
        values.putAll(args);
    }

    /**
     * Put a single arg there
     * @param key the arg's key
     * @param value arg's value
     * @param <T> the Type
     */
    public <T> void put(String key, T value) {
        values.put(key, value);
    }

    /**
     * Get an arg
     * @param key arg's key
     * @return arg's value
     * @param <T> Arg's type
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) values.get(key);
    }

    /**
     * does this instance have an arg with this key?
     * @param key key to check
     * @return if the arg exists
     */
    public boolean has(String key) {
        return values.containsKey(key);
    }
}