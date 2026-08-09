package io.github.theodoremeyer.simplevoicegeyser.core.schedule;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectTaskSchedulerTest {

    @Test
    void selectsNonRegionThreadedBackend() {
        DirectTaskScheduler scheduler = new DirectTaskScheduler();
        assertFalse(scheduler.isRegionThreaded());
        assertTrue(scheduler.isAcceptingTasks());
        scheduler.shutdown();
    }

    @Test
    void cancelAsyncTaskPreventsExecution() throws Exception {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        try {
            DirectTaskScheduler scheduler = new DirectTaskScheduler(uuid -> true, executor, false);
            AtomicBoolean ran = new AtomicBoolean(false);
            CountDownLatch started = new CountDownLatch(1);

            ScheduledTask task = scheduler.runAsyncLater("svg/test/cancel", () -> {
                started.countDown();
                ran.set(true);
            }, 200, TimeUnit.MILLISECONDS);

            task.cancel();
            assertTrue(task.isCancelled());
            assertFalse(started.await(350, TimeUnit.MILLISECONDS));
            assertFalse(ran.get());
            scheduler.shutdown();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shutdownRejectsNewWorkAndIsIdempotent() {
        DirectTaskScheduler scheduler = new DirectTaskScheduler();
        AtomicInteger runs = new AtomicInteger();

        scheduler.runGlobal("svg/test/before-shutdown", runs::incrementAndGet);
        assertEquals(1, runs.get());

        scheduler.shutdown();
        scheduler.shutdown();

        assertFalse(scheduler.isAcceptingTasks());
        ScheduledTask rejected = scheduler.runGlobal("svg/test/after-shutdown", runs::incrementAndGet);
        assertTrue(rejected.isCancelled());
        assertEquals(1, runs.get());
    }

    @Test
    void entityRetirementCallbackRunsWhenEntityMissing() {
        UUID missing = UUID.randomUUID();
        DirectTaskScheduler scheduler = new DirectTaskScheduler(uuid -> false, Executors.newSingleThreadScheduledExecutor(), true);
        AtomicBoolean retired = new AtomicBoolean(false);
        AtomicBoolean ran = new AtomicBoolean(false);

        ScheduledTask task = scheduler.runForEntity(
                "svg/test/entity-retired",
                missing,
                () -> ran.set(true),
                () -> retired.set(true)
        );

        assertTrue(task.isCancelled());
        assertTrue(retired.get());
        assertFalse(ran.get());
        scheduler.shutdown();
    }

    @Test
    void entityTaskRunsWhenEntityPresent() {
        UUID present = UUID.randomUUID();
        DirectTaskScheduler scheduler = new DirectTaskScheduler(uuid -> uuid.equals(present), Executors.newSingleThreadScheduledExecutor(), true);
        AtomicBoolean ran = new AtomicBoolean(false);

        scheduler.executeForEntity("svg/test/entity-present", present, () -> ran.set(true), null);

        assertTrue(ran.get());
        scheduler.shutdown();
    }
}
