package io.github.theodoremeyer.simplevoicegeyser.core.audio;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Lightweight outbound audio pipeline counters. Never stores or logs raw PCM.
 */
public final class AudioDiagnostics {

    private final LongAdder framesReceived = new LongAdder();
    private final LongAdder framesEncoded = new LongAdder();
    private final LongAdder framesSent = new LongAdder();
    private final LongAdder framesInvalid = new LongAdder();
    private final LongAdder framesRejectedClosed = new LongAdder();
    private final LongAdder framesPrivacyDropped = new LongAdder();
    private final LongAdder sequenceGaps = new LongAdder();
    private final LongAdder outOfOrder = new LongAdder();
    private final LongAdder duplicates = new LongAdder();
    private final LongAdder staleGeneration = new LongAdder();
    private final LongAdder queueDrops = new LongAdder();
    private final LongAdder missedDeadlines = new LongAdder();
    private final LongAdder pacingResyncs = new LongAdder();
    private final LongAdder streamResets = new LongAdder();
    private final LongAdder sendRejected = new LongAdder();
    private final LongAdder lateFrameDrops = new LongAdder();
    private final AtomicLong queueDepth = new AtomicLong();
    private final AtomicLong queueCapacity = new AtomicLong();
    private final AtomicLong targetJitterDepth = new AtomicLong(4);
    private final LongAdder arrivalIntervalSumNanos = new LongAdder();
    private final LongAdder arrivalIntervalSamples = new LongAdder();
    private final LongAdder encodeDurationSumNanos = new LongAdder();
    private final LongAdder encodeDurationSamples = new LongAdder();
    private final LongAdder sendIntervalSumNanos = new LongAdder();
    private final LongAdder sendIntervalSamples = new LongAdder();
    private final AtomicLong sendIntervalMinNanos = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong sendIntervalMaxNanos = new AtomicLong(0);
    private final LongAdder activeSendIntervalSumNanos = new LongAdder();
    private final LongAdder activeSendIntervalSamples = new LongAdder();
    private final AtomicLong activeSendIntervalMinNanos = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong activeSendIntervalMaxNanos = new AtomicLong(0);
    private final LongAdder activeSendIntervalP95Bucket = new LongAdder();
    private final AtomicLong idleGapMaxNanos = new AtomicLong(0);
    private final LongAdder activeTalkNanos = new LongAdder();
    private final LongAdder actualActiveSends = new LongAdder();
    private final AtomicLong opusSizeMin = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong opusSizeMax = new AtomicLong(0);
    private final AtomicLong lastArrivalNanos = new AtomicLong(0);
    private final AtomicLong lastSummaryAtFrames = new AtomicLong(0);
    private volatile String lastResetReason = "";
    /** Gaps at or above this are treated as idle (not active talk cadence). */
    private static final long IDLE_GAP_NANOS = 100_000_000L;

    /**
     * Record a frame accepted into the session queue.
     *
     * @param arrivalNanos frame arrival time
     * @param depth current queue depth after enqueue
     */
    public void onReceived(long arrivalNanos, int depth) {
        framesReceived.increment();
        queueDepth.set(depth);
        long previous = lastArrivalNanos.getAndSet(arrivalNanos);
        if (previous > 0L && arrivalNanos >= previous) {
            arrivalIntervalSumNanos.add(arrivalNanos - previous);
            arrivalIntervalSamples.increment();
        }
    }

    public void onInvalid() {
        framesInvalid.increment();
    }

    public void onRejectedClosed() {
        framesRejectedClosed.increment();
    }

    /**
     * Frame dropped because the player has no confirmed group membership.
     */
    public void onPrivacyDrop() {
        framesPrivacyDropped.increment();
    }

    public void onGap(long gapSize) {
        sequenceGaps.add(Math.max(1L, gapSize));
    }

    public void onOutOfOrder() {
        outOfOrder.increment();
    }

    public void onDuplicate() {
        duplicates.increment();
    }

    public void onStaleGeneration() {
        staleGeneration.increment();
    }

    public void onQueueDrop(int dropped) {
        if (dropped > 0) {
            queueDrops.add(dropped);
        }
    }

    public void onQueueDepth(int depth) {
        queueDepth.set(depth);
    }

    public void onQueueCapacity(int capacity, int targetJitter) {
        queueCapacity.set(capacity);
        targetJitterDepth.set(targetJitter);
    }

    public void onLateFrameDrop(int dropped) {
        if (dropped > 0) {
            lateFrameDrops.add(dropped);
        }
    }

