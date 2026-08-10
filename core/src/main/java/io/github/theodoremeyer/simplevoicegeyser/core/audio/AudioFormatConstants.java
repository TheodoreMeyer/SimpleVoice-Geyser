package io.github.theodoremeyer.simplevoicegeyser.core.audio;

/**
 * Fixed browser→server microphone frame format constants.
 * <p>
 * These values are protocol invariants and must not be made configurable.
 */
public final class AudioFormatConstants {

    /**
     * Expected PCM sample rate in Hz.
     */
    public static final int SAMPLE_RATE = 48000;

    /**
     * Expected channel count (mono).
     */
    public static final int CHANNELS = 1;

    /**
     * Samples per microphone frame (20 ms at 48 kHz).
     */
    public static final int FRAME_SAMPLES = 960;

    /**
     * Frame duration in milliseconds (SVC / Opus 20 ms frames).
     */
    public static final int FRAME_DURATION_MS = 20;

    /**
     * Frame duration in nanoseconds.
     */
    public static final long FRAME_DURATION_NANOS = FRAME_DURATION_MS * 1_000_000L;

    /**
     * After this idle gap following a send, call {@code AudioSender#reset()} so
     * Java clients close the continuous stream (VAD / PTT release).
     * <p>
     * Must sit slightly above the browser VAD hangover (~400 ms) so inter-syllable
     * gaps that still transmit do not leave the Opus encoder / AudioSender in a
     * discontinuous state without {@code reset()}.
     */
    public static final long STREAM_IDLE_RESET_NANOS = 500_000_000L;

    /**
     * If the pacing clock is behind by more than this many frames, drop oldest
     * queued frames and resynchronize deadlines instead of bursting sends.
     */
    public static final int PACING_MAX_LAG_FRAMES = 2;

    /**
     * Hard capacity for the per-session PCM jitter buffer.
     */
    public static final int QUEUE_HARD_CAPACITY = 32;

    /**
     * Target steady-state jitter depth (frames) ≈ 60–120 ms.
     */
    public static final int JITTER_TARGET_FRAMES = 4;

    /**
     * Startup fill before starting the playout clock (frames).
     */
    public static final int JITTER_STARTUP_FRAMES = 3;

    /**
     * Maximum allowed queue latency in frames (~200 ms).
     */
    public static final int JITTER_MAX_LATENCY_FRAMES = 10;

    /**
     * Payload bytes per frame ({@link #FRAME_SAMPLES} * 2 for PCM16LE).
     */
    public static final int FRAME_BYTES = 1920;

    /**
     * Current outbound mic framing protocol version.
     */
    public static final int PROTOCOL_VERSION = 1;

    /**
     * PCM16 little-endian mono format code used by versioned SVTX frames.
     */
    public static final byte PCM_FORMAT_PCM16LE = 1;

    private AudioFormatConstants() {}
}
