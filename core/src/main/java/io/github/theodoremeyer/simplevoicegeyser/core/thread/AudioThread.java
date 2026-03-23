package io.github.theodoremeyer.simplevoicegeyser.core.thread;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Server's Audio Thread
 */
public final class AudioThread {

    private static AudioThread instance;

    private final ExecutorService executor;

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
        instance.executor.shutdownNow();
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
        instance.getExecutor().execute(runnable);
    }
}
