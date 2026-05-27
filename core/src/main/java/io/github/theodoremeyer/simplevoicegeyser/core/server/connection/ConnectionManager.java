package io.github.theodoremeyer.simplevoicegeyser.core.server.connection;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
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

    /**
     * Active live connections.
     */
    private final Map<UUID, SvgConnection> connections =
            new ConcurrentHashMap<>();

    /**
     * Creates the connection manager.
     */
    public ConnectionManager() {}

    /**
     * Registers a new connection.
     * If a connection already exists for the UUID,
     * the old connection is disconnected and replaced.
     *
     * @param uuid player uuid
     * @param session websocket session
     * @param player associated player
     * @return newly created connection
     */
    public SvgConnection connect(
            UUID uuid,
            Session session,
            SvgPlayer player
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

        SvgConnection connection =
                new SvgConnection(
                        session,
                        player
                );

        connections.put(uuid, connection);

        SvgCore.getLogger().info(
                "[ConnectionManager] Connected: " +
                        player.getName() +
                        " (" + uuid + ")"
        );

        return connection;
    }

    /**
     * Gets a connection by UUID.
     *
     * @param uuid uuid to lookup
     * @return connection or null
     */
    public SvgConnection get(UUID uuid) {
        return connections.get(uuid);
    }

    /**
     * Gets whether a player is connected.
     *
     * @param uuid player uuid
     * @return true if connected
     */
    public boolean isConnected(UUID uuid) {
        return connections.containsKey(uuid);
    }

    /**
     * Removes and disconnects a connection.
     *
     * @param uuid uuid to disconnect
     * @param code websocket close code
     * @param reason close reason
     */
    public void disconnect(
            UUID uuid,
            int code,
            String reason
    ) {

        SvgConnection connection =
                connections.remove(uuid);

        if (connection == null) {
            return;
        }

        connection.disconnect(code, reason);

        SvgCore.getLogger().info(
                "[ConnectionManager] Disconnected: " +
                        uuid +
                        " (" + reason + ")"
        );
    }

    /**
     * Removes a connection ONLY if it matches the provided session.
     * Prevents stale websocket sessions from disconnecting newer sessions.
     *
     * @param connection connection attempting removal
     */
    public void remove(SvgConnection connection) {

        if (connection == null) {
            return;
        }

        UUID uuid = connection.getUuid();

        connections.computeIfPresent(uuid, (ignored, current) -> {

            if (current != connection) {
                return current;
            }

            return null;
        });
    }

    /**
     * Disconnects all active clients.
     */
    public void disconnectAll() {

        for (SvgConnection connection : connections.values()) {
            connection.disconnect(
                    1001,
                    "Server shutting down"
            );
        }

        connections.clear();

        SvgCore.getLogger().info(
                "[ConnectionManager] Disconnected all clients"
        );
    }

    /**
     * Gets all active connections.
     *
     * @return immutable collection of connections
     */
    public Collection<SvgConnection> getConnections() {
        return Collections.unmodifiableCollection(
                connections.values()
        );
    }

    /**
     * Gets the amount of connected clients.
     *
     * @return online websocket count
     */
    public int size() {
        return connections.size();
    }
}