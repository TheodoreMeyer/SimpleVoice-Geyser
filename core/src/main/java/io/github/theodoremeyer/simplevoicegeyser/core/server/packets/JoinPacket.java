package io.github.theodoremeyer.simplevoicegeyser.core.server.packets;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.ConnectionStates;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.HandshakeState;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.HandshakeTracer;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.SvgConnection;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.auth.AuthException;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.auth.AuthResponse;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.compatibility.ClientCompatibilityResult;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.compatibility.ClientCompatibilityValidator;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.compatibility.ClientIdentity;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.compatibility.ClientTypePolicy;
import io.github.theodoremeyer.simplevoicegeyser.core.server.servlets.JettyWebSocket;
import org.eclipse.jetty.websocket.api.Session;
import org.json.JSONObject;

/**
 * Handles the Join Packet from the Client
 */
public final class JoinPacket implements Packet {

    /**
     * No Arg Constructor
     */
    public JoinPacket() {}

    @Override
    public String getType() {
        return "join";
    }

    @Override
    public void handle(JettyWebSocket socket, JSONObject json) {
        HandshakeTracer handshake = socket.getHandshake();
        long attempt = socket.addJoinAttempt();

        if (handshake != null) {
            handshake.transition(
                    HandshakeState.JOIN_RECEIVED,
                    "join-packet#" + attempt,
                    null,
                    socket.isSocketOpen(),
                    false
            );
        }

        SvgCore.getLogger().debug(
                "WebSocket: Join attempt #" + attempt
                        + " corr=" + socket.correlationId()
                        + " thread=" + Thread.currentThread().getName()
        );

        ClientCompatibilityResult compatibility = ClientCompatibilityValidator.validate(
                json,
                SvgCore.VERSION,
                SvgCore.BUILD_ID,
                ClientTypePolicy.fromConfig(SvgCore.getConfig())
        );

        if (!compatibility.accepted()) {
            SvgCore.getLogger().debug(
                    "WebSocket: Join rejected by compatibility gate reason="
                            + compatibility.closeReason()
                            + " corr=" + socket.correlationId()
            );
            if (handshake != null) {
                handshake.fail("compatibility", null, socket.isSocketOpen(), null);
            }
            socket.sendRaw(ConnectionStates.MessageType.ERROR, compatibility.message(), false);
            closeCompatibilityFailure(socket, compatibility);
            return;
        }

        ClientIdentity clientIdentity = compatibility.identity();
        if (handshake != null) {
            handshake.transition(
                    HandshakeState.COMPATIBILITY_ACCEPTED,
                    "compatibility",
                    null,
                    socket.isSocketOpen(),
                    false
            );
        }
        SvgCore.getLogger().debug(
                "WebSocket: Join compatibility accepted client="
                        + clientIdentity.toLogString()
                        + " corr=" + socket.correlationId()
        );

        if (socket.getConnection() != null) {
            socket.getConnection().sendError("Already authenticated.", false);
            return;
        }

        if (!SvgCore.isRunning() || !SvgCore.getConnectionManager().isAcceptingConnections()) {
            SvgCore.getLogger().debug(
                    "WebSocket: Join rejected; server not accepting connections"
                            + " running=" + SvgCore.isRunning()
                            + " accepting=" + SvgCore.getConnectionManager().isAcceptingConnections()
                            + " corr=" + socket.correlationId()
            );
            socket.rejectAndClose(
                    ConnectionStates.DisconnectCodes.SERVER_SHUTDOWN.getCode(),
                    "not_accepting",
                    "Server is not accepting voice connections right now."
            );
            return;
        }

        if (handshake != null) {
            handshake.transition(
                    HandshakeState.AUTHENTICATING,
                    "credentials",
                    null,
                    socket.isSocketOpen(),
                    false
            );
        }

        String username = json.optString("username", "").trim();
        String password = json.optString("password", "");

        AuthResponse response;
        try {
            response = JettyWebSocket.AUTHENTICATOR.authenticate(username, password);
        } catch (RuntimeException e) {
            if (handshake != null) {
                handshake.fail("authenticator-exception", null, socket.isSocketOpen(), e);
            }
            socket.rejectAndClose(
                    ConnectionStates.DisconnectCodes.FATAL_ERROR.getCode(),
                    "auth_exception",
                    "Authentication failed due to an internal server error."
            );
            return;
        }

        if (!response.success()) {
            SvgCore.getLogger().debug(
                    "WebSocket: Authentication failed corr=" + socket.correlationId()
                            + " reason=" + response.message()
            );
            if (handshake != null) {
                handshake.fail("auth-rejected", response.uuid(), socket.isSocketOpen(), null);
            }
            // Keep wording free of bare "timeout"/"access denied:" tokens that the web client
            // historically treated as hard-fatal for premature reconnect loops.
            socket.rejectAndClose(
                    ConnectionStates.DisconnectCodes.FATAL_ERROR.getCode(),
                    "auth_rejected",
                    "Authentication failed: " + sanitizeClientAuthMessage(response.message())
            );
            return;
        }

        SvgPlayer onlinePlayer = SvgCore.getPlayerManager().getPlayer(response.uuid());
        if (onlinePlayer == null || !onlinePlayer.isOnline()) {
            SvgCore.getLogger().debug(
                    "WebSocket: Authenticated credentials but player is not online in PlayerManager"
                            + " corr=" + socket.correlationId()
                            + " uuid=" + response.uuid()
                            + " present=" + (onlinePlayer != null)
                            + " onlineFlag=" + (onlinePlayer != null && onlinePlayer.isOnline())
            );
            if (handshake != null) {
                handshake.fail("player-offline", response.uuid(), socket.isSocketOpen(), null);
            }
            socket.rejectAndClose(
                    ConnectionStates.DisconnectCodes.PLAYER_LEAVE.getCode(),
                    "player_not_online",
                    "Authentication failed: You must be online on the Minecraft server before joining voice chat."
            );
            return;
        }

        if (!SvgCore.isRunning() || !SvgCore.getConnectionManager().isAcceptingConnections()) {
            socket.rejectAndClose(
                    ConnectionStates.DisconnectCodes.SERVER_SHUTDOWN.getCode(),
                    "not_accepting",
                    "Server is not accepting voice connections right now."
            );
            return;
        }

        if (!socket.isSocketOpen()) {
            if (handshake != null) {
                handshake.fail("socket-closed-before-register", onlinePlayer.getUniqueId(), false, null);
            }
            SvgCore.getLogger().debug(
                    "WebSocket: Aborting join; socket closed before session registration"
                            + " corr=" + socket.correlationId()
                            + " uuid=" + onlinePlayer.getUniqueId()
            );
            return;
        }

        final Session originatingSession = socket.getSession();
        SvgConnection connection;
        try {
            connection = SvgCore.getConnectionManager().connect(
                    originatingSession,
                    onlinePlayer,
                    socket.getAudioNegotiation(),
                    clientIdentity
            );
        } catch (IllegalStateException e) {
            if (handshake != null) {
                handshake.fail("connect-rejected", onlinePlayer.getUniqueId(), socket.isSocketOpen(), e);
            }
            socket.rejectAndClose(
                    ConnectionStates.DisconnectCodes.SERVER_SHUTDOWN.getCode(),
                    "connect_rejected",
                    "Server is not accepting voice connections right now."
            );
            return;
        }

        // Ensure this exact socket still owns the registered session before mutating it.
        SvgConnection registered = SvgCore.getConnectionManager().get(onlinePlayer.getUniqueId());
        if (registered != connection || socket.getSession() != originatingSession || !socket.isSocketOpen()) {
            SvgCore.getLogger().debug(
                    "WebSocket: Stale join continuation aborted"
                            + " corr=" + socket.correlationId()
                            + " uuid=" + onlinePlayer.getUniqueId()
                            + " replaced=" + (registered != connection)
                            + " socketOpen=" + socket.isSocketOpen()
            );
            if (handshake != null) {
                handshake.fail("stale-session", onlinePlayer.getUniqueId(), socket.isSocketOpen(), null);
            }
            if (registered == connection) {
                SvgCore.getConnectionManager().remove(connection);
                connection.disconnect(
                        ConnectionStates.DisconnectCodes.CLOSED_SESSION.getCode(),
                        "stale_join"
                );
            }
            return;
        }

        socket.setConnection(connection);

        try {
            connection.authenticate();
        } catch (AuthException e) {
            SvgCore.getLogger().debug(
                    "WebSocket: Failed to authenticate voice connection corr=" + socket.correlationId(),
                    e
            );
            if (handshake != null) {
                handshake.fail("voice-init", onlinePlayer.getUniqueId(), socket.isSocketOpen(), e);
            }
            connection.sendFatal(
                    "Failed to initialize voice chat.",
                    ConnectionStates.DisconnectCodes.FATAL_ERROR.getCode(),
                    "voice_init_failure"
            );
            return;
        }

        if (handshake != null) {
            handshake.transition(
                    HandshakeState.AUTHENTICATED,
                    "voice-authenticated",
                    onlinePlayer.getUniqueId(),
                    socket.isSocketOpen(),
                    false
            );
        }

        JSONObject authenticated = new JSONObject();
        authenticated.put("type", "authenticated");
        authenticated.put("mode", connection.getSessionMode().name());
        connection.sendJson(authenticated);

        VoicechatConnection vcConnection =
                SvgCore.getBridge().getVcServerApi().getConnectionOf(response.uuid());

        // Default group assignment only for WEB_VOICE sessions (not native controllers).
        if (connection.isWebVoice()
                && SvgCore.getConfig().DEFAULT_GROUP_ENABLED.get()
                && vcConnection != null) {

            boolean forceDefaultGroup =
                    SvgCore.getConfig().DEFAULT_GROUP_FORCE_ON_WEB_JOIN.get();

            // Re-fetch before mutating group assignment.
            vcConnection = SvgCore.getBridge().getVcServerApi().getConnectionOf(response.uuid());
            boolean alreadyInGroup =
                    vcConnection != null
                            && vcConnection.isInGroup()
                            && vcConnection.getGroup() != null;

            if (!alreadyInGroup || forceDefaultGroup) {
                try {
                    SvgCore.getGroupManager().createGroup(
                            onlinePlayer,
                            "Svg",
                            SvgCore.getConfig().DEFAULT_GROUP_PASSWORD.get(),
                            Group.Type.OPEN,
                            false,
                            true
                    );
                } catch (RuntimeException e) {
                    SvgCore.getLogger().debug(
                            "WebSocket: Default group assignment failed corr=" + socket.correlationId(),
                            e
                    );
                }
            } else {
                SvgCore.getLogger().debug(
                        "WebSocket: Preserving existing group for "
                                + response.uuid()
                                + " on web join"
                );
            }
        }

        // Authoritative ready confirmation must be sent even if in-game notify fails.
        connection.sendMessage(
                ConnectionStates.MessageType.STATUS,
                "Connected as " + connection.getPlayer().getName() + ".",
                false
        );

        JSONObject ready = new JSONObject();
        ready.put("type", "ready");
        ready.put("message", "Connected as " + connection.getPlayer().getName() + ".");
        ready.put(
                "allowWebCreation",
                Boolean.TRUE.equals(SvgCore.getConfig().GROUPS_ALLOW_WEB_CREATION.get())
        );
        connection.sendJson(ready);

        if (handshake != null) {
            handshake.transition(
                    HandshakeState.READY,
                    "ready-status-sent",
                    onlinePlayer.getUniqueId(),
                    socket.isSocketOpen(),
                    false
            );
        }

        try {
            if (SvgCore.getGroupSyncService() != null) {
                SvgCore.getGroupSyncService().sendReadyPayloads(connection);
            }
        } catch (RuntimeException e) {
            // Never let group sync failures undo an already-sent READY session.
            SvgCore.getLogger().debug(
                    "WebSocket: Ready payloads failed corr=" + socket.correlationId(),
                    e
            );
        }

        try {
            onlinePlayer.sendMessage(SvgCore.getPrefix() + "Connected!");
        } catch (RuntimeException e) {
            SvgCore.getLogger().debug(
                    "WebSocket: In-game connected notify failed corr=" + socket.correlationId(),
                    e
            );
        }

        SvgCore.getLogger().info(
                "[WebSocket] "
                        + connection.getPlayer().getName()
                        + " authenticated."
                        + " mode=" + connection.getSessionMode()
                        + " corr=" + socket.correlationId()
                        + " state=" + socket.handshakeState()
        );
    }

