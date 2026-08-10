package io.github.theodoremeyer.simplevoicegeyser.core.server.packets;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.server.servlets.JettyWebSocket;
import org.json.JSONObject;

/**
 * Client requests a revisioned groups snapshot with rate limiting and coalescing.
 */
public final class GroupsRefreshPacket implements Packet {

    @Override
    public String getType() {
        return "groups_refresh";
    }

    @Override
    public void handle(JettyWebSocket socket, JSONObject json) {
        String operationId = json.optString("operationId", null);
        if (!GroupPacketSupport.requireReady(socket, operationId)) {
            return;
        }

        SvgCore.getGroupSyncService().handleRefresh(socket.getConnection(), operationId);
    }
}