    /**
     * Record a successful encode.
     *
     * @param encodeNanos encode duration
     * @param opusBytes encoded packet size
     */
    public void onEncoded(long encodeNanos, int opusBytes) {
        framesEncoded.increment();
        if (encodeNanos >= 0L) {
            encodeDurationSumNanos.add(encodeNanos);
            encodeDurationSamples.increment();
        }
        if (opusBytes >= 0) {
            opusSizeMin.accumulateAndGet(opusBytes, Math::min);
            opusSizeMax.accumulateAndGet(opusBytes, Math::max);
        }
    }

    public void onSent() {
        framesSent.increment();
    }

    /**
     * Record actual spacing between successful {@code AudioSender.send()} calls.
     * Intentional idle gaps (≥100 ms) are excluded from active send-interval averages.
     *
     * @param intervalNanos nanoseconds since previous send
     */
    public void onSendInterval(long intervalNanos) {
        if (intervalNanos <= 0L) {
            return;
        }
        sendIntervalSumNanos.add(intervalNanos);
        sendIntervalSamples.increment();
        sendIntervalMinNanos.accumulateAndGet(intervalNanos, Math::min);
        sendIntervalMaxNanos.accumulateAndGet(intervalNanos, Math::max);

        if (intervalNanos >= IDLE_GAP_NANOS) {
            idleGapMaxNanos.accumulateAndGet(intervalNanos, Math::max);
            return;
        }

        activeSendIntervalSumNanos.add(intervalNanos);
        activeSendIntervalSamples.increment();
        activeSendIntervalMinNanos.accumulateAndGet(intervalNanos, Math::min);
        activeSendIntervalMaxNanos.accumulateAndGet(intervalNanos, Math::max);
        if (intervalNanos >= 30_000_000L) {
            activeSendIntervalP95Bucket.increment();
        }
        actualActiveSends.increment();
        activeTalkNanos.add(intervalNanos);
    }

    /**
     * Mark a talk-stream reset boundary so idle accounting restarts cleanly.
     */
    public void onStreamBoundaryReset() {
        // Session-scoped counters retain history for DEBUG summaries.
    }

    public void onMissedDeadline(long behindNanos) {
        if (behindNanos > 0L) {
            missedDeadlines.increment();
        }
    }

    public void onPacingResync(int dropped) {
        pacingResyncs.increment();
    }

    public void onStreamReset(String reason) {
        streamResets.increment();
        lastResetReason = reason == null ? "" : reason;
    }

    public void onSendRejected() {
        sendRejected.increment();
    }

    /**
     * @param everyNFrames summary cadence
     * @return true when a debug summary should be emitted
     */
    public boolean shouldSummarize(long everyNFrames) {
        if (everyNFrames <= 0L) {
            return false;
        }
        long received = framesReceived.sum();
        long previous = lastSummaryAtFrames.get();
        if (received - previous < everyNFrames) {
            return false;
        }
        return lastSummaryAtFrames.compareAndSet(previous, received);
    }