    private static String sanitizeClientAuthMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Invalid username or password.";
        }
        String lower = message.toLowerCase();
        // Keep generic credential failures opaque (do not distinguish user vs password).
        if (lower.contains("invalid username or password")) {
            return "Invalid username or password.";
        }
        if (lower.contains("didn't join") || lower.contains("did not join") || lower.contains("timeout")) {
            return "You must be online on the Minecraft server before joining voice chat.";
        }
        if (lower.contains("access denied") && lower.contains("mod")) {
            return "You do not have permission to use browser voice chat.";
        }
        if (lower.contains("access denied") && (lower.contains("banned") || lower.contains("permission"))) {
            return "You do not have permission to use browser voice chat.";
        }
        if (lower.contains("access denied") && lower.contains("bedrock")) {
            return message;
        }
        if (lower.contains("access denied")) {
            return "Invalid username or password.";
        }
        return message;
    }

    private void closeCompatibilityFailure(JettyWebSocket socket, ClientCompatibilityResult compatibility) {
        socket.markCloseInitiator("server-compatibility:" + compatibility.closeReason());
        Session session = socket.getSession();

        if (session == null || !session.isOpen()) {
            return;
        }

        try {
            session.close(compatibility.closeCode(), compatibility.closeReason());
        } catch (Exception ignored) {
        }
    }
}
