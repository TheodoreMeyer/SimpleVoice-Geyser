package io.github.theodoremeyer.simplevoicegeyser.core.server.packets;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.ConnectionStates;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.SvgConnection;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.auth.AuthException;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.auth.AuthResponse;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.auth.PendingJoinWaiter;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.compatibility.ClientCompatibilityResult;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.compatibility.ClientCompatibilityValidator;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.compatibility.ClientIdentity;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.compatibility.ClientTypePolicy;
import io.github.theodoremeyer.simplevoicegeyser.core.server.servlets.JettyWebSocket;
import org.eclipse.jetty.websocket.api.Session;
import org.json.JSONObject;

import java.time.Duration;
import java.util.UUID;

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

        SvgCore.getLogger().debug("WebSocket: Join attempt #"
                + socket.addJoinAttempt() + " from "
                + socket.getSession().getRemoteAddress());

        boolean requireLatest = Boolean.TRUE.equals(SvgCore.getConfig().REQUIRE_LATEST.get());
        ClientCompatibilityResult compatibility = ClientCompatibilityValidator.validate(
                json,
                SvgCore.VERSION,
                SvgCore.BUILD_ID,
                ClientTypePolicy.fromConfig(SvgCore.getConfig()),
                requireLatest
        );

        if (!compatibility.accepted()) {
            SvgCore.getLogger().debug(
                    "WebSocket: Join rejected by compatibility gate reason="
                            + compatibility.closeReason()
            );
            socket.sendRaw(ConnectionStates.MessageType.ERROR, compatibility.message(), false);
            closeCompatibilityFailure(socket, compatibility);
            return;
        }

        ClientIdentity clientIdentity = compatibility.identity();
        SvgCore.getLogger().debug(
                "WebSocket: Join compatibility accepted client="
                        + clientIdentity.toLogString()
        );

        if (socket.getConnection() != null) {
            socket.getConnection().sendError("Already authenticated.", false);
            return;
        }

        String username = json.optString("username", "").trim();
        String password = json.optString("password", "");

        AuthResponse response =
                JettyWebSocket.AUTHENTICATOR.authenticate(username, password);

        if (!response.success()) {
            socket.sendRaw(
                    ConnectionStates.MessageType.ERROR,
                    "Authentication failed: " + response.message(),
                    false
            );
            return;
        }

        if (response.isPending()) {
            beginPendingJoin(socket, response.uuid(), username, clientIdentity);
            return;
        }

        completeJoin(socket, response.uuid(), response.player(), username, clientIdentity);
    }

    private void beginPendingJoin(
            JettyWebSocket socket,
            UUID uuid,
            String username,
            ClientIdentity clientIdentity
    ) {
        int timeoutSeconds = Math.max(1, SvgCore.getConfig().VC_TIMEOUT.get());

        socket.setPendingJoinUuid(uuid);

        boolean alreadyOnline = SvgCore.getPlayerManager().getPlayer(uuid) != null
                || SvgCore.getPlayerManager().findOnlineByUsername(username) != null;
        String status = alreadyOnline
                ? "Waiting for voice chat... (" + timeoutSeconds + "s)"
                : "Waiting for you to join Minecraft... (" + timeoutSeconds + "s)";

        socket.sendRaw(
                ConnectionStates.MessageType.STATUS,
                status,
                false
        );

        SvgCore.getLogger().info(
                "[WebSocket] Pending join for " + username
                        + " (" + uuid + "); waiting up to " + timeoutSeconds + "s"
                        + (alreadyOnline ? " for voice chat (player already online)." : " for Minecraft.")
        );

        SvgCore.getPendingJoinWaiter()
                .waitForReady(uuid, Duration.ofSeconds(timeoutSeconds), socket)
                .whenComplete((ready, error) -> {
                    socket.clearPendingJoinUuid(uuid);

                    if (socket.getSession() == null || !socket.getSession().isOpen()) {
                        return;
                    }
                    if (socket.getConnection() != null) {
                        return;
                    }

                    if (error instanceof PendingJoinWaiter.JoinWaitExpiredException) {
                        socket.sendRaw(
                                ConnectionStates.MessageType.ERROR,
                                "You did not join the Minecraft server in time. Join the game, then try again.",
                                true
                        );
                        closeSession(
                                socket,
                                ConnectionStates.DisconnectCodes.TIMEOUT.getCode(),
                                "join_wait_expired"
                        );
                        return;
                    }

                    if (error != null) {
                        SvgCore.getLogger().debug(
                                "WebSocket: Pending join cancelled for " + uuid + ": " + error.getMessage()
                        );
                        return;
                    }

                    if (ready == null) {
                        return;
                    }

                    if (ready.rejected()) {
                        socket.sendRaw(
                                ConnectionStates.MessageType.ERROR,
                                "Authentication failed: " + ready.failureMessage(),
                                true
                        );
                        closeSession(
                                socket,
                                ConnectionStates.DisconnectCodes.FATAL_ERROR.getCode(),
                                "auth_rejected"
                        );
                        return;
                    }

                    completeJoin(socket, ready.uuid(), ready.player(), username, clientIdentity);
                });
    }

    private void completeJoin(
            JettyWebSocket socket,
            UUID uuid,
            SvgPlayer player,
            String username,
            ClientIdentity clientIdentity
    ) {
        if (socket.getConnection() != null) {
            return;
        }

        SvgConnection connection = SvgCore.getConnectionManager().connect(
                socket.getSession(),
                player,
                socket.getAudioNegotiation(),
                clientIdentity
        );

        socket.setConnection(connection);

        try {
            connection.authenticate();
        } catch (AuthException e) {
            SvgCore.getLogger().debug(
                    "WebSocket: Failed to authenticate voice connection",
                    e
            );

            connection.sendFatal(
                    "Failed to initialize voice chat.",
                    ConnectionStates.DisconnectCodes.FATAL_ERROR.getCode(),
                    "voice_init_failure"
            );
            return;
        }

        JettyWebSocket.AUTHENTICATOR.markJoinSuccess(username);

        VoicechatConnection vcConnection =
                SvgCore.getBridge().getVcServerApi().getConnectionOf(uuid);

        if (SvgCore.getConfig().DEFAULT_GROUP_ENABLED.get() && vcConnection != null) {

            boolean forceDefaultGroup =
                    SvgCore.getConfig().DEFAULT_GROUP_FORCE_ON_WEB_JOIN.get();

            boolean alreadyInGroup =
                    vcConnection.isInGroup() && vcConnection.getGroup() != null;

            if (!alreadyInGroup || forceDefaultGroup) {
                SvgCore.getGroupManager().createGroup(
                        player,
                        "Svg",
                        SvgCore.getConfig().DEFAULT_GROUP_PASSWORD.get(),
                        Group.Type.OPEN,
                        false,
                        true
                );
            } else {
                SvgCore.getLogger().debug(
                        "WebSocket: Preserving existing group for "
                                + uuid
                                + " on web join"
                );
            }
        }

        player.sendMessage(SvgCore.getPrefix() + "Connected!");

        connection.sendMessage(
                ConnectionStates.MessageType.STATUS,
                "Connected as " + connection.getPlayer().getName() + ".",
                false
        );

        VoicePlayersBroadcaster.broadcast();
        GroupsBroadcaster.sendTo(connection);

        SvgCore.getLogger().info(
                "[WebSocket] "
                        + connection.getPlayer().getName()
                        + " authenticated."
        );
    }

    private void closeCompatibilityFailure(JettyWebSocket socket, ClientCompatibilityResult compatibility) {
        closeSession(socket, compatibility.closeCode(), compatibility.closeReason());
    }

    private void closeSession(JettyWebSocket socket, int code, String reason) {
        Session session = socket.getSession();

        if (session == null || !session.isOpen()) {
            return;
        }

        try {
            session.close(code, reason);
        } catch (Exception ignored) {
        }
    }
}
