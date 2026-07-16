package io.github.theodoremeyer.simplevoicegeyser.core.server.connection.compatibility;

import io.github.theodoremeyer.simplevoicegeyser.core.api.data.SvgConfig;

import java.util.List;

public record ClientTypePolicy(boolean blacklist, List<String> list) {

    public static ClientTypePolicy fromConfig(SvgConfig config) {
        return new ClientTypePolicy(
                Boolean.TRUE.equals(config.CLIENT_ALLOWED_TYPES_BLACKLIST.get()),
                config.CLIENT_ALLOWED_TYPES_LIST.get()
        );
    }

    public boolean allows(String type) {
        boolean listed = list != null && list.stream()
                .filter(value -> value != null)
                .map(String::trim)
                .anyMatch(value -> value.equalsIgnoreCase(type));

        return blacklist ? !listed : listed;
    }
}
