package io.github.theodoremeyer.simplevoicegeyser.spigotmc.schedule;

import io.github.theodoremeyer.simplevoicegeyser.core.schedule.TaskScheduler;
import org.bukkit.Server;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Selects a Bukkit or Folia/Paper region scheduler adapter at startup.
 * <p>
 * Folia/Paper/Canvas region scheduler types are accessed reflectively so the
 * same artifact loads on classic Spigot without hard runtime linkage to
 * Paper-only classes.
 */
public final class PlatformSchedulers {

    private PlatformSchedulers() {}

    /**
     * Create the appropriate scheduler for the running server.
     * @param plugin owning plugin
     * @return scheduler adapter
     */
    public static TaskScheduler create(JavaPlugin plugin) {
        Server server = plugin.getServer();
        if (hasRegionSchedulers(server)) {
            return new FoliaTaskScheduler(plugin);
        }
        return new BukkitTaskScheduler(plugin);
    }

    /**
     * @param server Bukkit server (or test double exposing the same method names)
     * @return true when GlobalRegionScheduler APIs are present (Paper/Folia/Canvas)
     */
    public static boolean hasRegionSchedulers(Object server) {
        if (server == null) {
            return false;
        }
        try {
            // Reflect on the runtime class (CraftServer / Folia), not the compile-time
            // Server interface from spigot-api, which may omit these methods.
            Class<?> type = server.getClass();
            findPublicMethod(type, "getGlobalRegionScheduler");
            findPublicMethod(type, "getRegionScheduler");
            findPublicMethod(type, "getAsyncScheduler");
            return true;
        } catch (NoSuchMethodException | NoClassDefFoundError ex) {
            return false;
        }
    }

    private static void findPublicMethod(Class<?> type, String name) throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            try {
                current.getMethod(name);
                return;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        for (Class<?> iface : type.getInterfaces()) {
            try {
                iface.getMethod(name);
                return;
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException(type.getName() + "#" + name);
    }

    /**
     * Detect Folia/Canvas-style regionized threading (not plain Paper).
     * Safe on non-Folia servers: uses class name checks only.
     */
    public static boolean isRegionThreadedRuntime() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
        }
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
            // Paper also has these types; brand check distinguishes Folia/Canvas.
            String name = org.bukkit.Bukkit.getServer().getName();
            if (name != null) {
                String lower = name.toLowerCase();
                return lower.contains("folia") || lower.contains("canvas");
            }
        } catch (Throwable ignored) {
        }
        return isFoliaBrand();
    }

    private static boolean isFoliaBrand() {
        try {
            Class<?> buildInfoClass = Class.forName("io.papermc.paper.ServerBuildInfo");
            Object buildInfo = buildInfoClass.getMethod("buildInfo").invoke(null);
            Class<?> keyClass = Class.forName("net.kyori.adventure.key.Key");
            Object foliaKey = keyClass.getMethod("key", String.class, String.class)
                    .invoke(null, "papermc", "folia");
            Object result = buildInfoClass.getMethod("isBrandCompatible", keyClass)
                    .invoke(buildInfo, foliaKey);
            return Boolean.TRUE.equals(result);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
