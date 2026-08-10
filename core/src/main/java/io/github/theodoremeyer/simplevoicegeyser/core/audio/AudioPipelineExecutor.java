package io.github.theodoremeyer.simplevoicegeyser.core.audio;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Shared fixed thread pool for outbound browser→SVC Opus encode work.
 * <p>
 * Unlike {@link AudioThread} (single-thread inbound decode lane), this pool allows
 * concurrent encode across different sessions while each session remains serialized
 * by {@link SessionAudioPipeline}.
 */
public final class AudioPipelineExecutor {

    private static final int MIN_THREADS = 2;
    private static final int MAX_THREADS = 4;

    private static volatile AudioPipelineExecutor instance;

    private final ExecutorService executor;
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    /**
     * Create and install the shared outbound audio executor.
     */
    public AudioPipelineExecutor() {
        this(defaultPoolSize());
    }

    /**
     * Create and install the shared outbound audio executor with an explicit pool size.
     *
     * @param poolSize desired pool size (clamped to 2–4)
     */
    public AudioPipelineExecutor(int poolSize) {
        int threads = clampPoolSize(poolSize);
        AtomicInteger threadIndex = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "SVG-Audio-Out-" + threadIndex.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        this.executor = Executors.newFixedThreadPool(threads, factory);
        instance = this;
    }

    /**
     * Install a custom executor (tests). Replaces any previous instance without shutting it down.
     *
     * @param executorService executor to wrap
     * @return installed instance
     */
    static AudioPipelineExecutor installForTests(ExecutorService executorService) {
        AudioPipelineExecutor installed = new AudioPipelineExecutor(executorService);
        instance = installed;
        return installed;
    }

    private AudioPipelineExecutor(ExecutorService executorService) {
        this.executor = executorService;
        instance = this;
    }

    /**
     * Stop accepting work and shut down the pool. Idempotent.
     */
    public static void shutdown() {
        AudioPipelineExecutor current = instance;
        if (current == null) {
            return;
        }
        current.accepting.set(false);
        current.executor.shutdownNow();
    }

    /**
     * @return whether new outbound encode work is accepted
     */
    public static boolean isAccepting() {
        AudioPipelineExecutor current = instance;
        return current != null && current.accepting.get();
    }

    /**
     * Submit work to the shared outbound pool.
     *
     * @param runnable task to run
     */
    public static void execute(Runnable runnable) {
        AudioPipelineExecutor current = instance;
        if (current == null || !current.accepting.get() || runnable == null) {
            return;
        }
        try {
            current.executor.execute(() -> {
                try {
                    runnable.run();
                } catch (Throwable t) {
                    try {
                        SvgCore.getLogger().error("SVG-Audio-Out task failed", t);
                    } catch (Exception ignored) {
                    }
                }
            });
        } catch (RuntimeException ignored) {
            // Executor may already be shut down.
        }
    }

    /**
     * @return a {@link Consumer} bound to {@link #execute(Runnable)} for pipeline injection
     */
    public static Consumer<Runnable> runner() {
        return AudioPipelineExecutor::execute;
    }

    static int defaultPoolSize() {
        return clampPoolSize(Runtime.getRuntime().availableProcessors());
    }

    static int clampPoolSize(int poolSize) {
        return Math.max(MIN_THREADS, Math.min(MAX_THREADS, poolSize));
    }
}
