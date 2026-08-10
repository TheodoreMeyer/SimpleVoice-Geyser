package io.github.theodoremeyer.simplevoicegeyser.core.schedule;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * Test scheduler that can defer entity-owned work until explicitly flushed.
 */
public final class ControllableTaskScheduler implements TaskScheduler {

    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final AtomicBoolean deferEntityTasks = new AtomicBoolean(false);
    private final List<DeferredEntityTask> deferred = new CopyOnWriteArrayList<>();
    private final Predicate<UUID> entityPresent;
    private final AtomicBoolean rejectEntitySchedule = new AtomicBoolean(false);

    public ControllableTaskScheduler() {
        this(uuid -> true);
    }

    public ControllableTaskScheduler(Predicate<UUID> entityPresent) {
        this.entityPresent = entityPresent == null ? uuid -> true : entityPresent;
    }

    public void setDeferEntityTasks(boolean defer) {
        deferEntityTasks.set(defer);
    }

    public void setRejectEntitySchedule(boolean reject) {
        rejectEntitySchedule.set(reject);
    }

    public int deferredCount() {
        return deferred.size();
    }

    public void flushDeferredEntityTasks() {
        List<DeferredEntityTask> snapshot = new ArrayList<>(deferred);
        deferred.clear();
        for (DeferredEntityTask task : snapshot) {
            if (!accepting.get()) {
                runRetired(task.retired);
                continue;
            }
            if (!entityPresent.test(task.entityId)) {
                runRetired(task.retired);
                continue;
            }
            new NamedTask(task.taskName, task.task).run();
        }
    }

    @Override
    public boolean isAcceptingTasks() {
        return accepting.get();
    }

    @Override
    public boolean isRegionThreaded() {
        return true;
    }

    @Override
    public void shutdown() {
        accepting.set(false);
        cancelAll();
    }

    @Override
    public void cancelAll() {
        for (DeferredEntityTask task : deferred) {
            runRetired(task.retired);
        }
        deferred.clear();
    }

    @Override
    public ScheduledTask runGlobal(String taskName, Runnable task) {
        if (!accepting.get()) {
            return CancelledTask.INSTANCE;
        }
        new NamedTask(taskName, task).run();
        return new SimpleScheduledTask();
    }

    @Override
    public ScheduledTask runGlobalLater(String taskName, Runnable task, long delayTicks) {
        return runGlobal(taskName, task);
    }

    @Override
    public ScheduledTask runGlobalTimer(String taskName, Runnable task, long delayTicks, long periodTicks) {
        return runGlobal(taskName, task);
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
        return runGlobal(taskName, task);
    }

    @Override
    public ScheduledTask runForEntity(String taskName, UUID entityId, Runnable task, Runnable retired) {
        if (!accepting.get() || rejectEntitySchedule.get()) {
            runRetired(retired);
            return CancelledTask.INSTANCE;
        }
        if (entityId == null || !entityPresent.test(entityId)) {
            runRetired(retired);
            return CancelledTask.INSTANCE;
        }
        if (deferEntityTasks.get()) {
            deferred.add(new DeferredEntityTask(taskName, entityId, task, retired));
            return new SimpleScheduledTask();
        }
        new NamedTask(taskName, task).run();
        return new SimpleScheduledTask();
    }

    @Override
    public ScheduledTask runForEntityLater(
            String taskName,
            UUID entityId,
            Runnable task,
            Runnable retired,
            long delayTicks
    ) {
        return runForEntity(taskName, entityId, task, retired);
    }

    @Override
    public void executeForEntity(String taskName, UUID entityId, Runnable task, Runnable retired) {
        runForEntity(taskName, entityId, task, retired);
    }

    @Override
    public ScheduledTask runAsync(String taskName, Runnable task) {
        return runGlobal(taskName, task);
    }

    @Override
    public ScheduledTask runAsyncLater(String taskName, Runnable task, long delay, TimeUnit unit) {
        return runGlobal(taskName, task);
    }

    @Override
    public ScheduledTask runAsyncTimer(
            String taskName,
            Runnable task,
            long delay,
            long period,
            TimeUnit unit
    ) {
        return runGlobal(taskName, task);
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

    private record DeferredEntityTask(
            String taskName,
            UUID entityId,
            Runnable task,
            Runnable retired
    ) {}
}
