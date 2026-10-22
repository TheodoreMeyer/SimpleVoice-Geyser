package io.github.theodoremeyer.simplevoicegeyser.core.server.connection.auth;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.ConnectionStates;
import io.github.theodoremeyer.simplevoicegeyser.core.server.servlets.JettyWebSocket;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.jetty.websocket.api.Session;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Waits for a password-authenticated client until the player is online
 * and Simple Voice Chat has a usable connection (or the wait expires).
 */
public final class PendingJoinWaiter {

    private static final long POLL_MS = 250L;

    private final ScheduledExecutorService executor;
    private final ConcurrentHashMap<UUID, PendingEntry> pending = new ConcurrentHashMap<>();

    public PendingJoinWaiter() {
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "svg-pending-join");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Result of a pending wait.
     *
     * @param uuid player uuid
     * @param player online player when ready
     * @param failureMessage non-null when wait ended with a hard reject (e.g. SVC mod installed)
     */
    public record ReadyResult(UUID uuid, SvgPlayer player, String failureMessage) {
        public static ReadyResult ready(UUID uuid, SvgPlayer player) {
            return new ReadyResult(uuid, player, null);
        }

        public static ReadyResult rejected(UUID uuid, String message) {
            return new ReadyResult(uuid, null, message);
        }

        public boolean rejected() {
            return failureMessage != null;
        }
    }

    /**
     * Starts waiting for the player/SVC to become ready.
     * Replaces any previous pending wait for the same UUID.
     *
     * @param uuid player uuid
     * @param timeout max wait
     * @param socket websocket that owns this wait
     * @return future completed with ready/rejected, or completed exceptionally on timeout/cancel
     */
    public CompletableFuture<ReadyResult> waitForReady(
            UUID uuid,
            Duration timeout,
            JettyWebSocket socket
    ) {
        CompletableFuture<ReadyResult> future = new CompletableFuture<>();
        PendingEntry entry = new PendingEntry(socket, future);

        PendingEntry previous = pending.put(uuid, entry);
        if (previous != null) {
            previous.cancel("replaced");
            closeReplacedSocket(previous.socket, socket);
        }

        ReadyResult immediate = tryResolve(uuid);
        if (immediate != null) {
            complete(uuid, entry, immediate);
            return future;
        }

        entry.pollTask = executor.scheduleAtFixedRate(
                () -> {
                    try {
                        ReadyResult resolved = tryResolve(uuid);
                        if (resolved != null) {
                            complete(uuid, entry, resolved);
                        }
                    } catch (Throwable t) {
                        // scheduleAtFixedRate suppresses later runs after an uncaught exception
                        SvgCore.getLogger().debug(
                                "PendingJoinWaiter: poll error for " + uuid,
                                t
                        );
                    }
                },
                POLL_MS,
                POLL_MS,
                TimeUnit.MILLISECONDS
        );

        long timeoutMs = Math.max(1L, timeout.toMillis());
        entry.timeoutTask = executor.schedule(
                () -> {
                    if (pending.remove(uuid, entry)) {
                        entry.stopTasks();
                        future.completeExceptionally(new JoinWaitExpiredException());
                    }
                },
                timeoutMs,
                TimeUnit.MILLISECONDS
        );

        return future;
    }

    /**
     * Called when a player enters {@link io.github.theodoremeyer.simplevoicegeyser.core.managers.PlayerManager}.
     *
     * @param uuid joined player
     */
    public void notifyPlayerJoined(UUID uuid) {
        PendingEntry entry = pending.get(uuid);
        if (entry != null) {
            ReadyResult resolved = tryResolve(uuid);
            if (resolved != null) {
                complete(uuid, entry, resolved);
            }
            return;
        }

        // Auth UUID may differ from Floodgate online UUID — match pending waits by name.
        SvgPlayer joined = SvgCore.getPlayerManager().getPlayer(uuid);
        if (joined == null) {
            return;
        }
        for (Map.Entry<UUID, PendingEntry> pendingEntry : pending.entrySet()) {
            UUID authUuid = pendingEntry.getKey();
            ReadyResult resolved = tryResolve(authUuid);
            if (resolved != null
                    && resolved.player() != null
                    && uuid.equals(resolved.player().getUniqueId())) {
                complete(authUuid, pendingEntry.getValue(), resolved);
                return;
            }
        }
    }

