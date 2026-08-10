package io.github.theodoremeyer.simplevoicegeyser.core.audio;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

/**
 * Thread-safe fail-closed transmit gate for outbound web-voice frames.
 * <p>
 * Closing the gate clears any queued PCM before asynchronous cleanup continues.
 */
public final class VoiceTransmitGate {

    private static final long DROP_SUMMARY_EVERY = 200L;

    private final UUID playerUuid;
    private final long sessionGeneration;
    private final AtomicReference<SessionVoiceMembership> membership;
    private final LongAdder droppedFrames = new LongAdder();
    private final AtomicLong lastDropSummaryAt = new AtomicLong(0);
    private volatile Consumer<Void> onGateClosed;

    /**
     * @param playerUuid player uuid
     * @param sessionGeneration session generation
     */
    public VoiceTransmitGate(UUID playerUuid, long sessionGeneration) {
        this.playerUuid = Objects.requireNonNull(playerUuid, "playerUuid");
        this.sessionGeneration = sessionGeneration;
        this.membership = new AtomicReference<>(
                SessionVoiceMembership.uncertain(playerUuid, sessionGeneration)
        );
    }

    /**
     * @return player uuid
     */
    public UUID getPlayerUuid() {
        return playerUuid;
    }

    /**
     * @return session generation this gate belongs to
     */
    public long getSessionGeneration() {
        return sessionGeneration;
    }

    /**
     * @return current membership snapshot
     */
    public SessionVoiceMembership getMembership() {
        return membership.get();
    }

    /**
     * Register a callback invoked when the gate is closed (leave / removal).
     * Used to clear the outbound queue before further cleanup.
     *
     * @param callback callback
     */
    public void setOnGateClosed(Consumer<Void> callback) {
        this.onGateClosed = callback;
    }

    /**
     * Apply membership when it belongs to this session generation.
     * Stale membership from a replaced session is ignored (fail-closed).
     *
     * @param next next membership
     * @return true when applied
     */
    public boolean applyMembership(SessionVoiceMembership next) {
        if (next == null) {
            return false;
        }
        if (!playerUuid.equals(next.playerUuid()) || next.sessionGeneration() != sessionGeneration) {
            return false;
        }
        SessionVoiceMembership previous = membership.getAndSet(next);
        if (previous.allowsTransmit() && !next.allowsTransmit()) {
            notifyClosed();
        }
        return true;
    }

    /**
     * Immediately block transmit and clear queued frames.
     *
     * @param membershipRevision revision to associate with the closed state
     */
    public void closeTransmit(long membershipRevision) {
        membership.set(SessionVoiceMembership.none(playerUuid, sessionGeneration, membershipRevision));
        notifyClosed();
    }

    /**
     * Mark membership uncertain (stale) — transmit stays blocked.
     */
    public void markUncertain() {
        SessionVoiceMembership previous = membership.getAndSet(
                SessionVoiceMembership.uncertain(playerUuid, sessionGeneration)
        );
        if (previous.allowsTransmit()) {
            notifyClosed();
        }
    }

    /**
     * @return whether encode/send is permitted
     */
    public boolean allowsTransmit() {
        SessionVoiceMembership current = membership.get();
        return current != null && current.allowsTransmit();
    }

    /**
     * Record a dropped frame due to the privacy gate. Emits periodic debug summaries only.
     */
    public void onDroppedFrame() {
        droppedFrames.increment();
        long dropped = droppedFrames.sum();
        long previous = lastDropSummaryAt.get();
        if (dropped - previous < DROP_SUMMARY_EVERY) {
            return;
        }
        if (!lastDropSummaryAt.compareAndSet(previous, dropped)) {
            return;
        }
        try {
            SessionVoiceMembership current = membership.get();
            SvgCore.getLogger().debug(
                    "VoiceTransmitGate: droppedFrames=" + dropped
                            + " uuid=" + playerUuid
                            + " gen=" + sessionGeneration
                            + " group=" + (current == null || current.groupUuid() == null
                            ? "none"
                            : current.groupUuid())
                            + " confirmed=" + (current != null && current.confirmed())
            );
        } catch (Exception ignored) {
        }
    }

    /**
     * @return dropped frame count (tests)
     */
    public long getDroppedFrames() {
        return droppedFrames.sum();
    }

    private void notifyClosed() {
        Consumer<Void> callback = onGateClosed;
        if (callback != null) {
            try {
                callback.accept(null);
            } catch (RuntimeException ignored) {
            }
        }
    }
}