    /**
     * Build a compact debug summary that never includes PCM samples.
     *
     * @param sessionLabel player / session label
     * @return summary line
     */
    public String summary(String sessionLabel) {
        long arrivals = arrivalIntervalSamples.sum();
        long encodes = encodeDurationSamples.sum();
        long sends = sendIntervalSamples.sum();
        long avgArrival = arrivals == 0L ? 0L : arrivalIntervalSumNanos.sum() / arrivals;
        long avgEncode = encodes == 0L ? 0L : encodeDurationSumNanos.sum() / encodes;
        long avgSend = sends == 0L ? 0L : sendIntervalSumNanos.sum() / sends;
        long minSend = sendIntervalMinNanos.get();
        long maxSend = sendIntervalMaxNanos.get();
        if (minSend == Long.MAX_VALUE) {
            minSend = 0L;
        }
        long minOpus = opusSizeMin.get();
        long maxOpus = opusSizeMax.get();
        if (minOpus == Long.MAX_VALUE) {
            minOpus = 0L;
        }

        long activeSamples = activeSendIntervalSamples.sum();
        long activeAvg = activeSamples == 0L ? 0L : activeSendIntervalSumNanos.sum() / activeSamples;
        long activeMin = activeSendIntervalMinNanos.get();
        long activeMax = activeSendIntervalMaxNanos.get();
        if (activeMin == Long.MAX_VALUE) {
            activeMin = 0L;
        }
        // Approximate P95: when ≥5% of active samples are ≥30 ms, report activeMax; else activeAvg.
        long p95 = activeSamples > 0 && activeSendIntervalP95Bucket.sum() * 20L >= activeSamples
                ? Math.max(activeAvg, 30_000_000L)
                : activeAvg;
        long talkNanos = activeTalkNanos.sum();
        long expected = talkNanos <= 0L ? 0L : talkNanos / AudioFormatConstants.FRAME_DURATION_NANOS;
        long actual = actualActiveSends.sum();
        long missing = Math.max(0L, expected - actual);

        return "AudioPipeline[" + sessionLabel + "]"
                + " recv=" + framesReceived.sum()
                + " enc=" + framesEncoded.sum()
                + " sent=" + framesSent.sum()
                + " invalid=" + framesInvalid.sum()
                + " closedReject=" + framesRejectedClosed.sum()
                + " privacyDrop=" + framesPrivacyDropped.sum()
                + " gaps=" + sequenceGaps.sum()
                + " ooo=" + outOfOrder.sum()
                + " dup=" + duplicates.sum()
                + " staleGen=" + staleGeneration.sum()
                + " qDrop=" + queueDrops.sum()
                + " lateDrop=" + lateFrameDrops.sum()
                + " queueSize=" + queueDepth.get()
                + " queueCapacity=" + queueCapacity.get()
                + " targetJitterDepth=" + targetJitterDepth.get()
                + " queueLatencyMs=" + (queueDepth.get() * 20L)
                + " missedDeadline=" + missedDeadlines.sum()
                + " paceResync=" + pacingResyncs.sum()
                + " streamReset=" + streamResets.sum()
                + " sendReject=" + sendRejected.sum()
                + " lastReset=" + lastResetReason
                + " avgArrivalNs=" + avgArrival
                + " avgEncodeNs=" + avgEncode
                + " sendIntervalMs=[" + (minSend / 1_000_000L) + "," + (avgSend / 1_000_000L)
                + "," + (maxSend / 1_000_000L) + "]"
                + " activeSendIntervalMinMs=" + (activeMin / 1_000_000L)
                + " activeSendIntervalAvgMs=" + (activeAvg / 1_000_000L)
                + " activeSendIntervalP95Ms=" + (p95 / 1_000_000L)
                + " activeSendIntervalMaxMs=" + (activeMax / 1_000_000L)
                + " idleGapMaxMs=" + (idleGapMaxNanos.get() / 1_000_000L)
                + " activeTalkMs=" + (talkNanos / 1_000_000L)
                + " expectedActiveSends=" + expected
                + " actualActiveSends=" + actual
                + " missingActiveSends=" + missing
                + " opusBytes=[" + minOpus + "," + maxOpus + "]";
    }

    public long getFramesReceived() {
        return framesReceived.sum();
    }

    public long getFramesEncoded() {
        return framesEncoded.sum();
    }

    public long getFramesSent() {
        return framesSent.sum();
    }

    public long getQueueDrops() {
        return queueDrops.sum();
    }

    public long getLateFrameDrops() {
        return lateFrameDrops.sum();
    }

    public long getOutOfOrder() {
        return outOfOrder.sum();
    }

    public long getDuplicates() {
        return duplicates.sum();
    }

    public long getFramesRejectedClosed() {
        return framesRejectedClosed.sum();
    }

    public long getFramesPrivacyDropped() {
        return framesPrivacyDropped.sum();
    }

    public long getSequenceGaps() {
        return sequenceGaps.sum();
    }

    public long getStaleGeneration() {
        return staleGeneration.sum();
    }

    public long getMissedDeadlines() {
        return missedDeadlines.sum();
    }

    public long getPacingResyncs() {
        return pacingResyncs.sum();
    }

    public long getStreamResets() {
        return streamResets.sum();
    }

    public long getSendRejected() {
        return sendRejected.sum();
    }

    /**
     * @return mean send interval in nanoseconds, or 0 when no samples
     */
    public long getMeanSendIntervalNanos() {
        long samples = sendIntervalSamples.sum();
        return samples == 0L ? 0L : sendIntervalSumNanos.sum() / samples;
    }

    public long getMinSendIntervalNanos() {
        long v = sendIntervalMinNanos.get();
        return v == Long.MAX_VALUE ? 0L : v;
    }

    public long getMaxSendIntervalNanos() {
        return sendIntervalMaxNanos.get();
    }
}
