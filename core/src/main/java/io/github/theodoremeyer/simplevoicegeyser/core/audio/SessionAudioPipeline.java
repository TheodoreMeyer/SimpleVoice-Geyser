package io.github.theodoremeyer.simplevoicegeyser.core.audio;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Per-session serialized outbound microphone pipeline.
 * <p>
 * Frames are accepted on the Jetty callback thread, queued with a bounded drop-oldest
 * policy, then drained on a dedicated per-session pace scheduler so Opus encode never
 * runs on the websocket thread and never blocks the shared {@link AudioPipelineExecutor}
 * with {@code Thread.sleep}. Each session has at most one paced send in flight.
 */
public final class SessionAudioPipeline {

    private static final long DEBUG_SUMMARY_EVERY_FRAMES = 100L;
    private static final AtomicInteger PACE_THREAD_INDEX = new AtomicInteger();

    /**
     * Opus encoder owned by this pipeline (closed exactly once).
     */
    public interface Encoder {
        /**
         * Encode one PCM16 frame.
         *
         * @param pcm samples
         * @return opus bytes, or empty/null on failure
         */
        byte[] encode(short[] pcm);

        /**
         * Reset codec state.
         */
        void resetState();

        /**
         * Close the encoder.
         */
        void close();
    }

    /**
     * Sink that forwards encoded Opus to Simple Voice Chat.
     */
    public interface EncodedSink {
        /**
         * @param opus encoded packet
         * @return whether send succeeded
         */
        boolean send(byte[] opus);

        /**
         * Signal end of a continuous talk stream (maps to {@code AudioSender#reset()}).
         * Default no-op for tests.
         */
        default void endStream() {
        }
    }

    private final UUID sessionId;
    private final Encoder encoder;
    private final EncodedSink sink;
    private final Consumer<Runnable> taskRunner;
    private final int queueCapacity;
    private final long frameIntervalNanos;
    private final AudioDiagnostics diagnostics = new AudioDiagnostics();
    private final VoiceTransmitGate transmitGate;
    /** Dedicated single-thread scheduler for this session's 20 ms deadlines; null when unpaced. */
    private final ScheduledExecutorService paceExecutor;

    private final Object queueLock = new Object();
    private final ArrayDeque<MicFrame> queue = new ArrayDeque<>();
    private final AtomicBoolean processing = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean encoderClosed = new AtomicBoolean(false);

    private long activeStreamGeneration = -1L;
    private long lastAcceptedSequence = -1L;
    private boolean hasAcceptedSequence = false;
    private long lastSendNanos = 0L;
    private boolean streamOpen = false;
    /** Monotonic stream clock: nextDeadline = streamStartNanos + frameIndex * interval. */
    private long streamStartNanos = 0L;
    private long pacedFrameIndex = 0L;
    private long pacingDrops = 0L;
    private long pacingResyncs = 0L;
    private long streamResetCount = 0L;
    private volatile ScheduledFuture<?> pendingPaceTask;
    /** True while waiting for idle-stream end; must not block new-frame drains. */
    private final AtomicBoolean idleWatchActive = new AtomicBoolean(false);

    /**
     * Create a session pipeline.
     *
     * @param sessionId player uuid (diagnostics label)
     * @param encoder opus encoder
     * @param sink encoded audio sink
     * @param queueCapacity bounded queue size (minimum 1)
     * @param taskRunner shared executor submitter
     */
    public SessionAudioPipeline(
            UUID sessionId,
            Encoder encoder,
            EncodedSink sink,
            int queueCapacity,
            Consumer<Runnable> taskRunner
    ) {
        this(sessionId, encoder, sink, queueCapacity, taskRunner, null, 0L);
    }

