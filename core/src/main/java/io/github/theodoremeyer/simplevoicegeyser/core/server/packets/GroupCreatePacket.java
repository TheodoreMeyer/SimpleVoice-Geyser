package io.github.theodoremeyer.simplevoicegeyser.core.server.packets;

import de.maxhenkel.voicechat.api.Group;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.managers.GroupManager;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.SvgConnection;
import io.github.theodoremeyer.simplevoicegeyser.core.server.ratelimit.RateLimitResult;
import io.github.theodoremeyer.simplevoicegeyser.core.server.servlets.JettyWebSocket;
import org.json.JSONObject;

/**
 * Create a group from the web client.
 *
 * <p>Client schema:
 * <pre>
 * {
 *   "type": "group_create",
 *   "operationId": "...",
 *   "name": "Group Name",
 *   "password": null,
 *   "groupType": "ISOLATED"
 * }
 * </pre>
 */
public final class GroupCreatePacket implements Packet {

    @Override
    public String getType() {
        return "group_create";
    }

    @Override
    public void handle(JettyWebSocket socket, JSONObject json) {
        String operationId = json.optString("operationId", null);
        if (!GroupPacketSupport.requireReady(socket, operationId)) {
            SvgCore.getLogger().info(String.format(
                    "group_create_rejected_not_ready operationId=%s",
                    operationId == null ? "?" : operationId
            ));
            return;
        }

        SvgConnection connection = socket.getConnection();
        long revision = GroupPacketSupport.groups().getRevision();

        RateLimitResult createLimit = SvgCore.getRateLimitService()
                .tryGroupCreate(GroupPacketSupport.connectionKey(connection));
        if (!createLimit.allowed()) {
            GroupPacketSupport.rejectRateLimit(connection, operationId, createLimit, revision);
            return;
        }

        if (!GroupPacketSupport.registerMutation(connection, operationId)) {
            GroupPacketSupport.rejectDuplicate(connection, operationId, revision);
            return;
        }

        String name = json.optString("name", "").trim();

        String password = null;
        if (json.has("password") && !json.isNull("password")) {
            password = json.optString("password", null);
            if (password != null && password.isBlank()) {
                password = null;
            }
        }

        // Prefer groupType; never treat packet discriminator `type` as the SVC group kind.
        String groupTypeRaw = json.optString("groupType", "");
        if (groupTypeRaw == null || groupTypeRaw.isBlank()) {
            // Legacy clients mistakenly reused `type` for the group kind — ignore "group_create".
            String legacy = json.optString("type", "");
            if (legacy != null && !legacy.isBlank() && !"group_create".equalsIgnoreCase(legacy)) {
                groupTypeRaw = legacy;
            } else {
                groupTypeRaw = "ISOLATED";
            }
        }
        Group.Type type = GroupPacketSupport.parseType(groupTypeRaw);
        boolean persistent = json.optBoolean("persistent", false);

        java.util.UUID playerId = connection.getPlayer() != null
                ? connection.getPlayer().getUniqueId()
                : null;
        SvgCore.getLogger().info(String.format(
                "group_create_received operationId=%s player=%s type=%s",
                operationId == null ? "?" : operationId,
                playerId == null ? "?" : playerId,
                type
        ));

        GroupManager.OpResult result = GroupPacketSupport.groups().createGroupDetailed(
                connection.getPlayer(),
                name,
                password,
                type,
                persistent,
                true,
                true
        );

        if (!result.success()) {
            String reason = sanitizeReason(result.error());
            SvgCore.getLogger().info(String.format(
                    "group_create_validation_failed operationId=%s reason=%s partial=%s",
                    operationId == null ? "?" : operationId,
                    reason,
                    result.partial()
            ));
        } else {
            SvgCore.getRateLimitService().markGroupCreate(GroupPacketSupport.connectionKey(connection));
            SvgCore.getLogger().info(String.format(
                    "group_create_confirmed operationId=%s groupId=%s joined=%s revision=%s",
                    operationId == null ? "?" : operationId,
                    result.groupId() == null ? "?" : result.groupId(),
                    result.joined(),
                    result.revision()
            ));
        }

        GroupPacketSupport.sendOperationResult(connection, operationId, result, "group_create");
    }

    private static String sanitizeReason(String error) {
        if (error == null || error.isBlank()) {
            return "unknown";
        }
        String m = error.toLowerCase(java.util.Locale.ROOT);
        if (m.contains("permission")) return "permission";
        if (m.contains("disabled")) return "disabled";
        if (m.contains("cooldown") || m.contains("wait")) return "cooldown";
        if (m.contains("limit") || m.contains("maximum") || m.contains("too many")) return "limit";
        if (m.contains("name")) return "name";
        if (m.contains("type")) return "type";
        if (m.contains("join failed") || m.contains("assign")) return "assign";
        if (m.contains("exist")) return "exists";
        if (m.contains("unavailable")) return "unavailable";
        return "server";
    }
}
