package io.github.theodoremeyer.simplevoicegeyser.core.managers.group;

import de.maxhenkel.voicechat.api.Group;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;

import java.lang.reflect.Field;

/**
 * Verifies group passwords without logging or returning secrets.
 * <p>
 * Preference: {@link Group#hasPassword()} first. SVG-managed BCrypt store next.
 * Otherwise an isolated reflection adapter against SVC internals — fail closed
 * on unknown structures ({@link Result#UNAVAILABLE} / {@link Result#INVALID}).
 */
public final class GroupPasswordVerifier {

    /**
     * Verification outcome.
     */
    public enum Result {
        VALID,
        INVALID,
        UNAVAILABLE
    }

    private final GroupPasswordStore passwordStore;

    /**
     * @param passwordStore SVG-managed hashes
     */
    public GroupPasswordVerifier(GroupPasswordStore passwordStore) {
        this.passwordStore = passwordStore;
    }

    /**
     * Verify a provided password for a group.
     *
     * @param group group
     * @param providedPassword password from the client (may be null/blank)
     * @return result
     */
    public Result verify(Group group, String providedPassword) {
        if (group == null) {
            return Result.INVALID;
        }

        if (!group.hasPassword()) {
            if (providedPassword == null || providedPassword.isBlank()) {
                return Result.VALID;
            }
            // Extra password on an unprotected group is ignored as valid join.
            return Result.VALID;
        }

        if (providedPassword == null || providedPassword.isBlank()) {
            return Result.INVALID;
        }

        if (passwordStore != null && passwordStore.has(group.getId())) {
            return passwordStore.verify(group.getId(), providedPassword)
                    ? Result.VALID
                    : Result.INVALID;
        }

        return verifyViaReflection(group, providedPassword);
    }

    private Result verifyViaReflection(Group group, String providedPassword) {
        try {
            String expected = readSvcPassword(group);
            if (expected == null) {
                // hasPassword() was true but we cannot read the secret → fail closed.
                SvgCore.getLogger().warning(
                        "[GROUPS] Password check unavailable for group id="
                                + group.getId()
                                + " (unknown SVC structure)"
                );
                return Result.UNAVAILABLE;
            }
            return expected.equals(providedPassword) ? Result.VALID : Result.INVALID;
        } catch (Throwable t) {
            SvgCore.getLogger().warning(
                    "[GROUPS] Password check unavailable for group id="
                            + group.getId()
                            + " (reflection failed)"
            );
            return Result.UNAVAILABLE;
        }
    }

    /**
     * Isolated adapter for known SVC internal password fields.
     * Never returns the password to callers outside this class for logging.
     */
    private static String readSvcPassword(Group group) throws Exception {
        Field groupField = group.getClass().getDeclaredField("group");
        groupField.setAccessible(true);
        Object groupObject = groupField.get(group);
        if (groupObject == null) {
            return null;
        }

        Field passwordField = groupObject.getClass().getDeclaredField("password");
        passwordField.setAccessible(true);
        Object value = passwordField.get(groupObject);
        return value instanceof String string ? string : null;
    }
}