    /**
     * Cancels a pending wait for a websocket session.
     *
     * @param uuid pending uuid
     * @param socket session that owns the wait
     */
    public void cancelForSocket(UUID uuid, JettyWebSocket socket) {
        if (uuid == null || socket == null) {
            return;
        }
        PendingEntry entry = pending.get(uuid);
        if (entry != null && entry.socket == socket) {
            pending.remove(uuid, entry);
            entry.cancel("socket_closed");
        }
    }

    /**
     * Shuts down the waiter executor and cancels all pending waits.
     */
    public void shutdown() {
        for (UUID uuid : pending.keySet()) {
            PendingEntry entry = pending.remove(uuid);
            if (entry != null) {
                entry.cancel("shutdown");
            }
        }
        executor.shutdownNow();
    }

    private void complete(UUID uuid, PendingEntry entry, ReadyResult result) {
        if (!pending.remove(uuid, entry)) {
            return;
        }
        entry.stopTasks();
        entry.future.complete(result);
    }

    private static void closeReplacedSocket(JettyWebSocket previous, JettyWebSocket current) {
        if (previous == null || previous == current) {
            return;
        }
        Session session = previous.getSession();
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            session.close(
                    ConnectionStates.DisconnectCodes.REPLACED.getCode(),
                    "replaced"
            );
        } catch (Exception ignored) {
        }
    }

    private ReadyResult tryResolve(UUID authUuid) {
        try {
            SvgPlayer player = resolveOnlinePlayer(authUuid);
            if (player == null) {
                return null;
            }

            // Always use the live online UUID for SVC (may differ from accounts.json).
            UUID uuid = player.getUniqueId();
            if (!uuid.equals(authUuid)) {
                SvgCore.getLogger().debug(
                        "[PendingJoin] Using online UUID " + uuid
                                + " for auth account " + authUuid
                                + " (" + player.getName() + ")"
                );
            }

            if (!player.hasPermission("svg.vc.join")) {
                return ReadyResult.rejected(
                        uuid,
                        "Access Denied. You may have been banned from vc."
                );
            }

            VoicechatServerApi api = SvgCore.getBridge() == null
                    ? null
                    : SvgCore.getBridge().getVcServerApi();
            if (api == null) {
                return ReadyResult.rejected(uuid, "Voice chat server unavailable.");
            }

            VoicechatConnection connection = api.getConnectionOf(uuid);
            if (connection == null) {
                // Player online but SVC not ready yet — keep waiting.
                return null;
            }

            if (connection.isInstalled()) {
                return ReadyResult.rejected(
                        uuid,
                        "Access Denied: Please remove the Simple Voice Chat mod."
                );
            }

            return ReadyResult.ready(uuid, player);
        } catch (Throwable t) {
            SvgCore.getLogger().debug("PendingJoinWaiter: tryResolve failed for " + authUuid, t);
            return null;
        }
    }

    /**
     * Resolve the online player for an auth UUID, including Floodgate name fallback
     * when accounts.json still points at an old UUID.
     */
    @Nullable
    private static SvgPlayer resolveOnlinePlayer(UUID authUuid) {
        SvgPlayer player = SvgCore.getPlayerManager().getPlayer(authUuid);
        if (player != null) {
            return player;
        }
        String storedName = SvgCore.getPasswordManager().getUsername(authUuid);
        if (storedName == null || storedName.isBlank()) {
            return null;
        }
        return SvgCore.getPlayerManager().findOnlineByUsername(storedName);
    }

    /**
     * Thrown when the Minecraft join wait expires.
     */
    public static final class JoinWaitExpiredException extends Exception {
        public JoinWaitExpiredException() {
            super("join_wait_expired");
        }
    }

    private static final class PendingEntry {
        private final JettyWebSocket socket;
        private final CompletableFuture<ReadyResult> future;
        private volatile ScheduledFuture<?> pollTask;
        private volatile ScheduledFuture<?> timeoutTask;

        private PendingEntry(
                JettyWebSocket socket,
                CompletableFuture<ReadyResult> future
        ) {
            this.socket = socket;
            this.future = future;
        }

        private void stopTasks() {
            ScheduledFuture<?> poll = pollTask;
            if (poll != null) {
                poll.cancel(false);
            }
            ScheduledFuture<?> timeout = timeoutTask;
            if (timeout != null) {
                timeout.cancel(false);
            }
        }

        private void cancel(String reason) {
            stopTasks();
            if (!future.isDone()) {
                future.completeExceptionally(new CancellationException(reason));
            }
        }
    }

    private static final class CancellationException extends Exception {
        private CancellationException(String reason) {
            super(reason);
        }
    }
}
