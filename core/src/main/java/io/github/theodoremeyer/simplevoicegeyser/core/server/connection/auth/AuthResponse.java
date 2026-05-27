package io.github.theodoremeyer.simplevoicegeyser.core.server.connection.auth;

import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;

import java.util.UUID;

/**
 * Result of an authentication attempt.
 * @param success whether auth succeeded
 * @param message failure message if !success
 * @param uuid player uuid if success
 * @param player player if success
 */
public record AuthResponse(boolean success, String message,
                           UUID uuid, SvgPlayer player
) {

    /**
     * Successful auth result.
     *
     * @param uuid player uuid
     * @param player player
     * @return auth response
     */
    public static AuthResponse success(UUID uuid, SvgPlayer player) {
        return new AuthResponse(
                true, null,
                uuid, player
        );
    }

    /**
     * Failed auth result.
     *
     * @param message failure message
     * @return auth response
     */
    public static AuthResponse failure(String message) {

        return new AuthResponse(
                false, message,
                null, null
        );
    }

    /**
     * Generic success helper.
     * <p>
     * Used for validation helper methods.
     *
     * @return auth response
     */
    public static AuthResponse ok() {

        return new AuthResponse(
                true, null,
                null, null
        );
    }
}
