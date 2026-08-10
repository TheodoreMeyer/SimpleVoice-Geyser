package io.github.theodoremeyer.simplevoicegeyser.core.audio;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Evidence tests for monotonic deadline pacing without Thread.sleep on shared pools.
 */
class SessionAudioPacingTest {

    @Test
    void pacedSendsUseMonotonicDeadlinesWithoutBurst() throws Exception {
        List<Long> sendAt = new ArrayList<>();
        CountDownLatch done = new CountDownLatch(8);
        AtomicInteger encodes = new AtomicInteger();

        SessionAudioPipeline.Encoder encoder = new SessionAudioPipeline.Encoder() {
            @Override
            public byte[] encode(short[] pcm) {
                encodes.incrementAndGet();
                return new byte[]{1, 2, 3};
            }

            @Override
            public void resetState() {
            }

            @Override
            public void close() {
            }
        };

        // Production uses 20 ms; avoid sub-15 ms intervals (Windows timer coarseness).
        long intervalNanos = TimeUnit.MILLISECONDS.toNanos(20);
        SessionAudioPipeline pipeline = new SessionAudioPipeline(
                UUID.randomUUID(),
                encoder,
                opus -> {
                    synchronized (sendAt) {
                        sendAt.add(System.nanoTime());
                    }
                    done.countDown();
                    return true;
                },
                16,
                runnable -> new Thread(runnable, "unused-shared").start(),
                null,
                intervalNanos
        );

        for (long seq = 0; seq < 8; seq++) {
            assertTrue(pipeline.submit(sineFrame(seq)));
        }

        assertTrue(done.await(5, TimeUnit.SECONDS), "timed out waiting for paced sends");
        pipeline.close();

        assertEquals(8, encodes.get());
        assertEquals(8, sendAt.size());

        List<Long> intervals = new ArrayList<>();
        for (int i = 1; i < sendAt.size(); i++) {
            intervals.add(sendAt.get(i) - sendAt.get(i - 1));
        }
        long mean = intervals.stream().mapToLong(Long::longValue).sum() / intervals.size();
        long min = intervals.stream().mapToLong(Long::longValue).min().orElse(0L);
        long max = intervals.stream().mapToLong(Long::longValue).max().orElse(0L);

        // Mean near 20 ms; allow scheduler jitter without permitting drain-all bursts.
        assertTrue(mean > TimeUnit.MILLISECONDS.toNanos(12), "mean too fast (burst): " + mean);
        assertTrue(mean < TimeUnit.MILLISECONDS.toNanos(45), "mean too slow: " + mean);
        // No back-to-back burst: minimum gap should stay well above 1 ms.
        assertTrue(min > TimeUnit.MILLISECONDS.toNanos(5), "min interval indicates burst: " + min);
        assertTrue(max < TimeUnit.MILLISECONDS.toNanos(100), "max interval runaway: " + max);

        long reportedMean = pipeline.getDiagnostics().getMeanSendIntervalNanos();
        assertTrue(reportedMean > 0L);
    }

    @Test
    void repeatedMissedDeadlinesEventuallyTriggerPaceResync() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        AtomicInteger sends = new AtomicInteger();
        SessionAudioPipeline.Encoder encoder = new SessionAudioPipeline.Encoder() {
            @Override public byte[] encode(short[] pcm) { return new byte[]{1}; }
            @Override public void resetState() {}
            @Override public void close() {}
        };
        long intervalNanos = TimeUnit.MILLISECONDS.toNanos(10);
        SessionAudioPipeline pipeline = new SessionAudioPipeline(
                UUID.randomUUID(),
                encoder,
                opus -> {
                    int n = sends.incrementAndGet();
                    if (n == 1) {
                        started.countDown();
                    }
                    if (n >= 4) {
                        done.countDown();
                    }
                    return true;
                },
                32,
                runnable -> new Thread(runnable, "unused").start(),
                null,
                intervalNanos
        );

