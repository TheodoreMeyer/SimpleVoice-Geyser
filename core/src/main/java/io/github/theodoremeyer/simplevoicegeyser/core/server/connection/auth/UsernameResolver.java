package io.github.theodoremeyer.simplevoicegeyser.core.server.connection.auth;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.data.PlayerVcPswd;
import io.github.theodoremeyer.simplevoicegeyser.core.geyser.GeyserHook;
import io.github.theodoremeyer.simplevoicegeyser.core.managers.PlayerManager;

import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves a login username to a player UUID.
 * <p>
 * Order:
 * <ol>
 *   <li>Exact stored username in the password store (case-insensitive)</li>
 *   <li>Exact current online player name (case-insensitive, unique)</li>
 *   <li>Controlled Floodgate alias using the live Floodgate prefix</li>
 * </ol>
 * Ambiguous matches fail closed ({@code null}). Never blindly prepends {@code "."}.
 */
public final class UsernameResolver {

    private UsernameResolver() {}

    /**
     * Resolve a username to a UUID.
     *
     * @param username raw username
     * @return uuid or null if unresolved / ambiguous
     */
    public static UUID resolve(String username) {
        if (username == null) {
            return null;
        }

        String trimmed = username.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        PlayerVcPswd passwords = SvgCore.getPasswordManager();
        if (passwords != null) {
            UUID stored = passwords.getUUID(trimmed);
            if (stored != null) {
                return stored;
            }
        }

        PlayerManager players = SvgCore.getPlayerManager();
        UUID onlineExact = resolveExactOnline(players, trimmed);
        if (onlineExact != null) {
            return onlineExact;
        }

        return resolveFloodgateAlias(players, trimmed);
    }

    private static UUID resolveExactOnline(PlayerManager players, String username) {
        if (players == null) {
            return null;
        }

        Set<UUID> matches = new HashSet<>();
        for (SvgPlayer player : players.getAllPlayers()) {
            if (player == null || player.getName() == null) {
                continue;
            }
            if (player.getName().equalsIgnoreCase(username)) {
                matches.add(player.getUniqueId());
            }
        }

        if (matches.size() == 1) {
            return matches.iterator().next();
        }
        return null;
    }

    private static UUID resolveFloodgateAlias(PlayerManager players, String username) {
        if (players == null) {
            return null;
        }

        Optional<String> prefixOpt = GeyserHook.getFloodgatePrefix();
        if (prefixOpt.isEmpty()) {
            return null;
        }

        String prefix = prefixOpt.get();
        if (prefix.isEmpty()) {
            return null;
        }

        Set<UUID> matches = new HashSet<>();
        String lowerInput = username.toLowerCase(Locale.ROOT);
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);

        for (SvgPlayer player : players.getAllPlayers()) {
            if (player == null || player.getName() == null) {
                continue;
            }

            String onlineName = player.getName();
            String lowerOnline = onlineName.toLowerCase(Locale.ROOT);

            // Input is gamertag; online name is prefix+gamertag
            if (lowerOnline.equals(lowerPrefix + lowerInput)) {
                matches.add(player.getUniqueId());
                continue;
            }

            // Input already includes prefix; online name is bare gamertag
            if (lowerInput.startsWith(lowerPrefix)) {
                String strippedInput = username.substring(prefix.length());
                if (!strippedInput.isEmpty() && onlineName.equalsIgnoreCase(strippedInput)) {
                    matches.add(player.getUniqueId());
                    continue;
                }
            }

            // Online name has prefix; input is bare gamertag matching stripped online name
            if (lowerOnline.startsWith(lowerPrefix)) {
                String strippedOnline = onlineName.substring(prefix.length());
                if (!strippedOnline.isEmpty() && strippedOnline.equalsIgnoreCase(username)) {
                    matches.add(player.getUniqueId());
                }
            }
        }

        if (matches.size() == 1) {
            return matches.iterator().next();
        }
        return null;
    }
}
