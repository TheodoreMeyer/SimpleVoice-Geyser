package io.github.theodoremeyer.simplevoicegeyser.core.managers.group;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.managers.GroupManager;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.SvgConnection;
import io.github.theodoremeyer.simplevoicegeyser.core.server.ratelimit.RateLimitResult;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Broadcasts group directory snapshots and incremental updates to authenticated SVG sessions.
 */
public final class GroupSyncService {

    private final GroupManager groupManager;
    private final ConcurrentHashMap<UUID, RefreshState> refreshStates = new ConcurrentHashMap<>();

    /**
     * @param groupManager group manager
     */
    public GroupSyncService(GroupManager groupManager) {
        this.groupManager = groupManager;
    }

    /**
     * Send session mode + full snapshot after READY.
     *
     * @param connection authenticated connection
     */
    public void sendReadyPayloads(SvgConnection connection) {
        if (connection == null || !connection.isAuthenticated()) {
            return;
        }
        sendSessionMode(connection);
        sendSnapshot(connection);
    }

    /**
     * Send {@code session_mode} to one connection.
     *
     * @param connection connection
     */
    public void sendSessionMode(SvgConnection connection) {
        JSONObject json = new JSONObject()
                .put("type", "session_mode")
                .put("mode", connection.getSessionMode().name());
        connection.sendJson(json);
    }

    /**
     * Send full {@code groups_snapshot} to one connection.
     *
     * @param connection connection
     */
    public void sendSnapshot(SvgConnection connection) {
        if (connection == null || !connection.isAuthenticated()) {
            return;
        }
        GroupSnapshot snapshot = groupManager.snapshotVisible(connection.getUuid());
        JSONObject json = snapshot.toJson().put("type", "groups_snapshot");
        json.put("allowWebCreation", Boolean.TRUE.equals(
                SvgCore.getConfig().GROUPS_ALLOW_WEB_CREATION.get()
        ));
        connection.sendJson(json);
    }

    /**
     * Handle a rate-limited, coalesced groups refresh request.
     *
     * @param connection authenticated connection
     * @param operationId client operation id
     */
    public void handleRefresh(SvgConnection connection, String operationId) {
        if (connection == null || connection.getUuid() == null) {
            return;
        }

        UUID connectionId = connection.getUuid();
        RefreshState state = refreshStates.computeIfAbsent(connectionId, ignored -> new RefreshState());
        List<String> batch = new ArrayList<>();

        synchronized (state) {
            if (operationId != null && !operationId.isBlank()) {
                state.pendingOperationIds.add(operationId);
            }

            if (state.inFlight) {
                return;
            }

            RateLimitResult limit = SvgCore.getRateLimitService()
                    .tryGroupsRefresh(connectionId.toString());
            if (!limit.allowed()) {
                if (operationId != null && !operationId.isBlank()) {
                    state.pendingOperationIds.remove(operationId);
                    sendRateLimited(connection, operationId, limit.retryAfterMs());
                }
                return;
            }

            state.inFlight = true;
            batch.addAll(state.pendingOperationIds);
            state.pendingOperationIds.clear();
        }

        try {
            // Authoritative SVC reconcile so Java-created groups appear even when revision is unchanged.
            groupManager.reconcilePlayerState(connectionId, false);
            SvgCore.getRateLimitService().markGroupsRefresh(connectionId.toString());
            sendSnapshot(connection);
            long revision = groupManager.getRevision();
            for (String opId : batch) {
                sendOperationSuccess(connection, opId, revision);
            }
        } finally {
            synchronized (state) {
                state.inFlight = false;
                if (!state.pendingOperationIds.isEmpty()) {
                    // Another refresh arrived while this one was running — serve it immediately.
                    handleRefresh(connection, null);
                }
            }
        }
    }

    /**
     * Broadcast full snapshots to all authenticated connections.
     */
    public void broadcastSnapshots() {
        for (SvgConnection connection : SvgCore.getConnectionManager().getAuthenticatedConnections()) {
            sendSnapshot(connection);
        }
        SvgCore.getLogger().info(
                "group_snapshot_published revision=" + groupManager.getRevision()
        );
    }

    /**
     * Publish group_created incremental event and refresh snapshots.
     *
     * @param groupId group id string
     * @param name group name
     */
    public void publishCreated(String groupId, String name) {
        JSONObject json = new JSONObject()
                .put("type", "group_created")
                .put("revision", groupManager.getRevision())
                .put("groupId", groupId)
                .put("name", name);
        broadcast(json);
    }

    /**
     * Publish group_removed incremental event.
     *
     * @param groupId group id string
     */
    public void publishRemoved(String groupId) {
        JSONObject json = new JSONObject()
                .put("type", "group_removed")
                .put("revision", groupManager.getRevision())
                .put("groupId", groupId);
        broadcast(json);
    }

    /**
     * Publish membership_changed incremental event.
     *
     * @param groupId group id (nullable on leave-all)
     * @param playerId player uuid
     * @param joined whether joined
     */
    public void publishMembershipChanged(String groupId, String playerId, boolean joined) {
        JSONObject json = new JSONObject()
                .put("type", "membership_changed")
                .put("revision", groupManager.getRevision())
                .put("groupId", groupId)
                .put("playerId", playerId)
                .put("joined", joined);
        broadcast(json);
    }

    private void broadcast(JSONObject json) {
        for (SvgConnection connection : SvgCore.getConnectionManager().getAuthenticatedConnections()) {
            connection.sendJson(json);
        }
    }

    private static void sendOperationSuccess(SvgConnection connection, String operationId, long revision) {
        JSONObject json = new JSONObject()
                .put("type", "operation_result")
                .put("operationId", operationId == null ? JSONObject.NULL : operationId)
                .put("success", true)
                .put("revision", revision);
        connection.sendJson(json);
    }

    private static void sendRateLimited(SvgConnection connection, String operationId, long retryAfterMs) {
        JSONObject json = new JSONObject()
                .put("type", "operation_result")
                .put("operationId", operationId == null ? JSONObject.NULL : operationId)
                .put("success", false)
                .put("errorCode", "RATE_LIMITED")
                .put("message", "Please wait before trying again.")
                .put("retryAfterMs", Math.max(1L, retryAfterMs))
                .put("revision", SvgCore.getGroupManager().getRevision());
        connection.sendJson(json);
    }

    private static final class RefreshState {
        private boolean inFlight;
        private final List<String> pendingOperationIds = new ArrayList<>();
    }
}
