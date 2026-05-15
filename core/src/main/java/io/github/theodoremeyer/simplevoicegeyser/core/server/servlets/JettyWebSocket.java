package io.github.theodoremeyer.simplevoicegeyser.core.server.servlets;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgColor;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.audio.SvgAudioSender;
import io.github.theodoremeyer.simplevoicegeyser.core.data.PlayerVcPswd;
import io.github.theodoremeyer.simplevoicegeyser.core.geyser.GeyserHook;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.json.JSONObject;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Websocket session Class.
 */
@WebSocket
public final class JettyWebSocket {
    /**
     * Standard code for fatal errors
     */
    private static final int FATAL_CLOSE_CODE = 4003;

    /**
     * Automatic reason, if it's not defined.
     */
    private static final String FATAL_CLOSE_REASON = "fatal";

    /**
     * AuthLimiter to limit login tries per user
     * Max 5 failed attempts per minute, with a lockout duration of 5 minutes after reaching the limit.
     * This helps protect against brute-force attacks while allowing legitimate users to retry after a short period.
     * If this is triggered, easy reset of /svg pswd
     */
    private static final AuthRateLimiter AUTH_RATE_LIMITER =
            new AuthRateLimiter(5, Duration.ofMinutes(1), Duration.ofMinutes(5));

    /**
     * The Session that is the audio client.
     */
    private Session session;

    /**
     * UUID associated with this websocket/user
     */
    private UUID uuid;
    /**
     * Player that this websocket does audio for
     */
    private SvgPlayer player;
    /**
     * Whether the user has logged in
     */
    private boolean authenticated = false;
    /**
     * AudioSender associated with this.
     */
    private SvgAudioSender audioSender;

    /**
     * Create a websocket connection with a client
     */
    public JettyWebSocket() {}

    /**
     * When the client connects.
     * @param session the websocket session associated with this class.
     */
    @OnWebSocketConnect
    public void onConnect(Session session) {
        this.session = session;
        session.setIdleTimeout(Duration.ofMinutes(SvgCore.getConfig().IDLE_TIMEOUT.get()));
        SvgCore.getLogger().info("[Websocket] WebSocket connected: " + session.getRemoteAddress());
    }

    /**
     * Handles all NON vc messages sent by client.
     * @param message message sent by client.
     */
    @OnWebSocketMessage
    public void onMessage(String message) {

        //ignore empty messages
        if (message == null || message.trim().isEmpty()) { //make sure the message is not empty
            return;
        }

        message = message.trim();

        //reject non JSON messages early
        if (!message.startsWith("{")) {
            sendMessage("error", "Invalid input. Expected a JSON object.", false);
            return;
        }

        try {
            JSONObject json = new JSONObject(message);
            String type = json.getString("type");

            switch (type) {
                case "join": { //handle the join form
                    join(json);
                    return;
                }

                case "chat": { //handle chat inputs.
                    chat(json);
                    return;
                }

                //handle unknown types
                case null, default:
                    sendMessage("error", "unknown type: " + type, false);
            }

        } catch (Exception e) {
            SvgCore.getLogger().severe("[VCBridge] code: 1, Exception: " + e.getMessage());
            SvgCore.debug("VCBridge", "error reading client data", e);
        }

    }

    /**
     * this copy is to handle audio data from the client.
     * I Don't really know too much about how to get pcmData from sent byte, all I know is that this is how Simple Voice Chat, and research wants me to do it
     * @param buffer the byte to process
     * @param offset the offset of the byte
     * @param length the int.
     */
    @OnWebSocketMessage
    public void onMessage(byte[] buffer, int offset, int length) {
        if (!authenticated || uuid == null) return; //make sure they signed in

        if (audioSender != null) { //make sure the sender is not null
            audioSender.sendOpus(Arrays.copyOfRange(buffer, offset, offset + length));
        }
    }

