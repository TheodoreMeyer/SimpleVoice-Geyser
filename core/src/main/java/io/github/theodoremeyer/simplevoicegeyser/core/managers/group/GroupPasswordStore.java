package io.github.theodoremeyer.simplevoicegeyser.core.managers.group;

import org.mindrot.jbcrypt.BCrypt;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory BCrypt hashes for groups SVG created with a password.
 */
public final class GroupPasswordStore {

    private final Map<UUID, String> hashes = new ConcurrentHashMap<>();

    /**
     * Store a bcrypt hash for a group id.
     *
     * @param groupId group uuid
     * @param plaintextPassword plaintext password (hashed immediately; not retained)
     */
    public void put(UUID groupId, String plaintextPassword) {
        if (groupId == null || plaintextPassword == null || plaintextPassword.isBlank()) {
            return;
        }
        hashes.put(groupId, BCrypt.hashpw(plaintextPassword, BCrypt.gensalt(12)));
    }

    /**
     * @param groupId group uuid
     * @return whether SVG manages a hash for this group
     */
    public boolean has(UUID groupId) {
        return groupId != null && hashes.containsKey(groupId);
    }

    /**
     * Verify a provided password against the stored hash.
     *
     * @param groupId group uuid
     * @param providedPassword candidate password
     * @return true if matches
     */
    public boolean verify(UUID groupId, String providedPassword) {
        if (groupId == null || providedPassword == null) {
            return false;
        }
        String hash = hashes.get(groupId);
        if (hash == null) {
            return false;
        }
        try {
            return BCrypt.checkpw(providedPassword, hash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Clear the stored hash when a group is removed.
     *
     * @param groupId group uuid
     */
    public void remove(UUID groupId) {
        if (groupId != null) {
            hashes.remove(groupId);
        }
    }

    /**
     * Clear all hashes.
     */
    public void clear() {
        hashes.clear();
    }
}
