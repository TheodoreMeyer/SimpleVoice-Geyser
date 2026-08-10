package io.github.theodoremeyer.simplevoicegeyser.core.audio;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Server's Audio Thread
 */
public final class AudioThread {

    private static AudioThread instance;

    private final ExecutorService executor;
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    /**
     * EntryPoint
     */
    public AudioThread() {
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "SVG-Audio");
            t.setDaemon(true);
            return t;
        });
        instance = this;
    }

    /**
     * Stop Thread
     */
    public static void shutdown() {
        AudioThread current = instance;
        if (current == null) {
            return;
        }
        current.accepting.set(false);
        current.executor.shutdownNow();
    }

    /**
     * @return whether new audio work is accepted
     */
    public static boolean isAccepting() {
        AudioThread current = instance;
        return current != null && current.accepting.get();
    }

    /**
     * Gets the executor to execute on Audio Thread
     * @return executor
     */
    public ExecutorService getExecutor() {
        return instance.executor;
    }

    /**
     * Execute code on the thread
     * @param runnable code to execute
     */
    public static void execute(Runnable runnable) {
        AudioThread current = instance;
        if (current == null || !current.accepting.get() || runnable == null) {
            return;
        }
        try {
            current.executor.execute(() -> {
                try {
                    runnable.run();
                } catch (Throwable t) {
                    try {
                        SvgCore.getLogger().error("SVG-Audio task failed", t);
                    } catch (Exception ignored) {
                    }
                }
            });
        } catch (RuntimeException ex) {
            // Executor may already be shut down.
        }
    }
}
