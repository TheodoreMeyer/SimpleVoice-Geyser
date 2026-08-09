package io.github.theodoremeyer.simplevoicegeyser.spigotmc.schedule;

import io.github.theodoremeyer.simplevoicegeyser.core.schedule.CancelledTask;
import io.github.theodoremeyer.simplevoicegeyser.core.schedule.NamedTask;
import io.github.theodoremeyer.simplevoicegeyser.core.schedule.ScheduledTask;
import io.github.theodoremeyer.simplevoicegeyser.core.schedule.TaskScheduler;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Classic Bukkit/Spigot scheduler adapter (single primary thread).
 */
public final class BukkitTaskScheduler implements TaskScheduler {

    private final JavaPlugin plugin;
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final Set<BukkitScheduledTask> tracked = ConcurrentHashMap.newKeySet();

    /**
     * @param plugin owning plugin
     */
    public BukkitTaskScheduler(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public boolean isAcceptingTasks() {
        // Do not gate on JavaPlugin#isEnabled(): onDisable may run after the plugin
        // is marked disabled, and we still need to finish entity-owned cleanup.
        return accepting.get();
    }

    @Override
    public boolean isRegionThreaded() {
        return false;
    }

    @Override
    public void shutdown() {
        accepting.set(false);
        cancelAll();
        try {
            Bukkit.getScheduler().cancelTasks(plugin);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void cancelAll() {
        for (BukkitScheduledTask task : tracked) {
            task.cancel();
        }
        tracked.clear();
    }

    @Override
    public ScheduledTask runGlobal(String taskName, Runnable task) {
        if (!isAcceptingTasks()) {
            return CancelledTask.INSTANCE;
        }
        NamedTask named = new NamedTask(taskName, task);
        if (Bukkit.isPrimaryThread()) {
            named.run();
            return CancelledTask.INSTANCE;
        }
        return track(Bukkit.getScheduler().runTask(plugin, named));
    }

    @Override
    public ScheduledTask runGlobalLater(String taskName, Runnable task, long delayTicks) {
        if (!isAcceptingTasks()) {
            return CancelledTask.INSTANCE;
        }
        return track(Bukkit.getScheduler().runTaskLater(
                plugin,
                new NamedTask(taskName, task),
                Math.max(0L, delayTicks)
        ));
    }

    @Override
    public ScheduledTask runGlobalTimer(String taskName, Runnable task, long delayTicks, long periodTicks) {
        if (!isAcceptingTasks()) {
            return CancelledTask.INSTANCE;
        }
        return track(Bukkit.getScheduler().runTaskTimer(
                plugin,
                new NamedTask(taskName, task),
                Math.max(0L, delayTicks),
                Math.max(1L, periodTicks)
        ));
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
        // Classic servers have one global tick thread; still validate the world exists.
        if (!isAcceptingTasks()) {
            return CancelledTask.INSTANCE;
        }
        World world = worldName == null ? null : Bukkit.getWorld(worldName);
        if (world == null) {
            return CancelledTask.INSTANCE;
        }
        return runGlobal(taskName, task);
    }

    @Override
    public ScheduledTask runForEntity(String taskName, UUID entityId, Runnable task, Runnable retired) {
        if (!isAcceptingTasks()) {
            return CancelledTask.INSTANCE;
        }
        Player player = entityId == null ? null : Bukkit.getPlayer(entityId);
        if (player == null || !player.isOnline()) {
            runRetired(retired);
            return CancelledTask.INSTANCE;
        }
        return runGlobal(taskName, () -> {
            Player online = Bukkit.getPlayer(entityId);
            if (online == null || !online.isOnline()) {
                runRetired(retired);
                return;
            }
            new NamedTask(taskName, task).run();
        });
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
        return runGlobalLater(taskName, () -> {
            Player online = entityId == null ? null : Bukkit.getPlayer(entityId);
            if (online == null || !online.isOnline()) {
                runRetired(retired);
                return;
            }
            new NamedTask(taskName, task).run();
        }, delayTicks);
    }

    @Override
    public void executeForEntity(String taskName, UUID entityId, Runnable task, Runnable retired) {
        if (!isAcceptingTasks()) {
            return;
        }
        Player player = entityId == null ? null : Bukkit.getPlayer(entityId);
        if (player == null || !player.isOnline()) {
            runRetired(retired);
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            new NamedTask(taskName, task).run();
            return;
        }
        runForEntity(taskName, entityId, task, retired);
    }

    @Override
    public ScheduledTask runAsync(String taskName, Runnable task) {
        if (!isAcceptingTasks()) {
            return CancelledTask.INSTANCE;
        }
        return track(Bukkit.getScheduler().runTaskAsynchronously(plugin, new NamedTask(taskName, task)));
    }

    @Override
    public ScheduledTask runAsyncLater(String taskName, Runnable task, long delay, TimeUnit unit) {
        if (!isAcceptingTasks()) {
            return CancelledTask.INSTANCE;
        }
        long ticks = Math.max(0L, unit.toMillis(delay) / 50L);
        return track(Bukkit.getScheduler().runTaskLaterAsynchronously(
                plugin,
                new NamedTask(taskName, task),
                ticks
        ));
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
        long delayTicks = Math.max(0L, unit.toMillis(delay) / 50L);
        long periodTicks = Math.max(1L, unit.toMillis(period) / 50L);
        return track(Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin,
                new NamedTask(taskName, task),
                delayTicks,
                periodTicks
        ));
    }

    private ScheduledTask track(BukkitTask bukkitTask) {
        BukkitScheduledTask wrapped = new BukkitScheduledTask(bukkitTask);
        tracked.add(wrapped);
        return wrapped;
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

    private final class BukkitScheduledTask implements ScheduledTask {
        private final BukkitTask bukkitTask;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        private BukkitScheduledTask(BukkitTask bukkitTask) {
            this.bukkitTask = bukkitTask;
        }

        @Override
        public void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                try {
                    bukkitTask.cancel();
                } catch (Throwable ignored) {
                }
                tracked.remove(this);
            }
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get() || bukkitTask.isCancelled();
        }
    }
}
