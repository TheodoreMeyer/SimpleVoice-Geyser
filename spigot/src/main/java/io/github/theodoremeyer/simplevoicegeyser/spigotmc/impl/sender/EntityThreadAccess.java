package io.github.theodoremeyer.simplevoicegeyser.spigotmc.impl.sender;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Runs work on the player's region/entity thread when Folia/Paper EntityScheduler is present,
 * otherwise falls back to the Bukkit global scheduler (Spigot-safe).
 * <p>
 * Paper/Folia APIs are accessed only via reflection so this module can compile against Spigot API.
 */
final class EntityThreadAccess {

    private static final long DEFAULT_TIMEOUT_SECONDS = 3L;

    private static final boolean FOLIA_ENTITY_SCHEDULER;
    private static final Method ENTITY_GET_SCHEDULER;
    private static final Method SCHEDULER_RUN;
    private static final Method SCHEDULER_RUN_AT_FIXED_RATE;
    private static final Method TASK_CANCEL;
    private static final Method IS_OWNED_BY_CURRENT_REGION;

    static {
        Method getScheduler = null;
        Method run = null;
        Method runAtFixedRate = null;
        Method cancel = null;
        Method owned = null;
        boolean folia = false;
        try {
            getScheduler = Entity.class.getMethod("getScheduler");
            Class<?> entityScheduler = getScheduler.getReturnType();
            // Folia/Paper: run(Plugin, Consumer<ScheduledTask>, Runnable retired)
            run = entityScheduler.getMethod("run", Plugin.class, Consumer.class, Runnable.class);
            runAtFixedRate = entityScheduler.getMethod(
                    "runAtFixedRate",
                    Plugin.class,
                    Consumer.class,
                    Runnable.class,
                    long.class,
                    long.class
            );
            Class<?> scheduledTask = runAtFixedRate.getReturnType();
            cancel = scheduledTask.getMethod("cancel");
            try {
                owned = Bukkit.class.getMethod("isOwnedByCurrentRegion", Entity.class);
            } catch (NoSuchMethodException ignored) {
                owned = null;
            }
            folia = true;
        } catch (ReflectiveOperationException ignored) {
            folia = false;
        }
        FOLIA_ENTITY_SCHEDULER = folia;
        ENTITY_GET_SCHEDULER = getScheduler;
        SCHEDULER_RUN = run;
        SCHEDULER_RUN_AT_FIXED_RATE = runAtFixedRate;
        TASK_CANCEL = cancel;
        IS_OWNED_BY_CURRENT_REGION = owned;
    }

    private EntityThreadAccess() {}

    static boolean isOnEntityThread(Player player) {
        if (FOLIA_ENTITY_SCHEDULER && IS_OWNED_BY_CURRENT_REGION != null) {
            try {
                Object result = IS_OWNED_BY_CURRENT_REGION.invoke(null, player);
                return Boolean.TRUE.equals(result);
            } catch (ReflectiveOperationException ignored) {
                // fall through
            }
        }
        return Bukkit.isPrimaryThread();
    }

    static void run(Player player, Plugin plugin, Runnable task) {
        if (!player.isOnline() || plugin == null) {
            return;
        }
        if (isOnEntityThread(player)) {
            task.run();
            return;
        }
        if (FOLIA_ENTITY_SCHEDULER) {
            try {
                Object scheduler = ENTITY_GET_SCHEDULER.invoke(player);
                SCHEDULER_RUN.invoke(
                        scheduler,
                        plugin,
                        (Consumer<Object>) scheduled -> task.run(),
                        null
                );
                return;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Failed to schedule on entity thread", e);
            }
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    static <T> T supply(Player player, Plugin plugin, Supplier<T> supplier) throws Exception {
        if (!player.isOnline()) {
            return supplier.get();
        }
        if (isOnEntityThread(player)) {
            return supplier.get();
        }
        if (plugin == null) {
            return supplier.get();
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        if (FOLIA_ENTITY_SCHEDULER) {
            try {
                Object scheduler = ENTITY_GET_SCHEDULER.invoke(player);
                SCHEDULER_RUN.invoke(
                        scheduler,
                        plugin,
                        (Consumer<Object>) scheduled -> {
                            try {
                                future.complete(supplier.get());
                            } catch (Throwable t) {
                                future.completeExceptionally(t);
                            }
                        },
                        (Runnable) () -> future.completeExceptionally(
                                new IllegalStateException("Player retired before entity task ran")
                        )
                );
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Failed to schedule on entity thread", e);
            }
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    future.complete(supplier.get());
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            });
        }
        return future.get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Starts a fixed-rate task on the entity scheduler (Folia) or global scheduler (Spigot).
     *
     * @return opaque handle that can be cancelled via {@link #cancel(Object)}
     */
    static Object runAtFixedRate(
            Player player,
            Plugin plugin,
            Runnable task,
            long initialDelayTicks,
            long periodTicks
    ) {
        if (!player.isOnline() || plugin == null) {
            return null;
        }
        if (FOLIA_ENTITY_SCHEDULER) {
            try {
                Object scheduler = ENTITY_GET_SCHEDULER.invoke(player);
                return SCHEDULER_RUN_AT_FIXED_RATE.invoke(
                        scheduler,
                        plugin,
                        (Consumer<Object>) scheduled -> task.run(),
                        null,
                        initialDelayTicks,
                        periodTicks
                );
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Failed to schedule fixed-rate entity task", e);
            }
        }
        return Bukkit.getScheduler().runTaskTimer(plugin, task, initialDelayTicks, periodTicks);
    }

    static void cancel(Object taskHandle) {
        if (taskHandle == null) {
            return;
        }
        if (taskHandle instanceof BukkitTask bukkitTask) {
            bukkitTask.cancel();
            return;
        }
        if (FOLIA_ENTITY_SCHEDULER && TASK_CANCEL != null) {
            try {
                TASK_CANCEL.invoke(taskHandle);
            } catch (ReflectiveOperationException ignored) {
                // already cancelled / wrong type
            }
        }
    }

    /** Convenience holder for spatial-cache tasks. */
    static final class Cancellable {
        private final AtomicReference<Object> handle = new AtomicReference<>();

        void set(Object task) {
            Object previous = handle.getAndSet(task);
            EntityThreadAccess.cancel(previous);
        }

        void cancel() {
            EntityThreadAccess.cancel(handle.getAndSet(null));
        }
    }
}
