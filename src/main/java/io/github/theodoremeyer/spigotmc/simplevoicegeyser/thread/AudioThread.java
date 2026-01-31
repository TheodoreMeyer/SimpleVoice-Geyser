package io.github.theodoremeyer.spigotmc.simplevoicegeyser.thread;

import io.github.theodoremeyer.spigotmc.simplevoicegeyser.SVGPlugin;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Server's Audio Thread
 */
public class AudioThread {

    private final SVGPlugin plugin;

    private static AudioThread instance;

    /**
     * The Thread
     */
    public final ExecutorService EXECUTOR =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "SVG-Audio");
                t.setDaemon(true);
                return t;
            });

    /**
     * Constructor, obtains plugin which will be used in the future.
     * @param plugin SVG plugin
     */
    public AudioThread(SVGPlugin plugin) {
        this.plugin = plugin;
        instance = this;
    }

    /**
     * Gets the executor to execute on Audio Thread
     * @return executor
     */
    public static ExecutorService getExecutor() {
        return instance.EXECUTOR;
    }
}
