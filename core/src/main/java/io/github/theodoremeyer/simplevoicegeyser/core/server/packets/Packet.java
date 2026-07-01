package io.github.theodoremeyer.simplevoicegeyser.core.server.packets;

import io.github.theodoremeyer.simplevoicegeyser.core.server.servlets.JettyWebSocket;
import org.json.JSONObject;

public interface Packet {

    String getType();

    void handle(JettyWebSocket socket, JSONObject json);
}