    /**
     * Create a session pipeline with an outbound privacy gate.
     *
     * @param sessionId player uuid (diagnostics label)
     * @param encoder opus encoder
     * @param sink encoded audio sink
     * @param queueCapacity bounded queue size (minimum 1)
     * @param taskRunner shared executor submitter
     * @param transmitGate optional membership gate (null = always open for tests)
     */
    public SessionAudioPipeline(
            UUID sessionId,
            Encoder encoder,
            EncodedSink sink,
            int queueCapacity,
            Consumer<Runnable> taskRunner,
            VoiceTransmitGate transmitGate
    ) {
        this(
                sessionId,
                encoder,
                sink,
                queueCapacity,
                taskRunner,
                transmitGate,
                AudioFormatConstants.FRAME_DURATION_NANOS
        );
    }

    /**
     * Create a session pipeline with explicit send pacing.
     *
     * @param sessionId player uuid
     * @param encoder opus encoder
     * @param sink encoded audio sink
     * @param queueCapacity bounded queue size
     * @param taskRunner shared executor submitter
     * @param transmitGate optional membership gate
     * @param frameIntervalNanos minimum spacing between SVC sends (0 disables pacing for tests)
     */
    public SessionAudioPipeline(
            UUID sessionId,
            Encoder encoder,
            EncodedSink sink,
            int queueCapacity,
            Consumer<Runnable> taskRunner,
            VoiceTransmitGate transmitGate,
            long frameIntervalNanos
    ) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.encoder = Objects.requireNonNull(encoder, "encoder");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.taskRunner = Objects.requireNonNull(taskRunner, "taskRunner");
        this.queueCapacity = Math.max(1, queueCapacity);
        this.frameIntervalNanos = Math.max(0L, frameIntervalNanos);
        this.transmitGate = transmitGate;
        if (transmitGate != null) {
            transmitGate.setOnGateClosed(ignored -> clearQueuedFramesAndResetEncoder());
        }
        if (this.frameIntervalNanos > 0L) {
            int n = PACE_THREAD_INDEX.incrementAndGet();
            this.paceExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "SVG-Audio-Pace-" + n);
                thread.setDaemon(true);
                return thread;
            });
        } else {
            this.paceExecutor = null;
        }
    }

    /**
     * Submit a frame for serialized encode/send.
     *
     * @param frame microphone frame
     * @return true when the frame was accepted into the queue
     */
    public boolean submit(MicFrame frame) {
        if (frame == null) {
            diagnostics.onInvalid();
            return false;
        }
        if (closed.get()) {
            diagnostics.onRejectedClosed();
            return false;
        }
        if (!allowsTransmit()) {
            dropForPrivacy();
            return false;
        }
        if (!frame.isValidFormat()) {
            diagnostics.onInvalid();
            return false;
        }

        int depth;
        int dropped = 0;
        synchronized (queueLock) {
            if (closed.get()) {
                diagnostics.onRejectedClosed();
                return false;
            }
            if (!allowsTransmit()) {
                dropForPrivacy();
                return false;
            }
            while (queue.size() >= queueCapacity) {
                queue.pollFirst();
                dropped++;
            }
            queue.addLast(frame);
            depth = queue.size();
        }

        diagnostics.onQueueDrop(dropped);
        diagnostics.onReceived(frame.getArrivalNanos(), depth);
        scheduleDrain();
        maybeLogSummary();
        return true;
    }

    /**
     * Clear queued PCM and reset encoder state without closing the pipeline.
     * Used when leaving a group so pre-leave buffered audio cannot leak publicly.
     */
    public void clearQueuedFramesAndResetEncoder() {
        synchronized (queueLock) {
            int cleared = queue.size();
            queue.clear();
            diagnostics.onQueueDepth(0);
            if (cleared > 0) {
                diagnostics.onQueueDrop(cleared);
            }
        }
        if (!closed.get() && !encoderClosed.get()) {
            try {
                encoder.resetState();
            } catch (Exception e) {
                SvgCore.getLogger().debug("AudioPipeline: resetState after gate close failed for " + sessionId, e);
            }
        }
        endStreamIfOpen("gate_closed");
    }

    /**
     * @return whether the privacy gate currently allows transmit
     */
    public boolean allowsTransmit() {
        return transmitGate == null || transmitGate.allowsTransmit();
    }

    /**
     * @return transmit gate, or null when ungated (tests)
     */
    public VoiceTransmitGate getTransmitGate() {
        return transmitGate;
    }

    /**
     * Close the pipeline and encoder exactly once. Rejects further submits.
     */
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        cancelPendingPaceTask();

        synchronized (queueLock) {
            queue.clear();
            diagnostics.onQueueDepth(0);
        }

        endStreamIfOpen("pipeline_close");
        closeEncoderOnce();

        idleWatchActive.set(false);
        if (paceExecutor != null) {
            paceExecutor.shutdownNow();
        }
    }

    /**
     * @return whether the pipeline has been closed
     */
    public boolean isClosed() {
        return closed.get();
    }

    /**
     * @return diagnostics counters for tests / debug
     */
    public AudioDiagnostics getDiagnostics() {
        return diagnostics;
    }

    /**
     * @return configured queue capacity
     */
    public int getQueueCapacity() {
        return queueCapacity;
    }

    private void scheduleDrain() {
        if (closed.get()) {
            return;
        }
        // Cancel idle-only waits so newly queued frames are paced promptly.
        // Do not cancel an in-flight paced send (processing == true, idleWatch inactive).
        if (idleWatchActive.compareAndSet(true, false)) {
            cancelPendingPaceTask();
            processing.compareAndSet(true, false);
        }
        if (!processing.compareAndSet(false, true)) {
            return;
        }
        if (paceExecutor != null) {
            try {
                paceExecutor.execute(this::drainOnePaced);
            } catch (RejectedExecutionException ex) {
                processing.set(false);
            }
        } else {
            // Unpaced test path: drain via injected runner without Thread.sleep.
            taskRunner.accept(this::drainUnpaced);
        }
    }

    /**
     * Test / unpaced path: serialize encodes without real-time deadlines.
     */
    private void drainUnpaced() {
        try {
            while (!closed.get()) {
                MicFrame frame;
                synchronized (queueLock) {
                    frame = queue.pollFirst();
                    diagnostics.onQueueDepth(queue.size());
                }
                if (frame == null) {
                    break;
                }
                processFrame(frame);
            }
        } finally {
            finishProcessingCycle(false);
        }
    }

    /**
     * Production path: at most one Opus send per wake-up, scheduled to the next
     * monotonic deadline. Never calls {@code Thread.sleep}.
     * <p>
     * Frames stay in the queue until the deadline fires so cancelling a pending
     * pace task cannot lose audio.
     */
    private void drainOnePaced() {
        if (closed.get()) {
            processing.set(false);
            return;
        }

        try {
            maybeEndIdleStream();
            resynchronizeIfBehind();

            boolean empty;
            synchronized (queueLock) {
                empty = queue.isEmpty();
                diagnostics.onQueueDepth(queue.size());
                diagnostics.onQueueCapacity(queueCapacity, AudioFormatConstants.JITTER_TARGET_FRAMES);
            }
            if (empty) {
                maybeEndIdleStream();
                finishProcessingCycle(true);
                return;
            }

            long now = System.nanoTime();
            if (streamStartNanos <= 0L) {
                streamStartNanos = now;
                pacedFrameIndex = 0L;
            }
            long deadline = streamStartNanos + pacedFrameIndex * frameIntervalNanos;
            long delayNs = deadline - now;
            if (delayNs > 1_000_000L) {
                try {
                    pendingPaceTask = paceExecutor.schedule(
                            this::drainOnePaced,
                            delayNs,
                            TimeUnit.NANOSECONDS
                    );
                } catch (RejectedExecutionException ex) {
                    processing.set(false);
                }
                return;
            }
            if (delayNs < 0L) {
                diagnostics.onMissedDeadline(-delayNs);
            }

            MicFrame frame;
            synchronized (queueLock) {
                frame = queue.pollFirst();
                diagnostics.onQueueDepth(queue.size());
            }
            if (frame == null) {
                maybeEndIdleStream();
                finishProcessingCycle(true);
                return;
            }
            sendPacedFrame(frame);
        } catch (RuntimeException ex) {
            processing.set(false);
            try {
                SvgCore.getLogger().debug("AudioPipeline: paced drain failed for " + sessionId, ex);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void sendPacedFrame(MicFrame frame) {
        try {
            if (!closed.get()) {
                processFrame(frame);
            }
        } finally {
            boolean hasMore;
            synchronized (queueLock) {
                hasMore = !queue.isEmpty();
            }
            if (hasMore && !closed.get() && paceExecutor != null && streamStartNanos > 0L) {
                long nextDeadline = streamStartNanos + pacedFrameIndex * frameIntervalNanos;
                // Absolute sample clock, plus a hard minimum gap so overdue catch-up
                // never collapses into a back-to-back SVC burst.
                long minGapDeadline = lastSendNanos > 0L
                        ? lastSendNanos + frameIntervalNanos
                        : nextDeadline;
                long delayNs = Math.max(0L, Math.max(nextDeadline, minGapDeadline) - System.nanoTime());
                try {
                    pendingPaceTask = paceExecutor.schedule(
                            this::drainOnePaced,
                            delayNs,
                            TimeUnit.NANOSECONDS
                    );
                } catch (RejectedExecutionException ex) {
                    processing.set(false);
                }
            } else {
                finishProcessingCycle(true);
            }
        }
    }

    private void finishProcessingCycle(boolean mayScheduleIdle) {
        processing.set(false);
        if (closed.get()) {
            return;
        }
        boolean hasMore;
        synchronized (queueLock) {
            hasMore = !queue.isEmpty();
        }
        if (hasMore) {
            scheduleDrain();
        } else if (mayScheduleIdle && streamOpen && frameIntervalNanos > 0L) {
            scheduleIdleWatch();
        }
    }

    /**
     * When the pacing clock is late, resynchronize the monotonic deadline.
     * Mild scheduler lateness must not flush a healthy jitter buffer.
     * Frames are discarded only when buffered media latency exceeds the max,
     * and then only the minimum oldest complete frames needed to restore target depth.
     */
    private void resynchronizeIfBehind() {
        if (frameIntervalNanos <= 0L || streamStartNanos <= 0L) {
            return;
        }
        long now = System.nanoTime();
        long deadline = streamStartNanos + pacedFrameIndex * frameIntervalNanos;
        long lagFrames = Math.max(0L, (now - deadline) / frameIntervalNanos);
        if (lagFrames <= 1L) {
            return;
        }

        diagnostics.onMissedDeadline(Math.max(0L, now - deadline));

        int target = AudioFormatConstants.JITTER_TARGET_FRAMES;
        int maxLatency = AudioFormatConstants.JITTER_MAX_LATENCY_FRAMES;
        int dropped = 0;
        int queueSize;
        synchronized (queueLock) {
            queueSize = queue.size();
            // Mild clock lag (a few frames): reset the playout clock only.
            // Do not delete speech merely because the scheduler was slightly late.
            boolean mildLag = lagFrames <= AudioFormatConstants.PACING_MAX_LAG_FRAMES;
            if (!mildLag) {
                // Severe lateness: drop only excess above max buffered latency.
                while (queue.size() > maxLatency) {
                    queue.pollFirst();
                    dropped++;
                }
                // Then trim to target depth — minimum oldest frames, FIFO.
                while (queue.size() > target) {
                    queue.pollFirst();
                    dropped++;
                }
            } else if (queue.size() > maxLatency) {
                // Even on mild lag, never keep hundreds of ms of stale media.
                while (queue.size() > maxLatency) {
                    queue.pollFirst();
                    dropped++;
                }
            }
            diagnostics.onQueueDepth(queue.size());
            diagnostics.onQueueCapacity(queueCapacity, target);
            queueSize = queue.size();
        }
        if (dropped > 0) {
            pacingDrops += dropped;
            diagnostics.onQueueDrop(dropped);
            diagnostics.onLateFrameDrop(dropped);
        }
        // Always resync the monotonic clock so backlog is not burst-sent.
        streamStartNanos = now;
        pacedFrameIndex = 0L;
        pacingResyncs++;
        diagnostics.onPacingResync(dropped);
        try {
            SvgCore.getLogger().debug(
                    "AudioPipeline: pacing resync session=" + sessionId
                            + " lagFrames=" + lagFrames
                            + " dropped=" + dropped
                            + " queueAfter=" + queueSize
                            + " reason=" + (dropped > 0 ? "excess_latency" : "clock_only")
                            + " totalDrops=" + pacingDrops
                            + " resyncs=" + pacingResyncs
                            + " queueCapacity=" + queueCapacity
            );
        } catch (RuntimeException ignored) {
            // SvgCore may be unavailable in isolated unit tests.
        }
    }

    private void maybeEndIdleStream() {
        if (!streamOpen || lastSendNanos <= 0L) {
            return;
        }
        long idle = System.nanoTime() - lastSendNanos;
        if (idle >= AudioFormatConstants.STREAM_IDLE_RESET_NANOS) {
            endStreamIfOpen("idle_timeout");
        }
    }

    private void endStreamIfOpen(String reason) {
        if (!streamOpen) {
            return;
        }
        streamOpen = false;
        streamStartNanos = 0L;
        pacedFrameIndex = 0L;
        streamResetCount++;
        diagnostics.onStreamReset(reason);
        try {
            sink.endStream();
        } catch (RuntimeException ex) {
            SvgCore.getLogger().debug("AudioPipeline: endStream failed for " + sessionId, ex);
        }
        if (!closed.get() && !encoderClosed.get()) {
            try {
                encoder.resetState();
            } catch (Exception e) {
                SvgCore.getLogger().debug("AudioPipeline: encoder reset after endStream failed for " + sessionId, e);
            }
        }
    }

    private void scheduleIdleWatch() {
        if (paceExecutor == null || frameIntervalNanos <= 0L) {
            return;
        }
        if (!idleWatchActive.compareAndSet(false, true)) {
            return;
        }
        long idleSoFar = lastSendNanos <= 0L ? 0L : System.nanoTime() - lastSendNanos;
        long waitNs = Math.max(
                frameIntervalNanos,
                AudioFormatConstants.STREAM_IDLE_RESET_NANOS - idleSoFar
        );
        try {
            pendingPaceTask = paceExecutor.schedule(() -> {
                idleWatchActive.set(false);
                maybeEndIdleStream();
                boolean hasMore;
                synchronized (queueLock) {
                    hasMore = !queue.isEmpty();
                }
                if (hasMore && !closed.get()) {
                    scheduleDrain();
                }
            }, waitNs, TimeUnit.NANOSECONDS);
        } catch (RejectedExecutionException ex) {
            idleWatchActive.set(false);
        }
    }

    private void cancelPendingPaceTask() {
        ScheduledFuture<?> pending = pendingPaceTask;
        pendingPaceTask = null;
        if (pending != null) {
            pending.cancel(false);
        }
    }

    private void processFrame(MicFrame frame) {
        if (closed.get()) {
            diagnostics.onRejectedClosed();
            return;
        }

        // Privacy gate must reject before Opus encode and before AudioSender#send.
        if (!allowsTransmit()) {
            dropForPrivacy();
            return;
        }

        if (!acceptSequence(frame)) {
            return;
        }

        short[] pcm;
        try {
            pcm = pcm16LeToShorts(frame.payloadRef());
        } catch (RuntimeException ex) {
            diagnostics.onInvalid();
            return;
        }
        if (pcm.length != AudioFormatConstants.FRAME_SAMPLES) {
            diagnostics.onInvalid();
            return;
        }

        if (!allowsTransmit()) {
            dropForPrivacy();
            return;
        }

        long started = System.nanoTime();
        byte[] encoded;
        try {
            encoded = encoder.encode(pcm);
        } catch (RuntimeException ex) {
            SvgCore.getLogger().debug("AudioPipeline: encode failed for " + sessionId, ex);
            return;
        }
        long encodeNanos = System.nanoTime() - started;

        if (encoded == null || encoded.length == 0) {
            SvgCore.getLogger().debug("AudioPipeline: empty opus for " + sessionId);
            return;
        }

        diagnostics.onEncoded(encodeNanos, encoded.length);

        if (closed.get()) {
            diagnostics.onRejectedClosed();
            return;
        }

        if (!allowsTransmit()) {
            dropForPrivacy();
            return;
        }

        boolean sent;
        try {
            sent = sink.send(encoded);
        } catch (RuntimeException ex) {
            SvgCore.getLogger().debug("AudioPipeline: send failed for " + sessionId, ex);
            return;
        }
        if (sent) {
            long now = System.nanoTime();
            if (lastSendNanos > 0L) {
                diagnostics.onSendInterval(now - lastSendNanos);
            }
            diagnostics.onSent();
            lastSendNanos = now;
            streamOpen = true;
            pacedFrameIndex++;
        } else {
            diagnostics.onSendRejected();
            SvgCore.getLogger().debug("AudioPipeline: sink rejected packet for " + sessionId);
        }
    }

    private void dropForPrivacy() {
        diagnostics.onPrivacyDrop();
        if (transmitGate != null) {
            transmitGate.onDroppedFrame();
        }
    }

    private boolean acceptSequence(MicFrame frame) {
        long generation = frame.getStreamGeneration();
        long sequence = frame.getSequence();

        if (activeStreamGeneration < 0L || generation > activeStreamGeneration) {
            activeStreamGeneration = generation;
            lastAcceptedSequence = sequence;
            hasAcceptedSequence = true;
            return true;
        }

        if (generation < activeStreamGeneration) {
            diagnostics.onStaleGeneration();
            return false;
        }

        // Same generation.
        if (!hasAcceptedSequence) {
            lastAcceptedSequence = sequence;
            hasAcceptedSequence = true;
            return true;
        }

        if (sequence == lastAcceptedSequence) {
            diagnostics.onDuplicate();
            return false;
        }
        if (sequence < lastAcceptedSequence) {
            diagnostics.onOutOfOrder();
            return false;
        }
        if (sequence > lastAcceptedSequence + 1L) {
            diagnostics.onGap(sequence - lastAcceptedSequence - 1L);
        }
        lastAcceptedSequence = sequence;
        return true;
    }

    private void closeEncoderOnce() {
        if (!encoderClosed.compareAndSet(false, true)) {
            return;
        }
        try {
            encoder.resetState();
        } catch (Exception e) {
            SvgCore.getLogger().debug("AudioPipeline: resetState failed for " + sessionId, e);
        }
        try {
            encoder.close();
        } catch (Exception e) {
            SvgCore.getLogger().warning(
                    "[AudioPipeline] Failed to close encoder for " + sessionId + ": " + e.getMessage()
            );
        }
    }

    private void maybeLogSummary() {
        if (!diagnostics.shouldSummarize(DEBUG_SUMMARY_EVERY_FRAMES)) {
            return;
        }
        try {
            SvgCore.getLogger().debug(diagnostics.summary(String.valueOf(sessionId)));
        } catch (Exception ignored) {
        }
    }

    static short[] pcm16LeToShorts(byte[] pcm) {
        if (pcm.length % 2 != 0) {
            throw new IllegalArgumentException("PCM16LE byte length must be even");
        }
        ByteBuffer buffer = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
        short[] samples = new short[pcm.length / 2];
        buffer.asShortBuffer().get(samples);
        return samples;
    }

    /**
     * Clamp unsafe outbound queue capacity config values.
     *
     * @param configured configured capacity
     * @return capacity in [16, 64], defaulting to {@link AudioFormatConstants#QUEUE_HARD_CAPACITY}
     */
    public static int clampQueueCapacity(Integer configured) {
        if (configured == null || configured < 1) {
            return AudioFormatConstants.QUEUE_HARD_CAPACITY;
        }
        // Obsolete capacity-8 (and other tiny) configs are unsafe for browser bursts.
        if (configured < 16) {
            return AudioFormatConstants.QUEUE_HARD_CAPACITY;
        }
        return Math.min(configured, 64);
    }
}
