package io.github.theodoremeyer.simplevoicegeyser.core.schedule;

/**
 * Handle for a task scheduled through {@link TaskScheduler}.
 */
public interface ScheduledTask {

    /**
     * Cancel this task if it has not already finished.
     */
    void cancel();

    /**
     * @return whether this task has been cancelled
     */
    boolean isCancelled();
}
