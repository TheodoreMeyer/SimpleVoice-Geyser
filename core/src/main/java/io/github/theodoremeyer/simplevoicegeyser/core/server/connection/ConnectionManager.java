package io.github.theodoremeyer.simplevoicegeyser.core.server.connection;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.audio.AudioSessionNegotiation;
import org.eclipse.jetty.websocket.api.Session;

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

    /**
     * No-var contructor, as there is no state to initialize beyond the empty connections map.
     */
    public ConnectionManager() {}

    /**
     * Connect a player to the system
     * @param session session player is connected through
     * @param player player itself
     * @param audioNegotiation the known info negotiator for the session
     * @return the player Connection
     */
    public SvgConnection connect(
            Session session,
            SvgPlayer player,
            AudioSessionNegotiation audioNegotiation
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

        SvgConnection connection = new SvgConnection(session, player, audioNegotiation);
        connections.put(player.getUniqueId(), connection);

        SvgCore.getLogger().info(
                "[ConnectionManager] Connected: " + player.getName() + " (" + player.getUniqueId() + ")"
        );

        return connection;
    }

    /**
     * Get a connection using a uuid
     * @param uuid player/connection Uuid
     * @return the Connection if found.
     */
    public SvgConnection get(UUID uuid) {
        return connections.get(uuid);
    }

    /**
     * Disconnect a player's audio connection
     * @param uuid player uuid
     * @param code close code
     * @param reason reason
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
     * Remove a connection from the manager, without sending a disconnect packet. Used for cleanup after a disconnect has already been sent.
     * @param connection connection to close
     */
    public void remove(SvgConnection connection) {
        if (connection == null) {
            return;
        }

        UUID uuid = connection.getUuid();
        connections.computeIfPresent(uuid, (ignored, current) -> current != connection ? current : null);
    }

    /**
     * Disconnect all connections
     */
    public void disconnectAll() {
        for (SvgConnection connection : connections.values()) {
            connection.disconnect(1001, "Server shutting down");
        }

        connections.clear();
        SvgCore.getLogger().info("[ConnectionManager] Disconnected all clients");
    }
}
