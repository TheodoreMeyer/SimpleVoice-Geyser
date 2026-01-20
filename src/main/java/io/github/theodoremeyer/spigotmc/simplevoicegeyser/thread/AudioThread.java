package io.github.theodoremeyer.spigotmc.simplevoicegeyser.thread;

import io.github.theodoremeyer.spigotmc.simplevoicegeyser.SVGPlugin;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AudioThread {

    private final SVGPlugin plugin;

    private static AudioThread instance;

    public final ExecutorService EXECUTOR =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "SVG-Audio");
                t.setDaemon(true);
                return t;
            });

    public AudioThread(SVGPlugin plugin) {
        this.plugin = plugin;
        instance = this;
    }

    public static ExecutorService getExecutor() {
        return instance.EXECUTOR;
    }
}