    /**
     * What to do when the websocket closes
     * @param statusCode code of how it exited
     * @param reason why the websocket closed
     */
    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
        if (uuid != null) { //make sure the uuid for the session is not null, needed to close senders/listeners
            String username = SvgCore.getPasswordManager().getUsername(uuid);
            String displayName = (username != null) ? username : uuid.toString();
            SvgCore.getLogger().info("[WebSocket] WebSocket for " + displayName + " closed: " + statusCode + " - " + reason);

            SvgCore.getBridge().unregisterAudioSender(uuid);
            SvgCore.getBridge().unregisterAudioListener(uuid);
        } else {
            SvgCore.getLogger().warning("[WebSocket] Disconnected: unknown client for " + reason + ".");
        }
        SvgCore.getWsManager().removeClient(uuid, session);
    }

    /**
     * What to do on a websocket error
     * @param error the error thrown
     */
    @OnWebSocketError
    public void onError(Throwable error) {
        SvgCore.debug("WebSocket", "websocket error", error);
        SvgCore.getLogger().info("Error: " + error.getMessage());
    }

    private void join(@NonNull JSONObject json) {
        PlayerVcPswd playerVcPswd = SvgCore.getPasswordManager();
        String username = json.optString("username", "").trim();
        String password = json.optString("password", "");
        String authKey = username.toLowerCase(Locale.ROOT);

        if (username.isEmpty()) {
            closeOnError("Username required.", false);
            return;
        }

        if (!AUTH_RATE_LIMITER.allow(authKey)) {
            closeOnError("Too many failed login attempts. Reset your password in-game with /svg pswd [password].",
                    false
            );
            return;
        }

        UUID storedUuid = playerVcPswd.getUUID(username);

        // Generic auth failure message
        if (storedUuid == null ||
                !playerVcPswd.isPasswordSet(username) ||
                !playerVcPswd.validatePassword(username, password)) {

            AUTH_RATE_LIMITER.recordFailure(authKey);

            closeOnError("Access Denied: Invalid username or password.", false);
            return;
        }

        Boolean bedrock = GeyserHook.isBedrock(storedUuid);
        boolean bedrockRequired = SvgCore.getConfig().REQUIRE_BEDROCK.get();

        if (bedrock == null) {
            if (bedrockRequired) {
                SvgCore.getLogger().warning(
                        "Unable to enforce: client.requireBedrock. Please install floodgate or geyser"
                );
            }
        } else if (!bedrock && bedrockRequired) {
            AUTH_RATE_LIMITER.recordFailure(authKey);

            closeOnError("Access Denied: You must be a Bedrock player to join!", false);
            return;
        }

        // Move uuid assignment until AFTER validation succeeds
        this.uuid = storedUuid;

        if (!SvgCore.getWsManager().addClient(uuid, this.session)) {
            AUTH_RATE_LIMITER.recordFailure(authKey);

            closeOnError("Access Denied: Failed to Join.", false);
            return;
        }

        this.player = SvgCore.getPlayerManager().getPlayer(uuid);

        if (player == null) {
            closeOnError("Timeout: You didn’t join the server in time.", false);
            return;
        }

        if (!player.hasPermission("svg.vc.join")) {
            closeOnError("Access Denied. You may have been banned from vc.", true);
            return;
        }

        VoicechatConnection connection =
                SvgCore.getBridge().getVcServerApi().getConnectionOf(uuid);

        if (connection == null || connection.isInstalled()) {
            closeOnError("Access Denied: Can't Join server with mod installed or Connection is Null", false);
            return;
        }

        if (SvgCore.getConfig().DEFAULT_GROUP_ENABLED.get()) {
            String gPswd = SvgCore.getConfig().DEFAULT_GROUP_PASSWORD.get();

            SvgCore.getGroupManager().createGroup(
                    player, "Svg", gPswd, Group.Type.OPEN, false, true
            );
        }

        SvgCore.getBridge().registerAudioListener(uuid, session);
        this.audioSender = SvgCore.getBridge().registerAudioSender(uuid);

        authenticated = true;
        AUTH_RATE_LIMITER.reset(authKey);

        sendMessage("status", "Connected as " + username + ".", false);

        SvgCore.getLogger().info("[WebSocket] " + username + " joined with UUID: " + uuid);
    }

    private void chat(JSONObject json) {
        String chatMessage = json.optString("message", "").trim();
        if (!authenticated) { //if they are signed in
            sendMessage("error", "Access Denied: You must be authenticated.", false);
            return;
        }

        if (!chatMessage.isEmpty()) {
            String savedName = SvgCore.getPasswordManager().getUsername(uuid);
            String displayName = (player != null) ? player.getName() :
                    savedName != null ? savedName : uuid.toString();

            if (player != null) {
                sendMessage("chat", "You: " + chatMessage, false);
                player.chat("[Web Chat] " + displayName + ": " + SvgColor.BLUE + chatMessage);
            } else {
                sendMessage("error", "You are Not in Game. Sent message", false);
                for (SvgPlayer p : SvgCore.getPlayerManager().getAllPlayers()) {
                    p.sendMessage("[Web Chat] " + displayName + ": " + SvgColor.BLUE + chatMessage);
                }
            }
        }
    }

    private void sendMessage(String type, String message, boolean log) {
        sendMessage(type, message, log, false);
    }

    private void sendMessage(String type, String message, boolean log, boolean fatal) {
        JSONObject json = new JSONObject();
        json.put("type", type);
        json.put("message", message);
        json.put("fatal", fatal);
        try {
            session.getRemote().sendString(json.toString());
        } catch (IOException e) {
            SvgCore.getLogger().severe(e.toString());
        }


        if (player != null) {
            player.sendMessage(SvgCore.getPrefix() + SvgColor.translateAltColorCodes('&', message));
        }
        if (log) {
            SvgCore.getLogger().info("[WebSocket] " + type + ": " + uuid + ": " + message);
        }
    }

    private void closeOnError(String message, boolean log) {
        sendMessage("error", message, log, true);
        if (session != null && session.isOpen()) {
            session.close(FATAL_CLOSE_CODE, FATAL_CLOSE_REASON);
        }
    }

    /**
     * Clear the user from the tracked failure list
     * @param username username to clear
     */
    public static void clearAuthFailures(String username) {
        AUTH_RATE_LIMITER.reset(username);
    }

    private static final class AuthRateLimiter {

        private final int maxFailures;
        private final long windowMillis;
        private final long lockMillis;

        private final ConcurrentHashMap<String, Entry> entries =
                new ConcurrentHashMap<>();

        private AuthRateLimiter(
                int maxFailures,
                Duration window,
                Duration lockDuration
        ) {
            this.maxFailures = maxFailures;
            this.windowMillis = window.toMillis();
            this.lockMillis = lockDuration.toMillis();
        }

        private boolean allow(String username) {
            long now = System.currentTimeMillis();

            cleanupStaleEntries(now);

            Entry entry = entries.computeIfAbsent(
                    username,
                    unused -> new Entry()
            );

            synchronized (entry) {

                entry.lastSeen = now;

                // Lock still active
                if (now < entry.lockUntil) {
                    return false;
                }

                // Reset rolling window
                if (now - entry.windowStart > windowMillis) {
                    entry.windowStart = now;
                    entry.failures = 0;
                    entry.lockUntil = 0;
                }

                return true;
            }
        }

        private void recordFailure(String username) {
            long now = System.currentTimeMillis();

            cleanupStaleEntries(now);

            Entry entry = entries.computeIfAbsent(
                    username,
                    unused -> new Entry()
            );

            synchronized (entry) {

                entry.lastSeen = now;

                // Reset expired window
                if (now - entry.windowStart > windowMillis) {
                    entry.windowStart = now;
                    entry.failures = 0;
                    entry.lockUntil = 0;
                }

                entry.failures++;

                if (entry.failures >= maxFailures) {

                    entry.lockUntil = now + lockMillis;

                    entry.failures = 0;
                    entry.windowStart = now;

                    SvgCore.getLogger().warning(
                            "[WebSocket] Account temporarily locked due to repeated failed logins: "
                                    + username
                    );
                }
            }
        }

        private void reset(String username) { entries.remove(username.toLowerCase(Locale.ROOT)); }

        private void cleanupStaleEntries(long now) {
            long maxAge = windowMillis + lockMillis;

            entries.entrySet().removeIf(entry ->
                    now - entry.getValue().lastSeen > maxAge &&
                            now >= entry.getValue().lockUntil
            );
        }

        private static final class Entry {
            private long windowStart = System.currentTimeMillis();
            private int failures = 0;
            private long lockUntil = 0;
            private long lastSeen = System.currentTimeMillis();
        }
    }
}
