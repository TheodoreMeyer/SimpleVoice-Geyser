package io.github.theodoremeyer.spigotmc.simplevoicegeysertoo;

import org.eclipse.jetty.websocket.api.Session;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Map;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Class Managing Websockets
 */
public class WebSocketManager {

    /**
     * A list of Websockets that are actively connected
     */
    protected static final Map<UUID, Session> clients = new ConcurrentHashMap<>();

    /**
     * Add a Websocket to the list
     * @param uuid uuid of session, usually player session is associated to.
     * @param session session/websocket of the client
     * @return true/false whether it did it successfully.
     */
    public static boolean addClient(UUID uuid, Session session) {
        if (clients.containsKey(uuid)) {
            return false; // already connected
        }
        clients.put(uuid, session);
        return true;
    }

    /**
     * Removes closed voice chat player session to prevent problems
     * This method needs to be fixed
     * @param uuid uuid of websocket to disconnect
     */
    public static void removeClient(UUID uuid) {
        if (uuid == null) {
            SVGPlugin.log().warning("-[WebSocketManager] Tried to remove null UUID");
            return;
        }
        Session session = clients.get(uuid);
        if (session != null) {
            session.close(); //close associated websocket session
            clients.remove(uuid); //remove the closed session from the map
        }
    }

    /**
     * Disconnects a specific websocket client from the server
     * @param uuid uuid of websocket to disconnect
     * @return whether is successfully disconnected or not
     */
    public static boolean disconnectClient(UUID uuid) {
        Session session = clients.remove(uuid);
        if (session != null && session.isOpen()) { //make sure the session exists
            session.close(); //close session
            return true;
        }
        return false;
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
    }

    /**
     * Send a message to the Websocket client
     * @param uuid uuid of session to send to.
     * @param type the type of message to send, can be: error, message, chat.
     * @param message the message to send.
     */
    public static void sendJson(UUID uuid, String type, String message) {
        Session session = clients.get(uuid); // get session to send to
        JSONObject json = new JSONObject();
        json.put("type", type);
        json.put("message", message);
        try {
            session.getRemote().sendString(json.toString()); //send the message
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
