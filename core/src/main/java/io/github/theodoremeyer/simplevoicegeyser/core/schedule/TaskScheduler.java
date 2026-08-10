package io.github.theodoremeyer.simplevoicegeyser.core.schedule;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Platform scheduling abstraction for global, region, entity, and async work.
 * <p>
 * Implementations must attribute work to this plugin and reject new tasks after
 * {@link #shutdown()}.
 */
public interface TaskScheduler {

    /**
     * @return whether this scheduler accepts new work
     */
    boolean isAcceptingTasks();

    /**
     * @return whether the platform uses regionized threading (Folia/Canvas)
     */
    boolean isRegionThreaded();

    /**
     * Reject new work and cancel tracked tasks owned by this scheduler.
     * Idempotent.
     */
    void shutdown();

    /**
     * Cancel all tracked tasks without fully shutting down acceptance state.
     * Prefer {@link #shutdown()} during plugin disable.
     */
    void cancelAll();

    /**
     * Run on the global region / global server tick context.
     * Only for work that does not touch region-owned world or entity state.
     */
    ScheduledTask runGlobal(String taskName, Runnable task);

    /**
     * Delayed global work.
     * @param delayTicks delay in server ticks
     */
    ScheduledTask runGlobalLater(String taskName, Runnable task, long delayTicks);

    /**
     * Repeating global work.
     */
    ScheduledTask runGlobalTimer(String taskName, Runnable task, long delayTicks, long periodTicks);

    /**
     * Run on the region that owns the given world location.
     * @param worldName world name
     * @param blockX block X
     * @param blockY block Y
     * @param blockZ block Z
     */
    ScheduledTask runAtLocation(
            String taskName,
            String worldName,
            int blockX,
            int blockY,
            int blockZ,
            Runnable task
    );

    /**
     * Run on the scheduler that owns the given player/entity.
     * @param entityId player/entity UUID
     * @param retired invoked if the entity retires before or while scheduling
     */
    ScheduledTask runForEntity(String taskName, UUID entityId, Runnable task, Runnable retired);

    /**
     * Delayed entity-owned work.
     * @param delayTicks delay in server ticks
     */
    ScheduledTask runForEntityLater(
            String taskName,
            UUID entityId,
            Runnable task,
            Runnable retired,
            long delayTicks
    );

    /**
     * Execute entity-owned work immediately when already on the owning region,
     * otherwise schedule it. Never runs entity work on an unrelated thread.
     */
    void executeForEntity(String taskName, UUID entityId, Runnable task, Runnable retired);

    /**
     * Asynchronous work that must not touch Bukkit region/entity state.
     */
    ScheduledTask runAsync(String taskName, Runnable task);

    /**
     * Delayed asynchronous work.
     */
    ScheduledTask runAsyncLater(String taskName, Runnable task, long delay, TimeUnit unit);

    /**
     * Repeating asynchronous work.
     */
    ScheduledTask runAsyncTimer(String taskName, Runnable task, long delay, long period, TimeUnit unit);
}
