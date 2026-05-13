package io.github.theodoremeyer.simplevoicegeyser.core.server.connection;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.audio.AudioSessionNegotiation;
import org.eclipse.jetty.websocket.api.Session;

import java.util.Collection;
import java.util.Collections;
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
            UUID uuid,
            Session session,
            SvgPlayer player,
            AudioSessionNegotiation audioNegotiation
    ) {

        SvgConnection oldConnection = connections.remove(uuid);

        if (oldConnection != null) {
            SvgCore.getLogger().debug(
                    "ConnectionManager: Replacing existing connection for: " + uuid
            );

            oldConnection.disconnect(
                    ConnectionStates.DisconnectCodes.REPLACED.getCode(),
                    "Replaced by new session"
            );
        }

        SvgConnection connection = new SvgConnection(session, player, audioNegotiation);
        connections.put(uuid, connection);

        SvgCore.getLogger().info(
                "[ConnectionManager] Connected: " + player.getName() + " (" + uuid + ")"
        );

        return connection;
    }

    public SvgConnection get(UUID uuid) {
        return connections.get(uuid);
    }

    public boolean isConnected(UUID uuid) {
        return connections.containsKey(uuid);
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

    public Collection<SvgConnection> getConnections() {
        return Collections.unmodifiableCollection(connections.values());
    }

    public int size() {
        return connections.size();
    }
}
