package io.github.theodoremeyer.simplevoicegeyser.core.server;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import org.eclipse.jetty.websocket.api.Session;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Class Managing Websockets
 */
public final class WebSocketManager {

    /**
     * A list of Websockets that are actively connected
     */
    private final Map<UUID, Session> clients = new ConcurrentHashMap<>();

    /**
     * Add a Websocket to the list
     * @param uuid uuid of session, usually player session is associated to.
     * @param session session/websocket of the client
     * @return true/false whether it did it successfully.
     */
    public boolean addClient(UUID uuid, Session session) {
        Session old = clients.put(uuid, session);

        if (old != null && old != session && old.isOpen()) {
            try {
                old.close(); // kill old connection
            } catch (Exception e) {
                SvgCore.debug("WS", "Failed to close old session for UUID: " + uuid, e);
                return false;
            }
        }
        return true;
    }

    /**
     * Get a known/active client
     * @param uuid the uuid to get for
     * @return the Client
     */
    public Session getClient(UUID uuid) {
        return clients.get(uuid);
    }

    /**
     * Removes closed voice chat player session to prevent problems
     * This method needs to be fixed
     * @param uuid uuid of websocket to disconnect
     * @param session session of websocket to disconnect
     */
    public void removeClient(UUID uuid, Session session) {
        if (uuid == null || session == null) {
            SvgCore.getLogger().warning("[WebSocketManager] Tried to remove null UUID/session");
            return;
        }

        Session current = clients.get(uuid);

        // CRITICAL FIX
        if (current != session) {
            // Old/stale session → ignore
            return;
        }

        clients.remove(uuid);

        if (session.isOpen()) {
            try {
                session.close();
            } catch (Exception ignored) {}
        }
    }

    /**
     * Disconnects a specific websocket client from the server
     * @param uuid uuid of websocket to disconnect
     * @return whether is successfully disconnected or not
     */
    public boolean disconnectClient(UUID uuid) {
        Session session = clients.remove(uuid);
        if (session != null && session.isOpen()) { //make sure the session exists
            session.close(); //close session
            return true;
        }
        return false;
    }

    /**
     * Disconnects a player when they leave
     * @param player uuid of player leaving
     */
    public void playerLeave(SvgPlayer player) {
        UUID uuid = player.getUniqueId();
        sendJson(uuid, "message", player.getName() + " left the game.");
        disconnectClient(uuid);
    }

    /**
     * Disconnects all websocket clients from the server
     */
    public void disconnectAllClients() {
        int count = 0;
        for (UUID uuid : new HashSet<>(clients.keySet())) {
            if (disconnectClient(uuid)) {
                count++;
            }
        }
        SvgCore.getLogger().info("Disconnected " + count + " clients");
    }

    /**
     * Send a message to the Websocket client
     * @param uuid uuid of session to send to.
     * @param type the type of message to send, can be: error, message, chat.
     * @param message the message to send.
     */
    public void sendJson(UUID uuid, String type, String message) {
        Session session = clients.get(uuid); // get session to send to
        if (session == null || !session.isOpen()) {
            SvgCore.getLogger().warning("Attempted to send message to non-existent or closed session: " + uuid);
            return;
        }

        JSONObject json = new JSONObject();
        json.put("type", type);
        json.put("message", message);
        try {
            session.getRemote().sendString(json.toString()); //send the message
        } catch (IOException e) {
            SvgCore.getLogger().error("Failed to send message to client: " + uuid, e);
        }
    }

    /**
     * Send A JSON message to the Websocket Client
     * This allows custom messages to be sent to client other than status data
     * @param uuid the uuid of session to send to
     * @param json the Data to send
     */
    public void sendJson(UUID uuid, JSONObject json) {
        Session session = clients.get(uuid);

        if (session == null || !session.isOpen()) {
            SvgCore.getLogger().warning("Attempted to send message to non-existent or closed session: " + uuid);
            return;
        }

        try {
            session.getRemote().sendString(json.toString());
        } catch (Exception e) {
            SvgCore.getLogger().error("Failed to send message to client: " + uuid, e);
        }
    }
}
