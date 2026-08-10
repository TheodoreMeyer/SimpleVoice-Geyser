package io.github.theodoremeyer.simplevoicegeyser.core.server.packets;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.server.servlets.JettyWebSocket;
import org.json.JSONObject;

/**
 * Client requests a full groups snapshot.
 */
public final class GroupsSubscribePacket implements Packet {

    @Override
    public String getType() {
        return "groups_subscribe";
    }

    @Override
    public void handle(JettyWebSocket socket, JSONObject json) {
        if (!GroupPacketSupport.requireReady(socket)) {
            return;
        }
        SvgCore.getGroupSyncService().sendSnapshot(socket.getConnection());
    }
}
