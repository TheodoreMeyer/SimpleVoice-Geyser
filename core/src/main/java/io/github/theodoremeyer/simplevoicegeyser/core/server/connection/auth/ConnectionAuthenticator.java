package io.github.theodoremeyer.simplevoicegeyser.core.server.connection.auth;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.data.PlayerVcPswd;
import io.github.theodoremeyer.simplevoicegeyser.core.geyser.GeyserHook;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

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
    private final AuthRateLimiter authRateLimiter;

    /**
     * Creates the authenticator.
     */
    public ConnectionAuthenticator() {
        this.authRateLimiter = new AuthRateLimiter(
                SvgCore.getConfig().MAX_AUTH_FAILURES.get(),
                Duration.ofMinutes(SvgCore.getConfig().AUTH_FAILURE_DURATION.get()),
                Duration.ofMinutes(SvgCore.getConfig().AUTH_LOCK_DURATION.get())
        );
    }

    /**
     * Attempts to authenticate a websocket client.
     * <p>
     * This method NEVER throws user-facing auth failures.
     * It returns structured responses instead.
     * <p>
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
        return authenticate(username, password, null);
    }

    /**
     * Attempts to authenticate a websocket client.
     *
     * @param username username
     * @param password password
     * @param proxyToken optional proxy-auth token
     * @return auth response
     */
    public AuthResponse authenticate(
            String username,
            String password,
            String proxyToken
    ) {

        try {

            username = normalizeUsername(username);

            if (username.isEmpty()) {

                return AuthResponse.failure(
                        "Username required."
                );
            }

            String authKey = username.toLowerCase(Locale.ROOT);

            if (proxyToken != null && !proxyToken.isBlank()) {
                AuthResponse proxyAuth = authenticateProxy(username, proxyToken);
                if (!proxyAuth.success()) {
                    return proxyAuth;
                }
                authRateLimiter.reset(authKey);
                return proxyAuth;
            }

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

            SvgCore.getLogger().error("Authenticator: Unexpected authentication failure", e);

            return AuthResponse.failure(
                    "Internal server error."
            );
        }
    }

    private AuthResponse authenticateProxy(String username, String proxyToken) {
        String secret = SvgCore.getConfig().PROXY_SHARED_SECRET.get();
        ProxyAuthToken.Claims claims = ProxyAuthToken.validate(proxyToken, secret);
        if (claims == null) {
            return AuthResponse.failure("Proxy authentication failed.");
        }

        if (!claims.username().equalsIgnoreCase(username)) {
            return AuthResponse.failure("Proxy authentication failed.");
        }

        SvgPlayer player = SvgCore.getPlayerManager().getPlayer(claims.uuid());
        if (player == null) {
            return AuthResponse.failure("Timeout: You didn’t join the server in time.");
        }

        AuthResponse permissionResult = validatePermissions(player);
        if (!permissionResult.success()) {
            return permissionResult;
        }

        AuthResponse voiceChatResult = validateVoiceChat(claims.uuid());
        if (!voiceChatResult.success()) {
            return voiceChatResult;
        }

        return AuthResponse.success(claims.uuid(), player);
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
                SvgCore.getConfig().REQUIRE_BEDROCK.get();

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
                    "Access Denied: " + "You must be a Bedrock player to join!"
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

        if (SvgCore.getBridge().getVcServerApi() == null) {
            SvgCore.getLogger().warning(
                    "[Authenticator] VoiceChatServerApi is null."
            );

            return AuthResponse.failure(
                    "Voice chat server unavailable."
            );
        }

        VoicechatConnection connection =
                SvgCore.getBridge().getVcServerApi().getConnectionOf(uuid);

        if (connection == null) {
            return AuthResponse.failure(
                    "Access Denied: " + "Voice chat connection unavailable."
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

        authRateLimiter.reset(username.toLowerCase(Locale.ROOT));
    }

    /**
     * Normalizes usernames safely.
     *
     * @param username raw username
     * @return normalized username
     */
    private String normalizeUsername(String username) {
        if (username == null) {
            return "";
        }

        return username.trim();
    }
}
