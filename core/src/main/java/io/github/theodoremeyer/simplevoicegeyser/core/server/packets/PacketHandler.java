package io.github.theodoremeyer.simplevoicegeyser.core.server.packets;

import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.ConnectionStates;
import io.github.theodoremeyer.simplevoicegeyser.core.server.servlets.JettyWebSocket;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public final class PacketHandler {

    private final Map<String, Packet> packets = new HashMap<>();


    public PacketHandler() {
        register(new ChatPacket());
        register(new JoinPacket());
        register(new CapabilitiesPacket());
    }

    public void register(Packet packet) {
        packets.put(packet.getType(), packet);
    }

    public void handle(JettyWebSocket socket, JSONObject json) {
        Packet packet = packets.get(json.getString("type"));

        if (packet == null) {
            socket.sendRaw(
                    ConnectionStates.MessageType.ERROR,
                    "Unknown packet type.",
                    false
            );
            return;
        }

        packet.handle(socket, json);
    }
}