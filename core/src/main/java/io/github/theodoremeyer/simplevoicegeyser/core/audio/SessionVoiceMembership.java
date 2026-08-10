package io.github.theodoremeyer.simplevoicegeyser.core.audio;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable, thread-safe snapshot of authoritative voice-group membership for a session.
 * <p>
 * Fail-closed: transmit is allowed only when membership is confirmed and a group UUID is present.
 */
public final class SessionVoiceMembership {

    private final UUID playerUuid;
    private final long sessionGeneration;
    private final UUID groupUuid;
    private final long membershipRevision;
    private final boolean confirmed;

    /**
     * @param playerUuid player uuid
     * @param sessionGeneration authenticated session generation
     * @param groupUuid current group, or {@code null} when not in a group
     * @param membershipRevision directory/membership revision associated with this state
     * @param confirmed whether membership has been authoritatively reconciled
     */
    public SessionVoiceMembership(
            UUID playerUuid,
            long sessionGeneration,
            UUID groupUuid,
            long membershipRevision,
            boolean confirmed
    ) {
        this.playerUuid = Objects.requireNonNull(playerUuid, "playerUuid");
        this.sessionGeneration = sessionGeneration;
        this.groupUuid = groupUuid;
        this.membershipRevision = membershipRevision;
        this.confirmed = confirmed;
    }

    /**
     * Confirmed empty membership (player is not in a group).
     *
     * @param playerUuid player
     * @param sessionGeneration session generation
     * @param membershipRevision revision
     * @return membership
     */
    public static SessionVoiceMembership none(UUID playerUuid, long sessionGeneration, long membershipRevision) {
        return new SessionVoiceMembership(playerUuid, sessionGeneration, null, membershipRevision, true);
    }

    /**
     * Uncertain/stale membership — transmit remains blocked.
     *
     * @param playerUuid player
     * @param sessionGeneration session generation
     * @return membership
     */
    public static SessionVoiceMembership uncertain(UUID playerUuid, long sessionGeneration) {
        return new SessionVoiceMembership(playerUuid, sessionGeneration, null, 0L, false);
    }

    /**
     * Confirmed membership in a group.
     *
     * @param playerUuid player
     * @param sessionGeneration session generation
     * @param groupUuid group id
     * @param membershipRevision revision
     * @return membership
     */
    public static SessionVoiceMembership joined(
            UUID playerUuid,
            long sessionGeneration,
            UUID groupUuid,
            long membershipRevision
    ) {
        Objects.requireNonNull(groupUuid, "groupUuid");
        return new SessionVoiceMembership(playerUuid, sessionGeneration, groupUuid, membershipRevision, true);
    }

    /**
     * @return whether outbound voice may be encoded/sent
     */
    public boolean allowsTransmit() {
        return confirmed && groupUuid != null;
    }

    /**
     * @return player uuid
     */
    public UUID playerUuid() {
        return playerUuid;
    }

    /**
     * @return session generation
     */
    public long sessionGeneration() {
        return sessionGeneration;
    }

    /**
     * @return current group uuid, or null
     */
    public UUID groupUuid() {
        return groupUuid;
    }

    /**
     * @return membership revision
     */
    public long membershipRevision() {
        return membershipRevision;
    }

    /**
     * @return whether membership is confirmed
     */
    public boolean confirmed() {
        return confirmed;
    }

    /**
     * State identity used to deduplicate revision bumps / publications.
     *
     * @return identity string
     */
    public String stateIdentity() {
        return playerUuid
                + "|"
                + sessionGeneration
                + "|"
                + (groupUuid == null ? "none" : groupUuid)
                + "|"
                + membershipRevision
                + "|"
                + confirmed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SessionVoiceMembership that)) {
            return false;
        }
        return sessionGeneration == that.sessionGeneration
                && membershipRevision == that.membershipRevision
                && confirmed == that.confirmed
                && Objects.equals(playerUuid, that.playerUuid)
                && Objects.equals(groupUuid, that.groupUuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerUuid, sessionGeneration, groupUuid, membershipRevision, confirmed);
    }

    @Override
    public String toString() {
        return "SessionVoiceMembership{player=" + playerUuid
                + ", gen=" + sessionGeneration
                + ", group=" + (groupUuid == null ? "none" : groupUuid)
                + ", rev=" + membershipRevision
                + ", confirmed=" + confirmed
                + "}";
    }
}
