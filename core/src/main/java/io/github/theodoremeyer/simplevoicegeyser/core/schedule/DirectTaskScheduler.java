package io.github.theodoremeyer.simplevoicegeyser.core.schedule;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * Non-region scheduler used by Fabric and unit tests.
 * <p>
 * Global/entity tasks run on the calling thread (or a dedicated sync executor when
 * configured). Async tasks use a scheduler-owned executor. This preserves the
 * previous Fabric behavior of invoking player APIs directly from the caller.
 */
public final class DirectTaskScheduler implements TaskScheduler {

    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final Set<ScheduledTask> tracked = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService asyncExecutor;
    private final Predicate<UUID> entityPresent;
    private final boolean ownsAsyncExecutor;

    /**
     * Create a scheduler with default async executor and all entities considered present.
     */
    public DirectTaskScheduler() {
        this(uuid -> true, Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SVG-DirectScheduler");
            t.setDaemon(true);
            return t;
        }), true);
    }

    /**
     * @param entityPresent returns whether an entity id can currently accept work
     * @param asyncExecutor executor for async tasks
     * @param ownsAsyncExecutor whether {@link #shutdown()} should terminate the executor
     */
    public DirectTaskScheduler(
            Predicate<UUID> entityPresent,
            ScheduledExecutorService asyncExecutor,
            boolean ownsAsyncExecutor
    ) {
        this.entityPresent = entityPresent == null ? uuid -> true : entityPresent;
        this.asyncExecutor = asyncExecutor;
        this.ownsAsyncExecutor = ownsAsyncExecutor;
    }

    @Override
    public boolean isAcceptingTasks() {
        return accepting.get();
    }

    @Override
    public boolean isRegionThreaded() {
        return false;
    }

    @Override
    public void shutdown() {
        if (!accepting.compareAndSet(true, false)) {
            cancelAll();
            return;
        }
        cancelAll();
        if (ownsAsyncExecutor) {
            asyncExecutor.shutdownNow();
        }
    }

    @Override
    public void cancelAll() {
        for (ScheduledTask task : tracked) {
            task.cancel();
        }
        tracked.clear();
    }

    @Override
    public ScheduledTask runGlobal(String taskName, Runnable task) {
        return runNow(taskName, task);
    }

    @Override
    public ScheduledTask runGlobalLater(String taskName, Runnable task, long delayTicks) {
        long delayMs = Math.max(0L, delayTicks) * 50L;
        return runAsyncLater(taskName, task, delayMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public ScheduledTask runGlobalTimer(String taskName, Runnable task, long delayTicks, long periodTicks) {
        long delayMs = Math.max(0L, delayTicks) * 50L;
        long periodMs = Math.max(1L, periodTicks) * 50L;
        return runAsyncTimer(taskName, task, delayMs, periodMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public ScheduledTask runAtLocation(
            String taskName,
            String worldName,
            int blockX,
            int blockY,
            int blockZ,
            Runnable task
    ) {
        return runNow(taskName, task);
    }

    @Override
    public ScheduledTask runForEntity(String taskName, UUID entityId, Runnable task, Runnable retired) {
        if (!isAcceptingTasks()) {
            return CancelledTask.INSTANCE;
        }
        if (entityId == null || !entityPresent.test(entityId)) {
            runRetired(retired);
            return CancelledTask.INSTANCE;
        }
        return runNow(taskName, task);
    }

    @Override
    public ScheduledTask runForEntityLater(
            String taskName,
            UUID entityId,
            Runnable task,
            Runnable retired,
            long delayTicks
    ) {
        if (!isAcceptingTasks()) {
            return CancelledTask.INSTANCE;
        }
        if (entityId == null || !entityPresent.test(entityId)) {
            runRetired(retired);
            return CancelledTask.INSTANCE;
        }
        return runGlobalLater(taskName, () -> {
            if (!entityPresent.test(entityId)) {
                runRetired(retired);
                return;
            }
            new NamedTask(taskName, task).run();
        }, delayTicks);
    }

    @Override
    public void executeForEntity(String taskName, UUID entityId, Runnable task, Runnable retired) {
        runForEntity(taskName, entityId, task, retired);
    }

    @Override
    public ScheduledTask runAsync(String taskName, Runnable task) {
        if (!isAcceptingTasks()) {
            return CancelledTask.INSTANCE;
        }
        NamedTask named = new NamedTask(taskName, task);
        Future<?> future = asyncExecutor.submit(named);
        return track(new FutureTask(future));
    }

    @Override
    public ScheduledTask runAsyncLater(String taskName, Runnable task, long delay, TimeUnit unit) {
        if (!isAcceptingTasks()) {
            return CancelledTask.INSTANCE;
        }
        NamedTask named = new NamedTask(taskName, task);
        ScheduledFuture<?> future = asyncExecutor.schedule(named, delay, unit);
        return track(new FutureTask(future));
    }

    @Override
    public ScheduledTask runAsyncTimer(
            String taskName,
            Runnable task,
            long delay,
            long period,
            TimeUnit unit
    ) {
        if (!isAcceptingTasks()) {
            return CancelledTask.INSTANCE;
        }
        NamedTask named = new NamedTask(taskName, task);
        ScheduledFuture<?> future = asyncExecutor.scheduleAtFixedRate(named, delay, period, unit);
        return track(new FutureTask(future));
    }

    private ScheduledTask runNow(String taskName, Runnable task) {
        if (!isAcceptingTasks()) {
            return CancelledTask.INSTANCE;
        }
        SimpleScheduledTask handle = new SimpleScheduledTask();
        tracked.add(handle);
        if (!handle.isCancelled()) {
            new NamedTask(taskName, task).run();
        }
        tracked.remove(handle);
        return handle;
    }

    private ScheduledTask track(ScheduledTask task) {
        tracked.add(task);
        return task;
    }

    private void runRetired(Runnable retired) {
        if (retired == null) {
            return;
        }
        try {
            retired.run();
        } catch (Throwable ignored) {
        }
    }

    private final class FutureTask implements ScheduledTask {
        private final Future<?> future;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        private FutureTask(Future<?> future) {
            this.future = future;
        }

        @Override
        public void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                future.cancel(false);
                tracked.remove(this);
            }
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get() || future.isCancelled();
        }
    }
}
