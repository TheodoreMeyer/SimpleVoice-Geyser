package io.github.theodoremeyer.simplevoicegeyser.core.server.packets;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.ConnectionStates;
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

        SvgCore.getLogger().debug("WebSocket: Join attempt #"
                + socket.addJoinAttempt() + " from "
                + socket.getSession().getRemoteAddress());

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

        SvgConnection connection = SvgCore.getConnectionManager().connect(
                socket.getSession(),
                response.player(),
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

        VoicechatConnection vcConnection =
                SvgCore.getBridge().getVcServerApi().getConnectionOf(response.uuid());

        if (SvgCore.getConfig().DEFAULT_GROUP_ENABLED.get() && vcConnection != null) {

            boolean forceDefaultGroup =
                    SvgCore.getConfig().DEFAULT_GROUP_FORCE_ON_WEB_JOIN.get();

            boolean alreadyInGroup =
                    vcConnection.isInGroup() && vcConnection.getGroup() != null;

            if (!alreadyInGroup || forceDefaultGroup) {
                SvgCore.getGroupManager().createGroup(
                        response.player(),
                        "Svg",
                        SvgCore.getConfig().DEFAULT_GROUP_PASSWORD.get(),
                        Group.Type.OPEN,
                        false,
                        true
                );
            } else {
                SvgCore.getLogger().debug(
                        "WebSocket: Preserving existing group for "
                                + response.uuid()
                                + " on web join"
                );
            }
        }

        response.player().sendMessage(SvgCore.getPrefix() + "Connected!");

        connection.sendMessage(
                ConnectionStates.MessageType.STATUS,
                "Connected as " + connection.getPlayer().getName() + ".",
                false
        );

        SvgCore.getLogger().info(
                "[WebSocket] "
                        + connection.getPlayer().getName()
                        + " authenticated."
        );
    }

    private void closeCompatibilityFailure(JettyWebSocket socket, ClientCompatibilityResult compatibility) {

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
