package io.github.theodoremeyer.simplevoicegeyser.core.schedule;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Basic cancellable task handle with an optional cancel callback.
 */
public final class SimpleScheduledTask implements ScheduledTask {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final Consumer<SimpleScheduledTask> onCancel;

    /**
     * @param onCancel optional cancel callback
     */
    public SimpleScheduledTask(Consumer<SimpleScheduledTask> onCancel) {
        this.onCancel = onCancel;
    }

    /**
     * Create a handle with no cancel side effects.
     */
    public SimpleScheduledTask() {
        this(null);
    }

    @Override
    public void cancel() {
        if (cancelled.compareAndSet(false, true) && onCancel != null) {
            onCancel.accept(this);
        }
    }

    @Override
    public boolean isCancelled() {
        return cancelled.get();
    }
}
