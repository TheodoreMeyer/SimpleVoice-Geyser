package io.github.theodoremeyer.simplevoicegeyser.core.audio;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract tests for outbound browser→SVC microphone framing and serialization.
 */
class OutboundMicContractTest {

    @Test
    void onlyExact1920ByteLegacyFramesAreAccepted() {
        AtomicLong seq = new AtomicLong();
        assertTrue(MicFrameCodec.decode(new byte[1920], 1L, seq).isPresent());
        assertTrue(MicFrameCodec.decode(new byte[1919], 1L, seq).isEmpty());
        assertTrue(MicFrameCodec.decode(new byte[1921], 1L, seq).isEmpty());
        assertTrue(MicFrameCodec.decode(new byte[0], 1L, seq).isEmpty());
    }

    @Test
    void legacyFrameDecodesTo960SamplesAndKeepsPitch() {
        byte[] pcm = sinePcm16Le(1000, AudioFormatConstants.FRAME_SAMPLES);
        MicFrame frame = MicFrameCodec.decodeLegacy(pcm, 0L, 0L, 1L);
        assertEquals(AudioFormatConstants.FRAME_SAMPLES, frame.getSampleCount());
        assertEquals(AudioFormatConstants.FRAME_BYTES, frame.payloadRef().length);
        assertEquals(AudioFormatConstants.SAMPLE_RATE, frame.getSampleRate());
        short[] samples = SessionAudioPipeline.pcm16LeToShorts(frame.payloadRef());
        assertEquals(960, samples.length);
        double freq = approxFrequency(samples, AudioFormatConstants.SAMPLE_RATE);
        assertTrue(freq > 900 && freq < 1100, "freq=" + freq);
    }

    @Test
    void malformedSvtxIsRejectedSafely() {
        byte[] junk = new byte[64];
        junk[0] = 'S';
        junk[1] = 'V';
        junk[2] = 'T';
        junk[3] = 'X';
        assertTrue(MicFrameCodec.decode(junk, 1L, new AtomicLong()).isEmpty());
    }

    @Test
    void encodeIsSerializedOneAtATimePerSession() {
        ControllableRunner runner = new ControllableRunner();
        FakeEncoder encoder = new FakeEncoder();
        SessionAudioPipeline pipeline = new SessionAudioPipeline(
                UUID.randomUUID(),
                encoder,
                opus -> true,
                8,
                runner
        );

        for (long i = 0; i < 5; i++) {
            assertTrue(pipeline.submit(stampedFrame(0, i)));
        }
        runner.drainAll();
        assertEquals(5, encoder.encodeCount.get());
        assertFalse(encoder.reentered.get());
        assertEquals(List.of(0L, 1L, 2L, 3L, 4L), encoder.encodedSequences);
        pipeline.close();
    }

    @Test
    void boundedQueueDropsWholeOldestFramesOnly() {
        ControllableRunner runner = new ControllableRunner();
        FakeEncoder encoder = new FakeEncoder();
        SessionAudioPipeline pipeline = new SessionAudioPipeline(
                UUID.randomUUID(),
                encoder,
                opus -> true,
                2,
                runner
        );
        for (long i = 0; i < 5; i++) {
            pipeline.submit(stampedFrame(0, i));
        }
        assertEquals(3L, pipeline.getDiagnostics().getQueueDrops());
        runner.drainAll();
        assertEquals(2, encoder.encodeCount.get());
        assertEquals(List.of(3L, 4L), encoder.encodedSequences);
        pipeline.close();
    }

    @Test
    void shutdownRejectsNewAudio() {
        ControllableRunner runner = new ControllableRunner();
        SessionAudioPipeline pipeline = new SessionAudioPipeline(
                UUID.randomUUID(),
                new FakeEncoder(),
                opus -> true,
                8,
                runner
        );
        pipeline.close();
        assertFalse(pipeline.submit(stampedFrame(0, 0)));
        assertEquals(0, runner.pending());
    }

    @Test
    void audioOutboundThreadsAreDedicatedNotFolia() throws Exception {
        ExecutorService local = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "SVG-Audio-Out-test");
            t.setDaemon(true);
            return t;
        });
        AudioPipelineExecutor.installForTests(local);
        try {
            AtomicReference<String> name = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);
            AudioPipelineExecutor.execute(() -> {
                name.set(Thread.currentThread().getName());
                latch.countDown();
            });
            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertTrue(name.get().startsWith("SVG-Audio-Out"));
            assertFalse(name.get().toLowerCase().contains("folia"));
            assertFalse(name.get().toLowerCase().contains("region"));
            assertFalse(name.get().toLowerCase().contains("jetty"));
        } finally {
            local.shutdownNow();
        }
    }

    @Test
    void groupMembershipGateBlocksBeforeJoin() {
        UUID player = UUID.randomUUID();
        VoiceTransmitGate gate = new VoiceTransmitGate(player, 1L);
        ControllableRunner runner = new ControllableRunner();
        FakeEncoder encoder = new FakeEncoder();
        // Unpaced (0 ns): ControllableRunner owns drain; production uses 20 ms pacing.
        SessionAudioPipeline pipeline = new SessionAudioPipeline(
                player, encoder, opus -> true, 8, runner, gate, 0L
        );
        assertFalse(pipeline.submit(stampedFrame(0, 0)));
        runner.drainAll();
        assertEquals(0, encoder.encodeCount.get());
        gate.applyMembership(SessionVoiceMembership.joined(player, 1L, UUID.randomUUID(), 1L));
        assertTrue(pipeline.submit(stampedFrame(0, 1)));
        runner.drainAll();
        assertEquals(1, encoder.encodeCount.get());
        pipeline.close();
    }

    private static MicFrame stampedFrame(long generation, long sequence) {
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
                true
        );
    }

    private static byte[] sinePcm16Le(double freq, int samples) {
        ByteBuffer buffer = ByteBuffer.allocate(samples * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < samples; i++) {
            double s = Math.sin(2.0 * Math.PI * freq * i / AudioFormatConstants.SAMPLE_RATE);
            short v = (short) Math.round(s * 32767.0);
            buffer.putShort(v);
        }
        return buffer.array();
    }

    private static double approxFrequency(short[] samples, int sampleRate) {
        int start = samples.length / 5;
        int end = samples.length - start;
        int zc = 0;
        for (int i = start + 1; i < end; i++) {
            if (samples[i - 1] < 0 && samples[i] >= 0) {
                zc++;
            }
        }
        return zc / ((end - start) / (double) sampleRate);
    }

    private static final class ControllableRunner implements Consumer<Runnable> {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public synchronized void accept(Runnable runnable) {
            tasks.addLast(runnable);
        }

        synchronized int pending() {
            return tasks.size();
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
        private final AtomicInteger depth = new AtomicInteger();
        private final AtomicBoolean reentered = new AtomicBoolean(false);
        private final AtomicInteger encodeCount = new AtomicInteger();
        private final List<Long> encodedSequences = new ArrayList<>();

        @Override
        public byte[] encode(short[] pcm) {
            if (depth.getAndIncrement() > 0) {
                reentered.set(true);
            }
            try {
                encodeCount.incrementAndGet();
                byte[] bytes = new byte[pcm.length * 2];
                ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcm);
                long sequence = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
                encodedSequences.add(sequence);
                return new byte[]{9, 9};
            } finally {
                depth.decrementAndGet();
            }
        }

        @Override
        public void resetState() {
        }

        @Override
        public void close() {
        }
    }
}
