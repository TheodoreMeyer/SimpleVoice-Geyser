package io.github.theodoremeyer.simplevoicegeyser.core.server.connection;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.audio.AudioSessionNegotiation;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.compatibility.ClientIdentity;
import de.maxhenkel.voicechat.api.packets.SoundPacket;
import org.eclipse.jetty.websocket.api.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The authoritative manager for all active websocket/voice connections.
 * This is the ONLY source of truth for connected clients.
 */
public final class ConnectionManager {

    private final Map<UUID, SvgConnection> connections =
            new ConcurrentHashMap<>();

    public ConnectionManager() {}

    public SvgConnection connect(
            Session session,
            SvgPlayer player,
            AudioSessionNegotiation audioNegotiation,
            ClientIdentity clientIdentity
    ) {

        SvgConnection oldConnection = connections.remove(player.getUniqueId());

        if (oldConnection != null) {
            SvgCore.getLogger().debug(
                    "ConnectionManager: Replacing existing connection for: " + player.getUniqueId()
            );

            oldConnection.disconnect(
                    ConnectionStates.DisconnectCodes.REPLACED.getCode(),
                    "Replaced by new session"
            );
        }

        SvgConnection connection = new SvgConnection(session, player, audioNegotiation, clientIdentity);
        connections.put(player.getUniqueId(), connection);

        SvgCore.getLogger().info(
                "[ConnectionManager] Connected: " + player.getName() + " (" + player.getUniqueId() + ")"
        );

        return connection;
    }

    public SvgConnection get(UUID uuid) {
        return connections.get(uuid);
    }

    public VoicePacketForwarder.Result forwardVoicePacket(UUID senderUuid, SoundPacket packet) {
        List<VoicePacketForwarder.Receiver<SoundPacket>> receivers = new ArrayList<>(connections.size());
        for (SvgConnection connection : connections.values()) {
            receivers.add(new SvgConnectionReceiver(connection));
        }
        return VoicePacketForwarder.forward(senderUuid, packet, receivers);
    }

    public void disconnect(UUID uuid, int code, String reason) {
        SvgConnection connection = connections.remove(uuid);

        if (connection == null) {
            return;
        }

        connection.disconnect(code, reason);

        SvgCore.getLogger().info(
                "[ConnectionManager] Disconnected: " + uuid + " (" + reason + ")"
        );
    }

    /**
     * Remove a connection after the websocket close has already been sent.
     */
    public void remove(SvgConnection connection) {
        if (connection == null) {
            return;
        }

        UUID uuid = connection.getUuid();
        connections.computeIfPresent(uuid, (ignored, current) -> current != connection ? current : null);
    }

    public void disconnectAll() {
        for (SvgConnection connection : connections.values()) {
            connection.disconnect(1001, "Server shutting down");
        }

        connections.clear();
        SvgCore.getLogger().info("[ConnectionManager] Disconnected all clients");
    }

    private record SvgConnectionReceiver(SvgConnection connection) implements VoicePacketForwarder.Receiver<SoundPacket> {
        @Override
        public UUID uuid() {
            return connection.getUuid();
        }

        @Override
        public boolean authenticated() {
            return connection.isAuthenticated();
        }

        @Override
        public boolean closed() {
            return connection.isClosed();
        }

        @Override
        public boolean canReceiveVoicePacket() {
            return connection.getAudioListener() != null;
        }

        @Override
        public void receive(SoundPacket packet) {
            connection.receiveVoicePacket(packet);
        }
    }
}