        // Start the clock with one frame, then stall so lag exceeds one frame, then flood.
        assertTrue(pipeline.submit(sineFrame(0)));
        assertTrue(started.await(2, TimeUnit.SECONDS));
        Thread.sleep(45); // > 2 * 10ms late
        for (long seq = 1; seq < 8; seq++) {
            assertTrue(pipeline.submit(sineFrame(seq)));
        }
        assertTrue(done.await(3, TimeUnit.SECONDS), "timed out waiting for paced backlog");
        assertTrue(
                pipeline.getDiagnostics().getPacingResyncs() >= 1L,
                "missed multi-frame lag must increment paceResync, resyncs="
                        + pipeline.getDiagnostics().getPacingResyncs()
                        + " missed=" + pipeline.getDiagnostics().getMissedDeadlines()
                        + " sends=" + sends.get()
        );
        pipeline.close();
    }

    @Test
    void mildLagResyncDoesNotFlushHealthyJitterBuffer() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch afterLag = new CountDownLatch(1);
        AtomicInteger sends = new AtomicInteger();
        SessionAudioPipeline.Encoder encoder = new SessionAudioPipeline.Encoder() {
            @Override public byte[] encode(short[] pcm) { return new byte[]{1}; }
            @Override public void resetState() {}
            @Override public void close() {}
        };
        long intervalNanos = TimeUnit.MILLISECONDS.toNanos(10);
        SessionAudioPipeline pipeline = new SessionAudioPipeline(
                UUID.randomUUID(),
                encoder,
                opus -> {
                    int n = sends.incrementAndGet();
                    if (n == 1) {
                        started.countDown();
                    }
                    if (n >= 3) {
                        afterLag.countDown();
                    }
                    return true;
                },
                32,
                runnable -> new Thread(runnable, "unused").start(),
                null,
                intervalNanos
        );

        // Start the clock, stall ~2 frames (mild), then flood the queue.
        assertTrue(pipeline.submit(sineFrame(0)));
        assertTrue(started.await(2, TimeUnit.SECONDS));
        // Need lagFrames == 2: after the first send, pacedFrameIndex=1 so deadline is
        // streamStart+10ms; sleep ≥30ms yields lagFrames≥2. Keep ≤ PACING_MAX_LAG_FRAMES.
        Thread.sleep(35);
        for (long seq = 1; seq < 24; seq++) {
            assertTrue(pipeline.submit(sineFrame(seq)));
        }
        assertTrue(afterLag.await(3, TimeUnit.SECONDS));

        long late = pipeline.getDiagnostics().getLateFrameDrops();
        long resyncs = pipeline.getDiagnostics().getPacingResyncs();
        // Old bug: lagFrames=2 dropped=22. New: mild lag trims only above max latency.
        assertTrue(resyncs >= 1L, "expected paceResync, resyncs=" + resyncs + " late=" + late);
        assertTrue(late <= 16L, "mild lag lateDrop too aggressive: " + late);
        pipeline.close();
    }

    @Test
    void activeSendIntervalExcludesIdleGaps() {
        AudioDiagnostics diagnostics = new AudioDiagnostics();
        diagnostics.onSendInterval(20_000_000L);
        diagnostics.onSendInterval(21_000_000L);
        diagnostics.onSendInterval(5_000_000_000L); // idle
        String summary = diagnostics.summary("test");
        assertTrue(summary.contains("activeSendIntervalAvgMs=20")
                || summary.contains("activeSendIntervalAvgMs=21")
                || summary.contains("activeSendIntervalAvgMs=19"), summary);
        assertTrue(summary.contains("idleGapMaxMs=5000"), summary);
        assertFalse(summary.contains("activeSendIntervalAvgMs=1680"), summary);
    }

    @Test
    void clampQueueCapacityMigratesObsoleteEightToThirtyTwo() {
        assertEquals(32, SessionAudioPipeline.clampQueueCapacity(8));
        assertEquals(32, SessionAudioPipeline.clampQueueCapacity(null));
        assertEquals(32, SessionAudioPipeline.clampQueueCapacity(32));
        assertEquals(64, SessionAudioPipeline.clampQueueCapacity(100));
    }

    @Test
    void encoderInputPreservesThousandHzPitchAndFrameSize() {
        short[] pcm = sinePcm(1000.0, 0.25, AudioFormatConstants.FRAME_SAMPLES);
        assertEquals(960, pcm.length);

        // Zero-crossing estimate of pitch for one 20 ms frame of 1 kHz ≈ 20 cycles.
        int crossings = 0;
        for (int i = 1; i < pcm.length; i++) {
            if ((pcm[i - 1] < 0 && pcm[i] >= 0) || (pcm[i - 1] >= 0 && pcm[i] < 0)) {
                crossings++;
            }
        }
        double estimatedHz = (crossings / 2.0) / (AudioFormatConstants.FRAME_SAMPLES / 48000.0);
        assertTrue(Math.abs(estimatedHz - 1000.0) < 80.0, "pitch estimate=" + estimatedHz);

        AtomicInteger seen = new AtomicInteger();
        SessionAudioPipeline.Encoder encoder = new SessionAudioPipeline.Encoder() {
            @Override
            public byte[] encode(short[] input) {
                seen.incrementAndGet();
                assertEquals(960, input.length);
                int c = 0;
                for (int i = 1; i < input.length; i++) {
                    if ((input[i - 1] < 0 && input[i] >= 0) || (input[i - 1] >= 0 && input[i] < 0)) {
                        c++;
                    }
                }
                double hz = (c / 2.0) / (960.0 / 48000.0);
                assertTrue(Math.abs(hz - 1000.0) < 80.0, "encoder input pitch=" + hz);
                return new byte[]{9};
            }

            @Override
            public void resetState() {
            }

            @Override
            public void close() {
            }
        };

        ControllableRunner runner = new ControllableRunner();
        SessionAudioPipeline pipeline = new SessionAudioPipeline(
                UUID.randomUUID(),
                encoder,
                opus -> true,
                8,
                runner
        );
        assertTrue(pipeline.submit(pcmFrame(0, pcm)));
        runner.drainAll();
        assertEquals(1, seen.get());
        pipeline.close();
    }

    @Test
    void idleResetHappensOncePerStreamNotPerQuietFrame() throws Exception {
        AtomicInteger resets = new AtomicInteger();
        CountDownLatch sent = new CountDownLatch(2);
        SessionAudioPipeline.Encoder encoder = new SessionAudioPipeline.Encoder() {
            @Override
            public byte[] encode(short[] pcm) {
                return new byte[]{1};
            }

            @Override
            public void resetState() {
            }

            @Override
            public void close() {
            }
        };
        SessionAudioPipeline pipeline = new SessionAudioPipeline(
                UUID.randomUUID(),
                encoder,
                new SessionAudioPipeline.EncodedSink() {
                    @Override
                    public boolean send(byte[] opus) {
                        sent.countDown();
                        return true;
                    }

                    @Override
                    public void endStream() {
                        resets.incrementAndGet();
                    }
                },
                8,
                runnable -> new Thread(runnable, "unused").start(),
                null,
                TimeUnit.MILLISECONDS.toNanos(5)
        );

        assertTrue(pipeline.submit(sineFrame(0)));
        assertTrue(pipeline.submit(sineFrame(1)));
        assertTrue(sent.await(2, TimeUnit.SECONDS));

        // Wait beyond STREAM_IDLE_RESET (500ms) for a single endStream.
        Thread.sleep(650);
        assertEquals(1, resets.get(), "expected exactly one stream reset after idle");
        assertEquals(1L, pipeline.getDiagnostics().getStreamResets());
        pipeline.close();
        // close may call endStream again only if stream still open; already ended → still 1
        assertTrue(resets.get() <= 2);
    }

    private static MicFrame sineFrame(long sequence) {
        return pcmFrame(sequence, sinePcm(1000.0, 0.2, AudioFormatConstants.FRAME_SAMPLES));
    }

    private static MicFrame pcmFrame(long sequence, short[] pcm) {
        byte[] bytes = new byte[pcm.length * 2];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcm);
        return new MicFrame(
                AudioFormatConstants.PROTOCOL_VERSION,
                0L,
                sequence,
                AudioFormatConstants.SAMPLE_RATE,
                AudioFormatConstants.CHANNELS,
                AudioFormatConstants.PCM_FORMAT_PCM16LE,
                AudioFormatConstants.FRAME_SAMPLES,
                bytes,
                sequence,
                false
        );
    }

    private static short[] sinePcm(double hz, double amplitude, int samples) {
        short[] pcm = new short[samples];
        double twoPiF = 2.0 * Math.PI * hz / AudioFormatConstants.SAMPLE_RATE;
        for (int i = 0; i < samples; i++) {
            pcm[i] = (short) Math.round(Math.sin(twoPiF * i) * amplitude * Short.MAX_VALUE);
        }
        return pcm;
    }

    private static final class ControllableRunner implements java.util.function.Consumer<Runnable> {
        private final java.util.ArrayDeque<Runnable> queue = new java.util.ArrayDeque<>();

        @Override
        public void accept(Runnable runnable) {
            queue.addLast(runnable);
        }

        void drainAll() {
            while (!queue.isEmpty()) {
                queue.removeFirst().run();
            }
        }
    }
}
