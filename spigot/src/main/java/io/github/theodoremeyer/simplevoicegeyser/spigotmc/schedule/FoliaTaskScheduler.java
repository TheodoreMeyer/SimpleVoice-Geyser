package io.github.theodoremeyer.simplevoicegeyser.spigotmc.schedule;

import io.github.theodoremeyer.simplevoicegeyser.core.schedule.CancelledTask;
import io.github.theodoremeyer.simplevoicegeyser.core.schedule.NamedTask;
import io.github.theodoremeyer.simplevoicegeyser.core.schedule.ScheduledTask;
import io.github.theodoremeyer.simplevoicegeyser.core.schedule.TaskScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Folia/Paper/Canvas region scheduler adapter.
 * <p>
 * Uses reflection against Paper's region scheduler API so Spigot classloading
 * does not hard-link Folia-only types. On Paper, these schedulers map to the
 * global tick thread; on Folia/Canvas they honor region ownership.
 * <p>
 * Canvas-specific profiling APIs are intentionally not used so this plugin is
 * never Canvas-only. Task attribution relies on the owning {@link Plugin} plus
 * stable {@link NamedTask} paths visible in Folia/Canvas profiler stacks.
 */
public final class FoliaTaskScheduler implements TaskScheduler {

    private final JavaPlugin plugin;
    private final boolean regionThreaded;
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final Set<ReflectiveScheduledTask> tracked = ConcurrentHashMap.newKeySet();

    private final Object globalScheduler;
    private final Object regionScheduler;
    private final Object asyncScheduler;

    private final Method globalExecute;
    private final Method globalRun;
    private final Method globalRunDelayed;
    private final Method globalRunAtFixedRate;
    private final Method globalCancelTasks;

    private final Method regionExecuteLocation;

    private final Method asyncRunNow;
    private final Method asyncRunDelayed;
    private final Method asyncRunAtFixedRate;
    private final Method asyncCancelTasks;

    private final Method entityGetScheduler;
    private final Method entitySchedulerExecute;
    private final Method entitySchedulerRun;
    private final Method entitySchedulerRunDelayed;

    private final Method scheduledTaskCancel;
    private final Method isOwnedByCurrentRegion;

