package io.github.theodoremeyer.simplevoicegeyser.core.server.packets;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.managers.GroupManager;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.SvgConnection;
import io.github.theodoremeyer.simplevoicegeyser.core.server.ratelimit.RateLimitResult;
import io.github.theodoremeyer.simplevoicegeyser.core.server.servlets.JettyWebSocket;
import org.json.JSONObject;

import java.util.UUID;

/**
 * Join a group by UUID.
 */
public final class GroupJoinPacket implements Packet {

    @Override
    public String getType() {
        return "group_join";
    }

    @Override
    public void handle(JettyWebSocket socket, JSONObject json) {
        String operationId = json.optString("operationId", null);
        if (!GroupPacketSupport.requireReady(socket, operationId)) {
            return;
        }

        SvgConnection connection = socket.getConnection();
        long revision = GroupPacketSupport.groups().getRevision();

        RateLimitResult joinLimit = SvgCore.getRateLimitService()
                .tryGroupJoin(GroupPacketSupport.connectionKey(connection));
        if (!joinLimit.allowed()) {
            GroupPacketSupport.rejectRateLimit(connection, operationId, joinLimit, revision);
            return;
        }

        if (!GroupPacketSupport.registerMutation(connection, operationId)) {
            GroupPacketSupport.rejectDuplicate(connection, operationId, revision);
            return;
        }

        UUID groupId = GroupPacketSupport.parseUuid(json.optString("groupId", null));
        String password = json.has("password") ? json.optString("password", null) : null;

        if (groupId == null) {
            GroupPacketSupport.sendOperationResult(
                    connection, operationId, false, "groupId required.", GroupPacketSupport.groups().getRevision()
            );
            return;
        }

        GroupManager.OpResult result = GroupPacketSupport.groups()
                .joinGroup(connection.getPlayer(), groupId, password);

        if (result.success()) {
            SvgCore.getRateLimitService().markGroupJoin(GroupPacketSupport.connectionKey(connection));
        }

        GroupPacketSupport.sendOperationResult(connection, operationId, result, "group_join");
    }
}
