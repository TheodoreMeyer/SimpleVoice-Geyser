package io.github.theodoremeyer.simplevoicegeyser.core.server.connection.auth;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.data.PlayerVcPswd;
import io.github.theodoremeyer.simplevoicegeyser.core.geyser.GeyserHook;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles websocket authentication and connection validation.
 * <p>
 * This class is responsible for:
 * - password validation
 * - bedrock enforcement
 * - permission validation
 * - voice chat compatibility checks
 * - auth rate limiting
 * <p>
 * This class DOES NOT create websocket connections.
 */
public final class ConnectionAuthenticator {

    /**
     * Authentication rate limiter.
     */
    private final AuthRateLimiter authRateLimiter =
            new AuthRateLimiter(
                    5,
                    Duration.ofMinutes(1),
                    Duration.ofMinutes(5)
            );

    /**
     * Creates the authenticator.
     */
    public ConnectionAuthenticator() {}

    /**
     * Attempts to authenticate a websocket client.
     *
     * This method NEVER throws user-facing auth failures.
     * It returns structured responses instead.
     *
     * Exceptions are reserved for unexpected server faults.
     *
     * @param username username
     * @param password password
     * @return auth response
     */
    public AuthResponse authenticate(
            String username,
            String password
    ) {

        try {

            username = normalizeUsername(username);

            if (username.isEmpty()) {

                return AuthResponse.failure(
                        "Username required."
                );
            }

            String authKey = username.toLowerCase(Locale.ROOT);

            if (!authRateLimiter.allow(authKey)) {

                return AuthResponse.failure(
                        "Too many failed login attempts. " +
                                "Reset your password in-game with /svg pswd [password]."
                );
            }

            PlayerVcPswd passwordManager = SvgCore.getPasswordManager();
            UUID uuid = passwordManager.getUUID(username);

            // Generic auth failure response
            if (uuid == null ||
                    !passwordManager.isPasswordSet(username) ||
                    !passwordManager.validatePassword(
                            username,
                            password
                    )) {

                authRateLimiter.recordFailure(authKey);

                return AuthResponse.failure(
                        "Access Denied: Invalid username or password."
                );
            }

            AuthResponse bedrockResult =
                    validateBedrock(uuid, authKey);

            if (!bedrockResult.success()) {
                return bedrockResult;
            }

            SvgPlayer player =
                    SvgCore.getPlayerManager()
                            .getPlayer(uuid);

            if (player == null) {

                return AuthResponse.failure(
                        "Timeout: You didn’t join the server in time."
                );
            }

            AuthResponse permissionResult =
                    validatePermissions(player);

            if (!permissionResult.success()) {
                return permissionResult;
            }

            AuthResponse voiceChatResult =
                    validateVoiceChat(uuid);

            if (!voiceChatResult.success()) {
                return voiceChatResult;
            }

            authRateLimiter.reset(authKey);

            return AuthResponse.success(
                    uuid,
                    player
            );

        } catch (Exception e) {

            SvgCore.getLogger().debug("Authenticator: Unexpected authentication failure", e);

            return AuthResponse.failure(
                    "Internal server error."
            );
        }
    }

    /**
     * Validates geyser/bedrock restrictions.
     *
     * @param uuid player uuid
     * @param authKey auth key
     * @return auth response
     */
    private AuthResponse validateBedrock(
            UUID uuid,
            String authKey
    ) {

        Boolean bedrock =
                GeyserHook.isBedrock(uuid);

        boolean requireBedrock =
                SvgCore.getConfig()
                        .REQUIRE_BEDROCK
                        .get();

        if (bedrock == null) {

            if (requireBedrock) {

                SvgCore.getLogger().warning(
                        "Unable to enforce: " +
                                "client.requireBedrock. " +
                                "Please install floodgate or geyser."
                );
            }

            return AuthResponse.ok();
        }

        if (!bedrock && requireBedrock) {

            authRateLimiter.recordFailure(authKey);

            return AuthResponse.failure(
                    "Access Denied: " +
                            "You must be a Bedrock player to join!"
            );
        }

        return AuthResponse.ok();
    }

    /**
     * Validates player permissions.
     *
     * @param player player
     * @return auth response
     */
    private AuthResponse validatePermissions(
            SvgPlayer player
    ) {

        if (!player.hasPermission("svg.vc.join")) {

            return AuthResponse.failure(
                    "Access Denied. " +
                            "You may have been banned from vc."
            );
        }

        return AuthResponse.ok();
    }

    /**
     * Validates Simple Voice Chat compatibility.
     *
     * @param uuid player uuid
     * @return auth response
     */
    private AuthResponse validateVoiceChat(
            UUID uuid
    ) {

        if (SvgCore.getBridge()
                .getVcServerApi() == null) {

            SvgCore.getLogger().warning(
                    "[Authenticator] VoiceChatServerApi is null."
            );

            return AuthResponse.failure(
                    "Voice chat server unavailable."
            );
        }

        VoicechatConnection connection =
                SvgCore.getBridge()
                        .getVcServerApi()
                        .getConnectionOf(uuid);

        if (connection == null) {

            return AuthResponse.failure(
                    "Access Denied: " +
                            "Voice chat connection unavailable."
            );
        }

        if (connection.isInstalled()) {

            return AuthResponse.failure(
                    "Access Denied: " + "Please remove the Simple Voice Chat mod."
            );
        }

        return AuthResponse.ok();
    }

