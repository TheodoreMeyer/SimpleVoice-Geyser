package io.github.theodoremeyer.simplevoicegeyser.core.commands;

import java.util.HashMap;
import java.util.Map;

public class CommandFlagParser {

    /**
     * Schema for cgroup flags
     * true = requires value
     * false = boolean flag
     */
    public static final Map<String, Boolean> CGROUP_FLAGS = Map.of(
            "-t", true,
            "-p", true,
            "-ps", false
    );

    /**
     * Parse flags from args array
     * @param schema flag definitions
     * @param args full args array
     * @param start index to start parsing (skip sub + name)
     */
    public static Map<String, Object> parse(Map<String, Boolean> schema,
                                            String[] args,
                                            int start) {

        Map<String, Object> out = new HashMap<>();

        // defaults (centralized now)
        out.put("type", "open");
        out.put("password", "");
        out.put("persistent", false);

        for (int i = start; i < args.length; i++) {
            String token = args[i].toLowerCase();

            if (!schema.containsKey(token)) {
                throw new IllegalArgumentException("Unknown flag: " + token);
            }

            boolean needsValue = schema.get(token);

            if (needsValue) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Flag " + token + " requires a value");
                }

                out.put(mapKey(token), args[++i]);
            } else {
                out.put(mapKey(token), true);
            }
        }

        return out;
    }

    /**
     * Maps flags → internal keys
     */
    private static String mapKey(String flag) {
        return switch (flag) {
            case "-t" -> "type";
            case "-p" -> "password";
            case "-ps" -> "persistent";
            default -> flag;
        };
    }
}