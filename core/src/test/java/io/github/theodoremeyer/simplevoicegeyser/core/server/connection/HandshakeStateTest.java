package io.github.theodoremeyer.simplevoicegeyser.core.server.connection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandshakeStateTest {

    @Test
    void allowsHappyPathTransitions() {
        assertTrue(HandshakeState.OPEN.canTransitionTo(HandshakeState.JOIN_RECEIVED));
        assertTrue(HandshakeState.JOIN_RECEIVED.canTransitionTo(HandshakeState.COMPATIBILITY_ACCEPTED));
        assertTrue(HandshakeState.COMPATIBILITY_ACCEPTED.canTransitionTo(HandshakeState.AUTHENTICATING));
        assertTrue(HandshakeState.AUTHENTICATING.canTransitionTo(HandshakeState.AUTHENTICATED));
        assertTrue(HandshakeState.AUTHENTICATED.canTransitionTo(HandshakeState.READY));
        assertTrue(HandshakeState.READY.canTransitionTo(HandshakeState.CLOSED));
    }

    @Test
    void rejectsRegressionFromTerminalStates() {
        assertFalse(HandshakeState.CLOSED.canTransitionTo(HandshakeState.READY));
        assertFalse(HandshakeState.FAILED.canTransitionTo(HandshakeState.AUTHENTICATED));
        assertFalse(HandshakeState.READY.canTransitionTo(HandshakeState.AUTHENTICATING));
        assertFalse(HandshakeState.AUTHENTICATED.canTransitionTo(HandshakeState.JOIN_RECEIVED));
    }
}
