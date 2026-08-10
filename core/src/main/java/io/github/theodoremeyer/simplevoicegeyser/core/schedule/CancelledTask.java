package io.github.theodoremeyer.simplevoicegeyser.core.schedule;

/**
 * Task handle that is already cancelled and ignores further cancel requests.
 */
public final class CancelledTask implements ScheduledTask {

    /**
     * Shared cancelled handle.
     */
    public static final CancelledTask INSTANCE = new CancelledTask();

    private CancelledTask() {}

    @Override
    public void cancel() {
        // already cancelled
    }

    @Override
    public boolean isCancelled() {
        return true;
    }
}
