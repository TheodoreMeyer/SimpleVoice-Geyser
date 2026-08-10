package io.github.theodoremeyer.simplevoicegeyser.core.audio;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoicePrivacyGateTest {

    @Test
    void loginWithNoGroupKeepsOutboundAudioBlocked() {
        UUID player = UUID.randomUUID();
        VoiceTransmitGate gate = new VoiceTransmitGate(player, 1L);
        assertFalse(gate.allowsTransmit());
        gate.applyMembership(SessionVoiceMembership.none(player, 1L, 1L));
        assertFalse(gate.allowsTransmit());
    }

    @Test
    void pcmFramesWithNoGroupNeverReachOpusEncoding() {
        ControllableRunner runner = new ControllableRunner();
        FakeEncoder encoder = new FakeEncoder();
        RecordingSink sink = new RecordingSink();
        UUID player = UUID.randomUUID();
        VoiceTransmitGate gate = new VoiceTransmitGate(player, 7L);
        gate.applyMembership(SessionVoiceMembership.none(player, 7L, 1L));

        SessionAudioPipeline pipeline = new SessionAudioPipeline(
                player, encoder, sink, 8, runner, gate, 0L
        );

        assertFalse(pipeline.submit(frame(0, 0)));
        runner.drainAll();
        assertEquals(0, encoder.encodeCount.get());
        assertEquals(0, sink.packets.size());
        assertTrue(pipeline.getDiagnostics().getFramesPrivacyDropped() >= 1L);
        pipeline.close();
    }

    @Test
    void encodedFramesWithNoGroupNeverReachAudioSender() {
        ControllableRunner runner = new ControllableRunner();
        FakeEncoder encoder = new FakeEncoder();
        RecordingSink sink = new RecordingSink();
        UUID player = UUID.randomUUID();
        VoiceTransmitGate gate = new VoiceTransmitGate(player, 3L);
        gate.applyMembership(SessionVoiceMembership.joined(player, 3L, UUID.randomUUID(), 1L));

        SessionAudioPipeline pipeline = new SessionAudioPipeline(
                player, encoder, sink, 8, runner, gate, 0L
        );

        assertTrue(pipeline.submit(frame(0, 0)));
        // Close gate before drain so queued frame is dropped before encode/send.
        gate.closeTransmit(2L);
        runner.drainAll();

        assertEquals(0, sink.packets.size());
        assertEquals(0, encoder.encodeCount.get());
        pipeline.close();
    }

    @Test
    void confirmedJoinOpensAudioGate() {
        UUID player = UUID.randomUUID();
        UUID group = UUID.randomUUID();
        VoiceTransmitGate gate = new VoiceTransmitGate(player, 2L);
        gate.applyMembership(SessionVoiceMembership.none(player, 2L, 0L));
        assertFalse(gate.allowsTransmit());

        gate.applyMembership(SessionVoiceMembership.joined(player, 2L, group, 5L));
        assertTrue(gate.allowsTransmit());

        ControllableRunner runner = new ControllableRunner();
        FakeEncoder encoder = new FakeEncoder();
        RecordingSink sink = new RecordingSink();
        SessionAudioPipeline pipeline = new SessionAudioPipeline(
                player, encoder, sink, 8, runner, gate, 0L
        );
        assertTrue(pipeline.submit(frame(0, 0)));
        runner.drainAll();
        assertEquals(1, encoder.encodeCount.get());
        assertEquals(1, sink.packets.size());
        pipeline.close();
    }

    @Test
    void leaveClosesGateImmediatelyAndClearsQueue() {
        UUID player = UUID.randomUUID();
        VoiceTransmitGate gate = new VoiceTransmitGate(player, 4L);
        gate.applyMembership(SessionVoiceMembership.joined(player, 4L, UUID.randomUUID(), 1L));

        ControllableRunner runner = new ControllableRunner();
        FakeEncoder encoder = new FakeEncoder();
        RecordingSink sink = new RecordingSink();
        SessionAudioPipeline pipeline = new SessionAudioPipeline(
                player, encoder, sink, 8, runner, gate, 0L
        );

        assertTrue(pipeline.submit(frame(0, 0)));
        assertTrue(pipeline.submit(frame(0, 1)));
        gate.closeTransmit(2L);
        assertFalse(gate.allowsTransmit());
        assertFalse(pipeline.submit(frame(0, 2)));
        runner.drainAll();
        assertEquals(0, sink.packets.size());
        pipeline.close();
    }

    @Test
    void groupRemovalClosesGate() {
        UUID player = UUID.randomUUID();
        VoiceTransmitGate gate = new VoiceTransmitGate(player, 5L);
        gate.applyMembership(SessionVoiceMembership.joined(player, 5L, UUID.randomUUID(), 3L));
        assertTrue(gate.allowsTransmit());
        gate.closeTransmit(4L);
        assertFalse(gate.allowsTransmit());
    }

    @Test
    void uncertainMembershipStaysClosed() {
        UUID player = UUID.randomUUID();
        VoiceTransmitGate gate = new VoiceTransmitGate(player, 6L);
        assertFalse(gate.allowsTransmit());
        gate.markUncertain();
        assertFalse(gate.allowsTransmit());
    }

    @Test
    void oldSessionMembershipCannotOpenReplacementSessionGate() {
        UUID player = UUID.randomUUID();
        UUID group = UUID.randomUUID();
        VoiceTransmitGate replacement = new VoiceTransmitGate(player, 20L);
        replacement.applyMembership(SessionVoiceMembership.none(player, 20L, 1L));

        boolean applied = replacement.applyMembership(
                SessionVoiceMembership.joined(player, 19L, group, 9L)
        );
        assertFalse(applied);
        assertFalse(replacement.allowsTransmit());
    }

    @Test
    void nativeControllerModeStillRejectsBrowserAudioViaNullSenderContract() {
        // NATIVE_VOICE_CONTROLLER sessions never construct SvgAudioSender; Jetty rejects binary.
        // Membership gate also stays closed when SessionVoiceMembership.none is applied.
        UUID player = UUID.randomUUID();
        SessionVoiceMembership membership = SessionVoiceMembership.none(player, 1L, 0L);
        assertFalse(membership.allowsTransmit());
    }

    @Test
    void noPerFrameLogSpamUsesPeriodicDropCounter() {
        UUID player = UUID.randomUUID();
        VoiceTransmitGate gate = new VoiceTransmitGate(player, 8L);
        gate.applyMembership(SessionVoiceMembership.none(player, 8L, 0L));
        for (int i = 0; i < 50; i++) {
            gate.onDroppedFrame();
        }
        assertEquals(50L, gate.getDroppedFrames());
    }

    private static MicFrame frame(long generation, long sequence) {
        byte[] pcm = new byte[AudioFormatConstants.FRAME_BYTES];
        ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).putLong(sequence);
        return new MicFrame(
                AudioFormatConstants.PROTOCOL_VERSION,
                generation,
                sequence,
                AudioFormatConstants.SAMPLE_RATE,
                AudioFormatConstants.CHANNELS,
                AudioFormatConstants.PCM_FORMAT_PCM16LE,
                AudioFormatConstants.FRAME_SAMPLES,
                pcm,
                sequence,
                false
        );
    }

    private static final class ControllableRunner implements Consumer<Runnable> {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public synchronized void accept(Runnable runnable) {
            tasks.addLast(runnable);
        }

        void drainAll() {
            while (true) {
                Runnable next;
                synchronized (this) {
                    next = tasks.pollFirst();
                }
                if (next == null) {
                    return;
                }
                next.run();
            }
        }
    }

    private static final class FakeEncoder implements SessionAudioPipeline.Encoder {
        private final AtomicInteger encodeCount = new AtomicInteger();
        private final List<Long> encodedSequences = new ArrayList<>();

        @Override
        public byte[] encode(short[] pcm) {
            encodeCount.incrementAndGet();
            byte[] bytes = new byte[pcm.length * 2];
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcm);
            long sequence = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
            encodedSequences.add(sequence);
            return new byte[]{9, 9};
        }

        @Override
        public void resetState() {
        }

        @Override
        public void close() {
        }
    }

    private static final class RecordingSink implements SessionAudioPipeline.EncodedSink {
        private final ConcurrentLinkedQueue<byte[]> packets = new ConcurrentLinkedQueue<>();

        @Override
        public boolean send(byte[] opus) {
            packets.add(opus);
            return true;
        }
    }
}
