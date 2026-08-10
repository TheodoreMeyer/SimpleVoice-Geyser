package io.github.theodoremeyer.simplevoicegeyser.core.server.connection;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Debug-friendly handshake transition helper. Never logs secrets.
 */
public final class HandshakeTracer {

    private final String correlationId;
    private final AtomicReference<HandshakeState> state = new AtomicReference<>(HandshakeState.OPEN);

    /**
     * @param correlationId opaque session correlation id
     */
    public HandshakeTracer(String correlationId) {
        this.correlationId = correlationId == null ? "unknown" : correlationId;
    }

    /**
     * @return current handshake state
     */
    public HandshakeState getState() {
        return state.get();
    }

    /**
     * @return correlation id
     */
    public String getCorrelationId() {
        return correlationId;
    }

    /**
     * Attempt a state transition and log it.
     * @param next next state
     * @param trigger packet or callback name
     * @param playerUuid player uuid when known
     * @param socketOpen whether the websocket is open
     * @param replaced whether this session was replaced
     * @return true when the transition was applied
     */
    public boolean transition(
            HandshakeState next,
            String trigger,
            UUID playerUuid,
            boolean socketOpen,
            boolean replaced
    ) {
        HandshakeState previous = state.get();
        if (!previous.canTransitionTo(next)) {
            SvgCore.getLogger().debug(
                    "Handshake: rejected transition"
                            + " corr=" + correlationId
                            + " from=" + previous
                            + " to=" + next
                            + " trigger=" + trigger
                            + " thread=" + Thread.currentThread().getName()
                            + " uuid=" + playerUuid
                            + " socketOpen=" + socketOpen
                            + " replaced=" + replaced
                            + " scheduler=" + schedulerSummary()
            );
            return false;
        }

        if (!state.compareAndSet(previous, next)) {
            SvgCore.getLogger().debug(
                    "Handshake: lost race applying transition"
                            + " corr=" + correlationId
                            + " expectedFrom=" + previous
                            + " to=" + next
                            + " trigger=" + trigger
            );
            return false;
        }

        SvgCore.getLogger().debug(
                "Handshake: transition"
                        + " corr=" + correlationId
                        + " from=" + previous
                        + " to=" + next
                        + " trigger=" + trigger
                        + " thread=" + Thread.currentThread().getName()
                        + " uuid=" + playerUuid
                        + " socketOpen=" + socketOpen
                        + " replaced=" + replaced
                        + " scheduler=" + schedulerSummary()
        );
        return true;
    }

    /**
     * Force a terminal failure state when allowed.
     */
    public void fail(String trigger, UUID playerUuid, boolean socketOpen, Throwable error) {
        HandshakeState previous = state.get();
        if (previous == HandshakeState.CLOSED || previous == HandshakeState.FAILED) {
            return;
        }
        state.set(HandshakeState.FAILED);
        if (error != null) {
            SvgCore.getLogger().debug(
                    "Handshake: failed"
                            + " corr=" + correlationId
                            + " from=" + previous
                            + " trigger=" + trigger
                            + " thread=" + Thread.currentThread().getName()
                            + " uuid=" + playerUuid
                            + " socketOpen=" + socketOpen
                            + " scheduler=" + schedulerSummary(),
                    error
            );
        } else {
            SvgCore.getLogger().debug(
                    "Handshake: failed"
                            + " corr=" + correlationId
                            + " from=" + previous
                            + " trigger=" + trigger
                            + " thread=" + Thread.currentThread().getName()
                            + " uuid=" + playerUuid
                            + " socketOpen=" + socketOpen
                            + " scheduler=" + schedulerSummary()
            );
        }
    }

    private static String schedulerSummary() {
        try {
            return SvgCore.getTaskScheduler().getClass().getSimpleName()
                    + "(regionThreaded=" + SvgCore.getTaskScheduler().isRegionThreaded()
                    + ",accepting=" + SvgCore.getTaskScheduler().isAcceptingTasks() + ")";
        } catch (Exception e) {
            return "unavailable";
        }
    }
}
