package io.github.theodoremeyer.simplevoicegeyser.core.server.packets;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.managers.GroupManager;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.SvgConnection;
import io.github.theodoremeyer.simplevoicegeyser.core.server.ratelimit.RateLimitResult;
import io.github.theodoremeyer.simplevoicegeyser.core.server.servlets.JettyWebSocket;
import org.json.JSONObject;

import java.util.UUID;

/**
 * Leave the current group.
 *
 * <p>Client schema:
 * <pre>
 * {
 *   "type": "group_leave",
 *   "operationId": "...",
 *   "expectedGroupId": "optional-current-group-id"
 * }
 * </pre>
 */
public final class GroupLeavePacket implements Packet {

    @Override
    public String getType() {
        return "group_leave";
    }

    @Override
    public void handle(JettyWebSocket socket, JSONObject json) {
        String operationId = json.optString("operationId", null);
        if (!GroupPacketSupport.requireReady(socket, operationId)) {
            return;
        }

        SvgConnection connection = socket.getConnection();
        long revision = GroupPacketSupport.groups().getRevision();

        RateLimitResult leaveLimit = SvgCore.getRateLimitService()
                .tryGroupLeave(GroupPacketSupport.connectionKey(connection));
        if (!leaveLimit.allowed()) {
            GroupPacketSupport.rejectRateLimit(connection, operationId, leaveLimit, revision);
            return;
        }

        if (!GroupPacketSupport.registerMutation(connection, operationId)) {
            GroupPacketSupport.rejectDuplicate(connection, operationId, revision);
            return;
        }

        UUID expectedGroupId = GroupPacketSupport.parseUuid(json.optString("expectedGroupId", null));
        GroupManager.OpResult result = GroupPacketSupport.groups()
                .leaveGroupDetailed(connection.getPlayer(), expectedGroupId);

        if (result.success()) {
            SvgCore.getRateLimitService().markGroupLeave(GroupPacketSupport.connectionKey(connection));
        }

        GroupPacketSupport.sendOperationResult(connection, operationId, result, "group_leave");
    }
}
