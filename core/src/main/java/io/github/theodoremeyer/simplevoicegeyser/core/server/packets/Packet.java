package io.github.theodoremeyer.simplevoicegeyser.core.server.packets;

import io.github.theodoremeyer.simplevoicegeyser.core.server.servlets.JettyWebSocket;
import org.json.JSONObject;

/**
 * Packet interface for PacketHandlers
 */
public interface Packet {

    /**
     * Type of Packet it should handle
     * @return String type
     */
    String getType();

    /**
     * Handle a JSON packet
     * @param socket the socket that received it
     * @param json the packet itself
     */
    void handle(JettyWebSocket socket, JSONObject json);
}
