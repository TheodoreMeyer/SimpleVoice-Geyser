package io.github.theodoremeyer.simplevoicegeyser.core.audio;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionAudioPipelineTest {

    @Test
    void encodesSequentiallyWithoutReentrancy() {
        ControllableRunner runner = new ControllableRunner();
        FakeEncoder encoder = new FakeEncoder();
        RecordingSink sink = new RecordingSink();
        SessionAudioPipeline pipeline = new SessionAudioPipeline(
                UUID.randomUUID(),
                encoder,
                sink,
                8,
                runner
        );

        pipeline.submit(frame(0, 0));
        pipeline.submit(frame(0, 1));
        pipeline.submit(frame(0, 2));
        runner.drainAll();

        assertEquals(3, encoder.encodeCount.get());
        assertEquals(3, sink.packets.size());
        assertFalse(encoder.reentered.get());
        assertEquals(List.of(0L, 1L, 2L), encoder.encodedSequences);
        pipeline.close();
    }

    @Test
    void queueOverflowDropsOldest() {
        ControllableRunner runner = new ControllableRunner();
        FakeEncoder encoder = new FakeEncoder();
        RecordingSink sink = new RecordingSink();
        SessionAudioPipeline pipeline = new SessionAudioPipeline(
                UUID.randomUUID(),
                encoder,
                sink,
                3,
                runner
        );

        for (long seq = 0; seq < 5; seq++) {
            assertTrue(pipeline.submit(frame(0, seq)));
        }

        assertEquals(2L, pipeline.getDiagnostics().getQueueDrops());
        runner.drainAll();

        assertEquals(3, sink.packets.size());
        assertEquals(List.of(2L, 3L, 4L), encoder.encodedSequences);
        pipeline.close();
    }

    @Test
    void closeIsIdempotentAndRejectsFurtherFrames() {
        ControllableRunner runner = new ControllableRunner();
        FakeEncoder encoder = new FakeEncoder();
        SessionAudioPipeline pipeline = new SessionAudioPipeline(
                UUID.randomUUID(),
                encoder,
                new RecordingSink(),
                8,
                runner
        );

        pipeline.close();
        pipeline.close();
        assertEquals(1, encoder.closeCount.get());
        assertTrue(pipeline.isClosed());

        assertFalse(pipeline.submit(frame(0, 0)));
        assertEquals(1L, pipeline.getDiagnostics().getFramesRejectedClosed());
        assertEquals(0, runner.pending());
    }

    @Test
    void rejectsDuplicatesOutOfOrderAndStaleGeneration() {
        ControllableRunner runner = new ControllableRunner();
        FakeEncoder encoder = new FakeEncoder();
        SessionAudioPipeline pipeline = new SessionAudioPipeline(
                UUID.randomUUID(),
                encoder,
                new RecordingSink(),
                8,
                runner
        );

        assertTrue(pipeline.submit(frame(1, 5)));
        assertTrue(pipeline.submit(frame(1, 5))); // duplicate
        assertTrue(pipeline.submit(frame(1, 4))); // ooo
        assertTrue(pipeline.submit(frame(0, 9))); // stale generation
        assertTrue(pipeline.submit(frame(1, 7))); // gap then accept
        runner.drainAll();

        assertEquals(2, encoder.encodeCount.get());
        assertEquals(List.of(5L, 7L), encoder.encodedSequences);
        assertEquals(1L, pipeline.getDiagnostics().getDuplicates());
        assertEquals(1L, pipeline.getDiagnostics().getOutOfOrder());
        assertEquals(1L, pipeline.getDiagnostics().getStaleGeneration());
        assertEquals(1L, pipeline.getDiagnostics().getSequenceGaps());
        pipeline.close();
    }

    @Test
    void separateSessionsCanEncodeConcurrentlyOnSharedPool() throws Exception {
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        CountDownLatch bothEntered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);

        SessionAudioPipeline.Encoder blockingEncoder = new SessionAudioPipeline.Encoder() {
            @Override
            public byte[] encode(short[] pcm) {
                int depth = inFlight.incrementAndGet();
                maxInFlight.accumulateAndGet(depth, Math::max);
                bothEntered.countDown();
                try {
                    assertTrue(release.await(2, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    inFlight.decrementAndGet();
                }
                return new byte[]{1};
            }

            @Override
            public void resetState() {
            }

            @Override
            public void close() {
            }
        };

        Consumer<Runnable> parallelRunner = task -> {
            Thread thread = new Thread(task, "test-session-drain");
            thread.setDaemon(true);
            thread.start();
        };

        SessionAudioPipeline a = new SessionAudioPipeline(
                UUID.randomUUID(), blockingEncoder, opus -> true, 8, parallelRunner
        );
        SessionAudioPipeline b = new SessionAudioPipeline(
                UUID.randomUUID(), blockingEncoder, opus -> true, 8, parallelRunner
        );

        a.submit(frame(0, 0));
        b.submit(frame(0, 0));

        assertTrue(bothEntered.await(2, TimeUnit.SECONDS));
        assertTrue(maxInFlight.get() >= 2);
        release.countDown();

        a.close();
        b.close();
    }

    @Test
    void sameSessionNeverReentersEncoderUnderDeferredSharedDrain() {
        ControllableRunner runner = new ControllableRunner();
        FakeEncoder encoder = new FakeEncoder();
        SessionAudioPipeline pipeline = new SessionAudioPipeline(
                UUID.randomUUID(),
                encoder,
                new RecordingSink(),
                32,
                runner
        );

        for (long seq = 0; seq < 20; seq++) {
            pipeline.submit(frame(0, seq));
        }
        runner.drainAll();

        assertEquals(20, encoder.encodeCount.get());
        assertFalse(encoder.reentered.get());
        pipeline.close();
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
        private final AtomicInteger closeCount = new AtomicInteger();
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
            closeCount.incrementAndGet();
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
