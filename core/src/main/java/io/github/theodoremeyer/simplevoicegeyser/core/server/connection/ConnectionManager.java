package io.github.theodoremeyer.simplevoicegeyser.core.server.connection;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.audio.AudioSessionNegotiation;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.compatibility.ClientIdentity;
import org.eclipse.jetty.websocket.api.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The authoritative manager for all active websocket/voice connections.
 * This is the ONLY source of truth for connected clients.
 */
public final class ConnectionManager {

    private final Map<UUID, SvgConnection> connections =
            new ConcurrentHashMap<>();

    private final AtomicBoolean accepting = new AtomicBoolean(true);

    /**
     * No arg Constructor
     */
    public ConnectionManager() {}

    /**
     * Reject new connections. Idempotent.
     */
    public void rejectNewConnections() {
        accepting.set(false);
    }

    /**
     * @return whether this manager is still accepting new connections
     */
    public boolean isAcceptingConnections() {
        return accepting.get();
    }

    /**
     * Connect a Session, and hold Identity and AudioSessionNegotiation for the player.
     * @param session Session to connect
     * @param player player Session represents
     * @param audioNegotiation the negotiation session
     * @param clientIdentity Client's Identity
     * @return the SvgConnection
     */
    public SvgConnection connect(
            Session session,
            SvgPlayer player,
            AudioSessionNegotiation audioNegotiation,
            ClientIdentity clientIdentity
    ) {

        if (!accepting.get()) {
            throw new IllegalStateException("ConnectionManager is not accepting connections");
        }

        UUID uuid = player.getUniqueId();
        SvgConnection connection = new SvgConnection(session, player, audioNegotiation, clientIdentity);

        SvgConnection oldConnection = connections.put(uuid, connection);
        if (oldConnection != null && oldConnection != connection) {
            SvgCore.getLogger().debug(
                    "ConnectionManager: Replacing existing connection for: " + uuid
            );

            oldConnection.disconnect(
                    ConnectionStates.DisconnectCodes.REPLACED.getCode(),
                    "Replaced by new session"
            );
        }

        SvgCore.getLogger().info(
                "[ConnectionManager] Connected: " + player.getName() + " (" + uuid + ")"
        );

        return connection;
    }

    /**
     * Get the Connection by Uuid
     * @param uuid player's uuid
     * @return the Connection if found
     */
    public SvgConnection get(UUID uuid) {
        return connections.get(uuid);
    }

    /**
     * Disconnnect a Client Connection
     * @param uuid player's Uuid
     * @param code Code
     * @param reason Reason
     */
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
     * @param connection the connection to remove
     */
    public void remove(SvgConnection connection) {
        if (connection == null) {
            return;
        }

        UUID uuid = connection.getUuid();
        connections.computeIfPresent(uuid, (ignored, current) -> current != connection ? current : null);
    }

    /**
     * Disconnect all Connections
     */
    public void disconnectAll() {
        rejectNewConnections();

        List<SvgConnection> snapshot = new ArrayList<>(connections.values());
        connections.clear();

        for (SvgConnection connection : snapshot) {
            try {
                connection.disconnect(1001, "Server shutting down");
            } catch (Exception e) {
                SvgCore.getLogger().debug(
                        "ConnectionManager: Error while disconnecting " + connection.getUuid(),
                        e
                );
            }
        }

        SvgCore.getLogger().info("[ConnectionManager] Disconnected all clients");
    }
}
