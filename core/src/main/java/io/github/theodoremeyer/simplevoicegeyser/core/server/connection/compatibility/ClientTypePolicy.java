package io.github.theodoremeyer.simplevoicegeyser.core.server.connection.compatibility;

import io.github.theodoremeyer.simplevoicegeyser.core.api.data.SvgConfig;

import java.util.List;

/**
 * Policy of allowed client types
 * @param blacklist whether it's a blacklist or whitelist
 * @param list the contained types
 */
public record ClientTypePolicy(boolean blacklist, List<String> list) {

    /**
     * Create a ClientTypePolicy from the Config
     * @param config the config to create from
     * @return the Policy
     */
    public static ClientTypePolicy fromConfig(SvgConfig config) {
        return new ClientTypePolicy(
                Boolean.TRUE.equals(config.CLIENT_ALLOWED_TYPES_BLACKLIST.get()),
                config.CLIENT_ALLOWED_TYPES_LIST.get()
        );
    }

    /**
     * Check to see if a Type is allowed
     * @param type client type
     * @return if it is rejected or allowed
     */
    public boolean allows(String type) {
        boolean listed = list != null && list.stream()
                .filter(value -> value != null)
                .map(String::trim)
                .anyMatch(value -> value.equalsIgnoreCase(type));

        return blacklist ? !listed : listed;
    }
}
