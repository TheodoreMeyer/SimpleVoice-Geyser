package io.github.theodoremeyer.simplevoicegeyser.core.server.connection;

/**
 * Authoritative WebSocket handshake lifecycle for a single browser session.
 * Transitions are monotonic: once {@link #CLOSED} or {@link #FAILED}, no further
 * active states are allowed.
 */
public enum HandshakeState {
    OPEN,
    JOIN_RECEIVED,
    COMPATIBILITY_ACCEPTED,
    AUTHENTICATING,
    AUTHENTICATED,
    READY,
    CLOSING,
    CLOSED,
    FAILED;

    /**
     * @param next candidate next state
     * @return whether moving to {@code next} is allowed
     */
    public boolean canTransitionTo(HandshakeState next) {
        if (next == null || next == this) {
            return false;
        }
        if (this == CLOSED || this == FAILED) {
            return false;
        }
        if (this == CLOSING) {
            return next == CLOSED || next == FAILED;
        }
        return switch (this) {
            case OPEN -> next == JOIN_RECEIVED || next == CLOSING || next == CLOSED || next == FAILED;
            case JOIN_RECEIVED -> next == COMPATIBILITY_ACCEPTED || next == CLOSING || next == CLOSED || next == FAILED;
            case COMPATIBILITY_ACCEPTED -> next == AUTHENTICATING || next == CLOSING || next == CLOSED || next == FAILED;
            case AUTHENTICATING -> next == AUTHENTICATED || next == CLOSING || next == CLOSED || next == FAILED;
            case AUTHENTICATED -> next == READY || next == CLOSING || next == CLOSED || next == FAILED;
            case READY -> next == CLOSING || next == CLOSED || next == FAILED;
            default -> false;
        };
    }
}
