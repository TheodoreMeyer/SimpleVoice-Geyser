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
 * When credentials are valid but the player is not online / SVC is not ready yet,
 * {@link AuthResponse#pending(UUID)} is returned so the join handler can wait.
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
     * @return auth response ({@link AuthResponse#success}, {@link AuthResponse#pending}, or failure)
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
                // Floodgate / linked accounts: password UUID can lag behind the online UUID.
                SvgPlayer byName = SvgCore.getPlayerManager().findOnlineByUsername(username);
                if (byName != null) {
                    UUID onlineUuid = byName.getUniqueId();
                    if (!onlineUuid.equals(uuid)) {
                        SvgCore.getLogger().warning(
                                "[Authenticator] Account UUID " + uuid
                                        + " does not match online player " + byName.getName()
                                        + " (" + onlineUuid + "). Using online UUID. "
                                        + "Re-run /svg pswd in-game to refresh the account."
                        );
                        uuid = onlineUuid;
                    }
                    player = byName;
                } else {
                    // Credentials OK — JoinPacket will wait up to client.vctimeout.
                    return AuthResponse.pending(uuid);
                }
            }

            AuthResponse permissionResult =
                    validatePermissions(player);

            if (!permissionResult.success()) {
                return permissionResult;
            }

            AuthResponse voiceChatResult =
                    validateVoiceChat(uuid);

            if (!voiceChatResult.success()) {
                // SVC not attached yet: wait instead of hard-failing immediately.
                if ("pending".equals(voiceChatResult.message())) {
                    return AuthResponse.pending(uuid);
                }
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

    /**
     * Marks a successful completed join (after pending wait) for rate-limit reset.
     *
     * @param username username
     */
    public void markJoinSuccess(String username) {
        if (username == null) {
            return;
        }
        authRateLimiter.reset(username.toLowerCase(Locale.ROOT));
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
     * Returns failure with message {@code "pending"} when the SVC connection is not ready yet.
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
            return AuthResponse.failure("pending");
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
