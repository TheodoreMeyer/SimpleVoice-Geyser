package io.github.theodoremeyer.simplevoicegeyser.fabric.hooks;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;

public class LuckPermsHook {

    private final Object api;
    private final boolean available;

    public LuckPermsHook() {
        Object resolved = null;
        boolean success = false;

        try {
            Class<?> provider = Class.forName("net.luckperms.api.LuckPermsProvider");
            Method getMethod = provider.getMethod("get");

            resolved = getMethod.invoke(null);
            success = resolved != null;

            if (success) {
                SvgCore.getLogger().info("[Perms] Hooked into LuckPerms.");
            }
        } catch (Throwable t) {
            SvgCore.getLogger().warning("[Perms] LuckPerms not found, using fallback.");
        }

        this.api = resolved;
        this.available = success;
    }

    public boolean hasPermission(ServerPlayer player, String permission) {
        if (!available) {
            SvgCore.getLogger().warning("[Perms] No permission API available, defaulting to true.");
            return true;
        }

        try {
            return Internal.check(api, player, permission);
        } catch (Throwable e) {
            SvgCore.getLogger().error("[Perms] Failed to check permission '" 
                    + permission + "' for " + player.getName() + ", defaulting to true.", e);
            return true;
        }
    }

    /**
     * Safe because we already verified LuckPerms exists.
     */
    private static class Internal {

        private static boolean check(Object apiObj, ServerPlayer player, String permission) {
            LuckPerms api = (LuckPerms) apiObj;

            User user = api.getUserManager().getUser(player.getUUID());

            if (user == null) {
                SvgCore.getLogger().warning("[Perms] LuckPerms user not loaded, defaulting to true.");
                return true;
            }

            return user.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
        }
    }
}