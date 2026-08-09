package io.github.theodoremeyer.simplevoicegeyser.core.schedule;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;

/**
 * Runnable wrapper that carries a stable task path for profilers and logs.
 */
public final class NamedTask implements Runnable {

    private final String name;
    private final Runnable delegate;

    /**
     * @param name stable logical task path (for example {@code svg/player/send-message})
     * @param delegate work to execute
     */
    public NamedTask(String name, Runnable delegate) {
        this.name = name == null || name.isBlank() ? "svg/unnamed" : name;
        this.delegate = delegate;
    }

    /**
     * @return stable task path
     */
    public String getName() {
        return name;
    }

    @Override
    public void run() {
        try {
            delegate.run();
        } catch (Throwable t) {
            try {
                SvgCore.getLogger().error("Scheduled task failed: " + name, t);
            } catch (Exception ignored) {
                // Logger may be unavailable during early shutdown.
            }
        }
    }

    @Override
    public String toString() {
        return name;
    }
}
