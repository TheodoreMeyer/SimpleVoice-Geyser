package io.github.theodoremeyer.simplevoicegeyser.core.server.packets;

import de.maxhenkel.voicechat.api.Group;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.managers.GroupManager;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.SvgConnection;
import io.github.theodoremeyer.simplevoicegeyser.core.server.ratelimit.RateLimitResult;
import io.github.theodoremeyer.simplevoicegeyser.core.server.servlets.JettyWebSocket;
import org.json.JSONObject;

import java.util.Locale;
import java.util.UUID;

/**
 * Shared helpers for authenticated, READY group websocket operations.
 */
final class GroupPacketSupport {

    private GroupPacketSupport() {}

    /**
     * Require an authenticated READY session. When {@code operationId} is present,
     * failures return {@code operation_result} so the browser can clear pending UI state.
     *
     * @param socket websocket
     * @param operationId client operation id (nullable)
     * @return true when ready
     */
    static boolean requireReady(JettyWebSocket socket, String operationId) {
        SvgConnection connection = socket.getConnection();
        if (connection != null && socket.isReady()) {
            return true;
        }

        String message = "Voice chat is not ready yet.";
        if (connection != null) {
            long revision = 0L;
            try {
                revision = groups().getRevision();
            } catch (RuntimeException ignored) {
                // Group manager may be unavailable during shutdown.
            }
            sendOperationResult(connection, operationId, false, message, revision);
        } else {
            socket.sendRaw(
                    io.github.theodoremeyer.simplevoicegeyser.core.server.connection.ConnectionStates.MessageType.ERROR,
                    message,
                    false
            );
        }
        return false;
    }

    /**
     * @param socket websocket
     * @return true when ready
     */
    static boolean requireReady(JettyWebSocket socket) {
        return requireReady(socket, null);
    }

    static void sendOperationResult(
            SvgConnection connection,
            String operationId,
            boolean success,
            String error,
            long revision
    ) {
        sendOperationResult(connection, operationId, success, error, revision, null, null);
    }

    static void sendOperationResult(
            SvgConnection connection,
            String operationId,
            GroupManager.OpResult result,
            String operation
    ) {
        if (result == null) {
            sendOperationResult(connection, operationId, false, "Unknown error.", 0L, null, operation);
            return;
        }
        sendOperationResult(
                connection,
                operationId,
                result.success(),
                result.error(),
                result.revision(),
                result,
                operation
        );
    }

    static void sendOperationResult(
            SvgConnection connection,
            String operationId,
            boolean success,
            String error,
            long revision,
            GroupManager.OpResult result,
            String operation
    ) {
        if (connection == null) {
            return;
        }

        JSONObject json = new JSONObject()
                .put("type", "operation_result")
                .put("operationId", operationId == null ? JSONObject.NULL : operationId)
                .put("success", success)
                .put("revision", revision);

        if (operation != null && !operation.isBlank()) {
            json.put("operation", operation);
        }

        if (!success && error != null) {
            json.put("error", error);
        }

        if (result != null) {
            if (result.partial()) {
                json.put("partial", true);
            }
            if (result.created() != null) {
                json.put("created", result.created());
            }
            if (result.errorCode() != null && !result.errorCode().isBlank()) {
                json.put("errorCode", result.errorCode());
            }
            if (result.groupId() != null) {
                json.put("groupId", result.groupId().toString());
            }
            if (result.joined() != null) {
                json.put("joined", result.joined());
            }
            if (result.left() != null) {
                json.put("left", result.left());
            }
            if (result.previousGroupId() != null) {
                json.put("previousGroupId", result.previousGroupId().toString());
            }
            if (result.currentGroupId() != null) {
                json.put("currentGroupId", result.currentGroupId().toString());
            } else {
                json.put("currentGroupId", JSONObject.NULL);
            }
            json.put("membershipRevision", groups().getMembershipRevision());
        }

        connection.sendJson(json);
    }

    static void sendRateLimitedResult(
            SvgConnection connection,
            String operationId,
            long retryAfterMs,
            long revision
    ) {
        if (connection == null) {
            return;
        }

        JSONObject json = new JSONObject()
                .put("type", "operation_result")
                .put("operationId", operationId == null ? JSONObject.NULL : operationId)
                .put("success", false)
                .put("errorCode", "RATE_LIMITED")
                .put("message", "Please wait before trying again.")
                .put("retryAfterMs", Math.max(1L, retryAfterMs))
                .put("revision", revision);

        connection.sendJson(json);
    }

    static String connectionKey(SvgConnection connection) {
        if (connection == null || connection.getUuid() == null) {
            return "unknown";
        }
        return connection.getUuid().toString();
    }

    static boolean registerMutation(SvgConnection connection, String operationId) {
        if (operationId == null || operationId.isBlank()) {
            return true;
        }
        return SvgCore.getRateLimitService().registerMutation(connectionKey(connection), operationId);
    }

    static void rejectDuplicate(SvgConnection connection, String operationId, long revision) {
        sendOperationResult(
                connection,
                operationId,
                false,
                "Duplicate operation.",
                revision
        );
    }

    static void rejectRateLimit(
            SvgConnection connection,
            String operationId,
            RateLimitResult limit,
            long revision
    ) {
        sendRateLimitedResult(connection, operationId, limit.retryAfterMs(), revision);
    }

    static Group.Type parseType(String raw) {
        if (raw == null || raw.isBlank()) {
            return de.maxhenkel.voicechat.api.Group.Type.ISOLATED;
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "ISOLATED" -> de.maxhenkel.voicechat.api.Group.Type.ISOLATED;
            case "OPEN" -> de.maxhenkel.voicechat.api.Group.Type.OPEN;
            default -> de.maxhenkel.voicechat.api.Group.Type.NORMAL;
        };
    }

    static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static GroupManager groups() {
        return SvgCore.getGroupManager();
    }

    static boolean allowWebCreation() {
        return Boolean.TRUE.equals(SvgCore.getConfig().GROUPS_ALLOW_WEB_CREATION.get());
    }
}