    /**
     * @param plugin owning plugin
     */
    public FoliaTaskScheduler(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.regionThreaded = PlatformSchedulers.isRegionThreadedRuntime();

        try {
            Object server = Bukkit.getServer();
            Class<?> serverClass = server.getClass();

            globalScheduler = serverClass.getMethod("getGlobalRegionScheduler").invoke(server);
            regionScheduler = serverClass.getMethod("getRegionScheduler").invoke(server);
            asyncScheduler = serverClass.getMethod("getAsyncScheduler").invoke(server);

            Class<?> globalClass = globalScheduler.getClass();
            globalExecute = findMethod(globalClass, "execute", Plugin.class, Runnable.class);
            globalRun = findMethod(globalClass, "run", Plugin.class, Consumer.class);
            globalRunDelayed = findMethod(globalClass, "runDelayed", Plugin.class, Consumer.class, long.class);
            globalRunAtFixedRate = findMethod(
                    globalClass,
                    "runAtFixedRate",
                    Plugin.class,
                    Consumer.class,
                    long.class,
                    long.class
            );
            globalCancelTasks = findMethod(globalClass, "cancelTasks", Plugin.class);

            Class<?> regionClass = regionScheduler.getClass();
            regionExecuteLocation = findMethod(
                    regionClass,
                    "execute",
                    Plugin.class,
                    Location.class,
                    Runnable.class
            );

            Class<?> asyncClass = asyncScheduler.getClass();
            asyncRunNow = findMethod(asyncClass, "runNow", Plugin.class, Consumer.class);
            asyncRunDelayed = findMethod(
                    asyncClass,
                    "runDelayed",
                    Plugin.class,
                    Consumer.class,
                    long.class,
                    TimeUnit.class
            );
            asyncRunAtFixedRate = findMethod(
                    asyncClass,
                    "runAtFixedRate",
                    Plugin.class,
                    Consumer.class,
                    long.class,
                    long.class,
                    TimeUnit.class
            );
            asyncCancelTasks = findMethod(asyncClass, "cancelTasks", Plugin.class);

            // Resolve from the Paper/Folia EntityScheduler type when present so we do not
            // depend on compile-time Entity#getScheduler being declared by spigot-api.
            Method resolvedEntityGetScheduler;
            Class<?> entitySchedulerType;
            try {
                entitySchedulerType = Class.forName(
                        "io.papermc.paper.threadedregions.scheduler.EntityScheduler"
                );
                resolvedEntityGetScheduler = Entity.class.getMethod("getScheduler");
            } catch (ClassNotFoundException | NoSuchMethodException ex) {
                resolvedEntityGetScheduler = findMethod(Entity.class, "getScheduler");
                entitySchedulerType = resolvedEntityGetScheduler.getReturnType();
            }
            entityGetScheduler = resolvedEntityGetScheduler;
            entitySchedulerExecute = findMethod(
                    entitySchedulerType,
                    "execute",
                    Plugin.class,
                    Runnable.class,
                    Runnable.class,
                    long.class
            );
            entitySchedulerRun = findMethod(
                    entitySchedulerType,
                    "run",
                    Plugin.class,
                    Consumer.class,
                    Runnable.class
            );
            entitySchedulerRunDelayed = findMethod(
                    entitySchedulerType,
                    "runDelayed",
                    Plugin.class,
                    Consumer.class,
                    Runnable.class,
                    long.class
            );

            Method cancel = null;
            try {
                Class<?> scheduledTaskClass = Class.forName(
                        "io.papermc.paper.threadedregions.scheduler.ScheduledTask"
                );
                cancel = scheduledTaskClass.getMethod("cancel");
            } catch (ClassNotFoundException ignored) {
            }
            scheduledTaskCancel = cancel;

            Method owned = null;
            try {
                owned = serverClass.getMethod("isOwnedByCurrentRegion", Entity.class);
            } catch (NoSuchMethodException ignored) {
            }
            isOwnedByCurrentRegion = owned;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to bind Folia/Paper region schedulers", e);
        }
    }

    @Override
    public boolean isAcceptingTasks() {
        // Do not gate on JavaPlugin#isEnabled(): onDisable may run after the plugin
        // is marked disabled, and we still need to finish entity-owned cleanup.
        return accepting.get();
    }

    @Override
    public boolean isRegionThreaded() {
        return regionThreaded;
    }