    /**
     * Clears auth failures for a user.
     *
     * @param username username
     */
    public void clearFailures(String username) {

        if (username == null) {
            return;
        }

        authRateLimiter.reset(
                username.toLowerCase(Locale.ROOT)
        );
    }

    /**
     * Normalizes usernames safely.
     *
     * @param username raw username
     * @return normalized username
     */
    private String normalizeUsername(
            String username
    ) {

        if (username == null) {
            return "";
        }

        return username.trim();
    }

    /**
     * Result of an authentication attempt.
     */
    public record AuthResponse(
            boolean success,
            String message,
            UUID uuid,
            SvgPlayer player
    ) {

        /**
         * Successful auth result.
         *
         * @param uuid player uuid
         * @param player player
         * @return auth response
         */
        public static AuthResponse success(
                UUID uuid,
                SvgPlayer player
        ) {

            return new AuthResponse(
                    true,
                    null,
                    uuid,
                    player
            );
        }

        /**
         * Failed auth result.
         *
         * @param message failure message
         * @return auth response
         */
        public static AuthResponse failure(
                String message
        ) {

            return new AuthResponse(
                    false,
                    message,
                    null,
                    null
            );
        }

        /**
         * Generic success helper.
         *
         * Used for validation helper methods.
         *
         * @return auth response
         */
        public static AuthResponse ok() {

            return new AuthResponse(
                    true,
                    null,
                    null,
                    null
            );
        }
    }

    /**
     * Simple rolling auth limiter.
     */
    private static final class AuthRateLimiter {

        /**
         * Maximum failures before lock.
         */
        private final int maxFailures;

        /**
         * Rolling auth window.
         */
        private final long windowMillis;

        /**
         * Lock duration.
         */
        private final long lockMillis;

        /**
         * Auth entries.
         */
        private final ConcurrentHashMap<String, Entry>
                entries = new ConcurrentHashMap<>();

        /**
         * Creates the limiter.
         *
         * @param maxFailures max failures
         * @param window window duration
         * @param lockDuration lock duration
         */
        private AuthRateLimiter(
                int maxFailures,
                Duration window,
                Duration lockDuration
        ) {

            this.maxFailures = maxFailures;
            this.windowMillis = window.toMillis();
            this.lockMillis = lockDuration.toMillis();
        }

        /**
         * Gets whether login is allowed.
         *
         * @param username username
         * @return true if allowed
         */
        private boolean allow(String username) {

            long now = System.currentTimeMillis();

            cleanup(now);

            Entry entry =
                    entries.computeIfAbsent(
                            username,
                            ignored -> new Entry()
                    );

            synchronized (entry) {

                entry.lastSeen = now;

                // lock still active
                if (now < entry.lockUntil) {
                    return false;
                }

                // reset expired rolling window
                if (now - entry.windowStart >
                        windowMillis) {

                    entry.windowStart = now;
                    entry.failures = 0;
                    entry.lockUntil = 0;
                }

                return true;
            }
        }

        /**
         * Records failed auth attempt.
         *
         * @param username username
         */
        private void recordFailure(
                String username
        ) {

            long now = System.currentTimeMillis();

            cleanup(now);

            Entry entry =
                    entries.computeIfAbsent(
                            username,
                            ignored -> new Entry()
                    );

            synchronized (entry) {

                entry.lastSeen = now;

                // reset expired rolling window
                if (now - entry.windowStart >
                        windowMillis) {

                    entry.windowStart = now;
                    entry.failures = 0;
                    entry.lockUntil = 0;
                }

                entry.failures++;

                if (entry.failures >= maxFailures) {

                    entry.lockUntil =
                            now + lockMillis;

                    entry.failures = 0;
                    entry.windowStart = now;

                    SvgCore.getLogger().warning(
                            "[Authenticator] " +
                                    "Account temporarily locked: " +
                                    username
                    );
                }
            }
        }

        /**
         * Clears failures for a username.
         *
         * @param username username
         */
        private void reset(String username) {
            entries.remove(username);
        }

        /**
         * Removes stale entries.
         *
         * @param now current timestamp
         */
        private void cleanup(long now) {

            long maxAge =
                    windowMillis + lockMillis;

            entries.entrySet().removeIf(entry ->

                    now - entry.getValue().lastSeen >
                            maxAge &&

                            now >= entry.getValue().lockUntil
            );
        }

        /**
         * Rate limit entry.
         */
        private static final class Entry {

            /**
             * Window start timestamp.
             */
            private long windowStart =
                    System.currentTimeMillis();

            /**
             * Failure count.
             */
            private int failures = 0;

            /**
             * Lock expiration timestamp.
             */
            private long lockUntil = 0;

            /**
             * Last seen timestamp.
             */
            private long lastSeen =
                    System.currentTimeMillis();
        }
    }
}