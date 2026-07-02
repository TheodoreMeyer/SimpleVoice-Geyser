package io.github.theodoremeyer.simplevoicegeyser.core.server.connection;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class VoicePacketForwarderTest {

    @Test
    void forwardsJavaSenderPacketToAuthenticatedSvgReceiver() {
        Object packet = new Object();
        UUID sender = UUID.randomUUID();
        FakeReceiver receiver = new FakeReceiver(UUID.randomUUID(), true, false, true);

        VoicePacketForwarder.Result result = VoicePacketForwarder.forward(sender, packet, List.of(receiver));

        assertEquals(1, result.forwarded());
        assertEquals(0, result.skippedSelf());
        assertSame(packet, receiver.received.getFirst());
    }

    @Test
    void skipsSameSenderReceiverToPreventSelfEcho() {
        Object packet = new Object();
        UUID sender = UUID.randomUUID();
        FakeReceiver receiver = new FakeReceiver(sender, true, false, true);

        VoicePacketForwarder.Result result = VoicePacketForwarder.forward(sender, packet, List.of(receiver));

        assertEquals(0, result.forwarded());
        assertEquals(1, result.skippedSelf());
        assertEquals(0, receiver.received.size());
    }

    @Test
    void ignoresNullSender() {
        FakeReceiver receiver = new FakeReceiver(UUID.randomUUID(), true, false, true);

        VoicePacketForwarder.Result result = VoicePacketForwarder.forward(null, new Object(), List.of(receiver));

        assertEquals(0, result.forwarded());
        assertEquals(1, result.skippedNullSender());
        assertEquals(0, receiver.received.size());
    }

    @Test
    void forwardsToMultipleReceiversExceptSender() {
        Object packet = new Object();
        UUID sender = UUID.randomUUID();
        FakeReceiver senderReceiver = new FakeReceiver(sender, true, false, true);
        FakeReceiver receiverA = new FakeReceiver(UUID.randomUUID(), true, false, true);
        FakeReceiver receiverB = new FakeReceiver(UUID.randomUUID(), true, false, true);

        VoicePacketForwarder.Result result = VoicePacketForwarder.forward(
                sender,
                packet,
                List.of(senderReceiver, receiverA, receiverB)
        );

        assertEquals(2, result.forwarded());
        assertEquals(1, result.skippedSelf());
        assertEquals(0, senderReceiver.received.size());
        assertSame(packet, receiverA.received.getFirst());
        assertSame(packet, receiverB.received.getFirst());
    }

    @Test
    void skipsInactiveAndMissingListenerReceivers() {
        Object packet = new Object();
        UUID sender = UUID.randomUUID();
        FakeReceiver inactive = new FakeReceiver(UUID.randomUUID(), false, false, true);
        FakeReceiver closed = new FakeReceiver(UUID.randomUUID(), true, true, true);
        FakeReceiver missingListener = new FakeReceiver(UUID.randomUUID(), true, false, false);

        VoicePacketForwarder.Result result = VoicePacketForwarder.forward(
                sender,
                packet,
                List.of(inactive, closed, missingListener)
        );

        assertEquals(0, result.forwarded());
        assertEquals(2, result.skippedInactive());
        assertEquals(1, result.skippedMissingListener());
    }

    private static final class FakeReceiver implements VoicePacketForwarder.Receiver<Object> {
        private final UUID uuid;
        private final boolean authenticated;
        private final boolean closed;
        private final boolean canReceive;
        private final List<Object> received = new ArrayList<>();

        private FakeReceiver(UUID uuid, boolean authenticated, boolean closed, boolean canReceive) {
            this.uuid = uuid;
            this.authenticated = authenticated;
            this.closed = closed;
            this.canReceive = canReceive;
        }

        @Override
        public UUID uuid() {
            return uuid;
        }

        @Override
        public boolean authenticated() {
            return authenticated;
        }

        @Override
        public boolean closed() {
            return closed;
        }

        @Override
        public boolean canReceiveVoicePacket() {
            return canReceive;
        }

        @Override
        public void receive(Object packet) {
            received.add(packet);
        }
    }
}