    @Override
    public void shutdown() {
        accepting.set(false);
        cancelAll();
        try {
            if (globalCancelTasks != null) {
                globalCancelTasks.invoke(globalScheduler, plugin);
            }
            if (asyncCancelTasks != null) {
                asyncCancelTasks.invoke(asyncScheduler, plugin);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void cancelAll() {
        for (ReflectiveScheduledTask task : tracked) {
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
        try {
            if (isOwnedByGlobalRegion()) {
                named.run();
                return CancelledTask.INSTANCE;
            }
            if (globalExecute != null) {
                globalExecute.invoke(globalScheduler, plugin, named);
                return CancelledTask.INSTANCE;
            }
            Object foliaTask = globalRun.invoke(globalScheduler, plugin, asConsumer(named));
            return track(foliaTask);
        } catch (Throwable t) {
            throw new IllegalStateException("Failed to schedule global task: " + taskName, t);
        }
    }

    @Override
    public ScheduledTask runGlobalLater(String taskName, Runnable task, long delayTicks) {
        if (!isAcceptingTasks()) {
            return CancelledTask.INSTANCE;
        }
        try {
            Object foliaTask = globalRunDelayed.invoke(
                    globalScheduler,
                    plugin,
                    asConsumer(new NamedTask(taskName, task)),
                    Math.max(1L, delayTicks)
            );
            return track(foliaTask);
        } catch (Throwable t) {
            throw new IllegalStateException("Failed to schedule delayed global task: " + taskName, t);
        }
    }

    @Override
    public ScheduledTask runGlobalTimer(String taskName, Runnable task, long delayTicks, long periodTicks) {
        if (!isAcceptingTasks()) {
            return CancelledTask.INSTANCE;
        }
        try {
            Object foliaTask = globalRunAtFixedRate.invoke(
                    globalScheduler,
                    plugin,
                    asConsumer(new NamedTask(taskName, task)),
                    Math.max(1L, delayTicks),
                    Math.max(1L, periodTicks)
            );
            return track(foliaTask);
        } catch (Throwable t) {
            throw new IllegalStateException("Failed to schedule repeating global task: " + taskName, t);
        }
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
        if (!isAcceptingTasks()) {
            return CancelledTask.INSTANCE;
        }
        World world = worldName == null ? null : Bukkit.getWorld(worldName);
        if (world == null) {
            return CancelledTask.INSTANCE;
        }
        Location location = new Location(world, blockX, blockY, blockZ);
        NamedTask named = new NamedTask(taskName, task);
        try {
            regionExecuteLocation.invoke(regionScheduler, plugin, location, named);
            return CancelledTask.INSTANCE;
        } catch (Throwable t) {
            throw new IllegalStateException("Failed to schedule region task: " + taskName, t);
        }
    }

    @Override
    public ScheduledTask runForEntity(String taskName, UUID entityId, Runnable task, Runnable retired) {
        if (!isAcceptingTasks()) {
            return CancelledTask.INSTANCE;
        }
        Player player = resolvePlayer(entityId);
        if (player == null) {
            runRetired(retired);
            return CancelledTask.INSTANCE;
        }
        NamedTask named = new NamedTask(taskName, wrapEntityTask(entityId, task, retired));
        try {
            Object entityScheduler = entityGetScheduler.invoke(player);
            Object foliaTask = entitySchedulerRun.invoke(
                    entityScheduler,
                    plugin,
                    asConsumer(named),
                    retiredRunnable(retired)
            );
            if (foliaTask == null) {
                runRetired(retired);
                return CancelledTask.INSTANCE;
            }
            return track(foliaTask);
        } catch (Throwable t) {
            throw new IllegalStateException("Failed to schedule entity task: " + taskName, t);
        }
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
        Player player = resolvePlayer(entityId);
        if (player == null) {
            runRetired(retired);
            return CancelledTask.INSTANCE;
        }
        NamedTask named = new NamedTask(taskName, wrapEntityTask(entityId, task, retired));
        try {
            Object entityScheduler = entityGetScheduler.invoke(player);
            Object foliaTask = entitySchedulerRunDelayed.invoke(
                    entityScheduler,
                    plugin,
                    asConsumer(named),
                    retiredRunnable(retired),
                    Math.max(1L, delayTicks)
            );
            if (foliaTask == null) {
                runRetired(retired);
                return CancelledTask.INSTANCE;
            }
            return track(foliaTask);
        } catch (Throwable t) {
            throw new IllegalStateException("Failed to schedule delayed entity task: " + taskName, t);
        }
    }

    @Override
    public void executeForEntity(String taskName, UUID entityId, Runnable task, Runnable retired) {
        if (!isAcceptingTasks()) {
            return;
        }
        Player player = resolvePlayer(entityId);
        if (player == null) {
            runRetired(retired);
            return;
        }
        NamedTask named = new NamedTask(taskName, wrapEntityTask(entityId, task, retired));
        try {
            if (ownsEntity(player)) {
                named.run();
                return;
            }
            Object entityScheduler = entityGetScheduler.invoke(player);
            Boolean scheduled = (Boolean) entitySchedulerExecute.invoke(
                    entityScheduler,
                    plugin,
                    named,
                    retiredRunnable(retired),
                    0L
            );
            if (Boolean.FALSE.equals(scheduled)) {
                runRetired(retired);
            }
        } catch (Throwable t) {
            throw new IllegalStateException("Failed to execute entity task: " + taskName, t);
        }
    }

    @Override
    public ScheduledTask runAsync(String taskName, Runnable task) {
        if (!isAcceptingTasks()) {
            return CancelledTask.INSTANCE;
        }
        try {
            Object foliaTask = asyncRunNow.invoke(
                    asyncScheduler,
                    plugin,
                    asConsumer(new NamedTask(taskName, task))
            );
            return track(foliaTask);
        } catch (Throwable t) {
            throw new IllegalStateException("Failed to schedule async task: " + taskName, t);
        }
    }

    @Override
    public ScheduledTask runAsyncLater(String taskName, Runnable task, long delay, TimeUnit unit) {
        if (!isAcceptingTasks()) {
            return CancelledTask.INSTANCE;
        }
        try {
            Object foliaTask = asyncRunDelayed.invoke(
                    asyncScheduler,
                    plugin,
                    asConsumer(new NamedTask(taskName, task)),
                    Math.max(0L, delay),
                    unit
            );
            return track(foliaTask);
        } catch (Throwable t) {
            throw new IllegalStateException("Failed to schedule delayed async task: " + taskName, t);
        }
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
        try {
            Object foliaTask = asyncRunAtFixedRate.invoke(
                    asyncScheduler,
                    plugin,
                    asConsumer(new NamedTask(taskName, task)),
                    Math.max(0L, delay),
                    Math.max(1L, period),
                    unit
            );
            return track(foliaTask);
        } catch (Throwable t) {
            throw new IllegalStateException("Failed to schedule repeating async task: " + taskName, t);
        }
    }

    private Runnable wrapEntityTask(UUID entityId, Runnable task, Runnable retired) {
        return () -> {
            Player online = resolvePlayer(entityId);
            if (online == null) {
                runRetired(retired);
                return;
            }
            task.run();
        };
    }

    private Player resolvePlayer(UUID entityId) {
        if (entityId == null) {
            return null;
        }
        Player player = Bukkit.getPlayer(entityId);
        if (player == null || !player.isOnline()) {
            return null;
        }
        return player;
    }

    private boolean ownsEntity(Entity entity) {
        if (isOwnedByCurrentRegion == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(isOwnedByCurrentRegion.invoke(Bukkit.getServer(), entity));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isOwnedByGlobalRegion() {
        // On Folia/Canvas always hop through GlobalRegionScheduler.
        // On Paper (same API, single tick thread), running immediately on the primary thread is safe.
        if (regionThreaded) {
            return false;
        }
        return Bukkit.isPrimaryThread();
    }

    private ScheduledTask track(Object foliaTask) {
        if (foliaTask == null) {
            return CancelledTask.INSTANCE;
        }
        ReflectiveScheduledTask wrapped = new ReflectiveScheduledTask(foliaTask);
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

    private Runnable retiredRunnable(Runnable retired) {
        if (retired == null) {
            return null;
        }
        return () -> runRetired(retired);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Consumer asConsumer(Runnable runnable) {
        return task -> runnable.run();
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... params)
            throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name, params);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                try {
                    return current.getMethod(name, params);
                } catch (NoSuchMethodException ignoredAgain) {
                    current = current.getSuperclass();
                }
            }
        }
        // Interfaces (Paper returns interface types from getMethod on Server)
        for (Class<?> iface : type.getInterfaces()) {
            try {
                return iface.getMethod(name, params);
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException(type.getName() + "#" + name);
    }

    private final class ReflectiveScheduledTask implements ScheduledTask {
        private final Object handle;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        private ReflectiveScheduledTask(Object handle) {
            this.handle = handle;
        }

        @Override
        public void cancel() {
            if (!cancelled.compareAndSet(false, true)) {
                return;
            }
            try {
                if (scheduledTaskCancel != null) {
                    scheduledTaskCancel.invoke(handle);
                } else {
                    Method cancel = handle.getClass().getMethod("cancel");
                    cancel.invoke(handle);
                }
            } catch (Throwable ignored) {
            }
            tracked.remove(this);
        }

        @Override
        public boolean isCancelled() {
            if (cancelled.get()) {
                return true;
            }
            try {
                Method method = handle.getClass().getMethod("isCancelled");
                Object value = method.invoke(handle);
                return Boolean.TRUE.equals(value);
            } catch (Throwable ignored) {
                return cancelled.get();
            }
        }
    }
}
