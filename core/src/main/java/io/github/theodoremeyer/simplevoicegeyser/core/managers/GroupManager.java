package io.github.theodoremeyer.simplevoicegeyser.core.managers;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.Group.Type;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgColor;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.audio.SessionVoiceMembership;
import io.github.theodoremeyer.simplevoicegeyser.core.managers.group.GroupInfo;
import io.github.theodoremeyer.simplevoicegeyser.core.managers.group.GroupMemberInfo;
import io.github.theodoremeyer.simplevoicegeyser.core.managers.group.GroupPasswordStore;
import io.github.theodoremeyer.simplevoicegeyser.core.managers.group.GroupPasswordVerifier;
import io.github.theodoremeyer.simplevoicegeyser.core.managers.group.GroupSnapshot;
import io.github.theodoremeyer.simplevoicegeyser.core.managers.group.GroupSyncService;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.SvgConnection;
import io.github.theodoremeyer.simplevoicegeyser.core.server.ratelimit.RateLimitService;
import io.github.theodoremeyer.simplevoicegeyser.core.svc.VoiceChatBridge;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * SVG Group System manager
 */
public final class GroupManager {

    private final VoiceChatBridge bridge;
    private final GroupPasswordStore passwordStore;
    private final GroupPasswordVerifier passwordVerifier;

    /** Groups keyed by UUID string. */
    private final Map<String, Group> groups = new ConcurrentHashMap<>();

    /** Membership: groupId → player UUIDs. */
    private final Map<UUID, Set<UUID>> membersByGroup = new ConcurrentHashMap<>();

    /** Groups created by each player (for per-player limits). */
    private final Map<UUID, Set<UUID>> createdByPlayer = new ConcurrentHashMap<>();

    /** Last create timestamp per player (cooldown). */
    private final Map<UUID, Long> lastCreateMillis = new ConcurrentHashMap<>();

    private final AtomicLong revision = new AtomicLong(0);

    /** Monotonic membership revision (join/leave / viewer membership changes). */
    private final AtomicLong membershipRevision = new AtomicLong(0);

    /** Fingerprint of last reconciled directory used to avoid double revision bumps. */
    private final AtomicReference<String> lastDirectoryFingerprint = new AtomicReference<>("");

    /** Last published membership identity per player (dedupe op + SVC event). */
    private final Map<UUID, String> lastMembershipIdentity = new ConcurrentHashMap<>();

    /**
     * Create a group manager to control groups with
     * @param api the bridge to SVG
     */
    public GroupManager(VoiceChatBridge api) {
        this.bridge = api;
        this.passwordStore = new GroupPasswordStore();
        this.passwordVerifier = new GroupPasswordVerifier(passwordStore);
    }

    /**
     * @return SVG-managed group password store
     */
    public GroupPasswordStore getPasswordStore() {
        return passwordStore;
    }

    /**
     * @return current directory revision
     */
    public long getRevision() {
        return revision.get();
    }

    /**
     * Bump revision and return the new value.
     * @return new revision
     */
    public long bumpRevision() {
        return revision.incrementAndGet();
    }

    /**
     * @return current membership revision
     */
    public long getMembershipRevision() {
        return membershipRevision.get();
    }

    /**
     * Bump membership revision and return the new value.
     * @return new membership revision
     */
    public long bumpMembershipRevision() {
        return membershipRevision.incrementAndGet();
    }

    /**
     * Create an SVC Group for SvgPlayer (command / form compatible).
     *
     * @param svgPlayer SvgPlayer creating the group
     * @param groupName name of the group being created
     * @param password password of the group; blank → passwordless
     * @param groupType Open, Normal, or Isolated
     * @param persistent Whether the group stays with no SvgPlayers or not
     * @param joinIfExists whether to join an already-created group with the same name
     * @return True/False
     */
    public boolean createGroup(
            SvgPlayer svgPlayer,
            String groupName,
            String password,
            Type groupType,
            boolean persistent,
            boolean joinIfExists
    ) {
        OpResult result = createGroupDetailed(
                svgPlayer,
                groupName,
                password,
                groupType,
                persistent,
                joinIfExists,
                false
        );
        return result.success();
    }

    /**
     * Create a group with web/config enforcement.
     *
     * @param svgPlayer creator
     * @param groupName name
     * @param password optional password
     * @param groupType type
     * @param persistent persistent flag
     * @param joinIfExists join existing by name
     * @param enforceWebLimits whether to apply web creation config limits
     * @return operation result
     */
    public OpResult createGroupDetailed(
            SvgPlayer svgPlayer,
            String groupName,
            String password,
            Type groupType,
            boolean persistent,
            boolean joinIfExists,
            boolean enforceWebLimits
    ) {
        if (svgPlayer == null) {
            return OpResult.fail("Invalid player.");
        }

        VoicechatServerApi api = getApi();
        if (api == null) {
            return OpResult.fail("Voice chat unavailable.");
        }

        if (!svgPlayer.hasPermission("svg.vc.group.create")) {
            svgPlayer.sendMessage(SvgCore.getPrefix() + SvgColor.RED
                    + "You do not have permission to create groups.");
            return OpResult.fail("Permission denied.");
        }

        if (groupName == null || groupName.isBlank()) {
            return OpResult.fail("Group name required.");
        }

        groupName = groupName.trim();
        int maxNameLength = SvgCore.getConfig().GROUPS_MAX_NAME_LENGTH.get();
        if (groupName.length() > maxNameLength) {
            return OpResult.fail("Group name too long.");
        }

        if (groupType == null) {
            groupType = Type.ISOLATED;
        }

        if (!isTypeAllowed(groupType)) {
            return OpResult.fail("Group type not allowed.");
        }

        if (!canCreate(svgPlayer, typeToString(groupType), persistent)) {
            svgPlayer.sendMessage(SvgCore.getPrefix() + SvgColor.RED
                    + "You don't have permission to create this group.");
            return OpResult.fail("Permission denied.");
        }

        if (enforceWebLimits && !Boolean.TRUE.equals(SvgCore.getConfig().GROUPS_ALLOW_WEB_CREATION.get())) {
            return OpResult.fail("Web group creation is disabled.");
        }

        Optional<Group> existingByName = findVisibleByName(groupName);
        if (existingByName.isPresent()) {
            if (joinIfExists) {
                OpResult join = joinGroup(svgPlayer, existingByName.get().getId(), password);
                return join;
            }
            svgPlayer.sendMessage(SvgCore.getPrefix() + SvgColor.DARK_RED
                    + "Group " + groupName + " already exists.");
            return OpResult.fail("Group already exists.");
        }

        OpResult limit = checkCreateLimits(svgPlayer, enforceWebLimits);
        if (!limit.success()) {
            return limit;
        }

        String effectivePassword = (password == null || password.isBlank()) ? null : password;

        Group.Builder builder = api.groupBuilder()
                .setName(groupName)
                .setType(groupType)
                .setPersistent(persistent);

        if (effectivePassword != null) {
            builder.setPassword(effectivePassword);
        }

        Group group = builder.build();
        if (group == null || group.getId() == null) {
            SvgCore.getLogger().info("group_create_build_failed nameLength=" + groupName.length());
            return OpResult.fail("Failed to create group.");
        }

        // Track ownership/password locally, but do not publish until creator is assigned
        // and SVC state is re-fetched (nonpersistent groups may be invisible until then).
        createdByPlayer
                .computeIfAbsent(svgPlayer.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet())
                .add(group.getId());
        lastCreateMillis.put(svgPlayer.getUniqueId(), System.currentTimeMillis());

        if (effectivePassword != null) {
            passwordStore.put(group.getId(), effectivePassword);
        }

        // Seed directory entry before join so joinGroup can resolve the group id.
        groups.put(group.getId().toString(), group);
        membersByGroup.computeIfAbsent(group.getId(), ignored -> ConcurrentHashMap.newKeySet());

        // Explicit create → join stages (do not infer membership from SVC events alone).
        OpResult join = joinGroup(svgPlayer, group.getId(), effectivePassword);
        if (!join.success() || !Boolean.TRUE.equals(join.joined())) {
            SvgCore.getLogger().info("group_create_assignment_failed groupId=" + group.getId()
                    + " joinSuccess=" + join.success());
            // Preserve seeded group for retry; do not wipe via empty live listing.
            preserveSeededGroup(group);
            reconcilePlayerState(svgPlayer.getUniqueId(), true);
            return OpResult.partialCreate(
                    group.getId(),
                    "Group created but join failed: " + (join.error() == null ? "unconfirmed membership" : join.error()),
                    getRevision()
            );
        }

        // Re-resolve and verify authoritative membership before reporting success.
        ReconcileResult reconciled = reconcilePlayerState(svgPlayer.getUniqueId(), true);
        preserveSeededGroup(group);
        if (reconciled.groupId() == null || !group.getId().equals(reconciled.groupId())) {
            SvgCore.getLogger().info("group_create_membership_unconfirmed groupId=" + group.getId()
                    + " authoritative=" + reconciled.groupId());
            return OpResult.partialCreate(
                    group.getId(),
                    "Group created but join failed: membership not confirmed",
                    reconciled.revision()
            );
        }

        SvgCore.getLogger().info("group_create_confirmed groupId=" + group.getId()
                + " revision=" + reconciled.revision());
        svgPlayer.sendMessage(SvgCore.getPrefix() + SvgColor.GREEN
                + "Joined Group: " + group.getName());
        return OpResult.okCreatedJoined(reconciled.revision(), group.getId());
    }

    /**
     * Keep a just-built group visible when live SVC directory listing lags behind.
     */
    private void preserveSeededGroup(Group group) {
        if (group == null || group.getId() == null) {
            return;
        }
        groups.put(group.getId().toString(), group);
        membersByGroup.computeIfAbsent(group.getId(), ignored -> ConcurrentHashMap.newKeySet());
    }

    /**
     * Remove a group that was built but never successfully joined by its creator.
     */
    private void cleanupUnassignedGroup(SvgPlayer svgPlayer, Group group) {
        if (group == null || group.getId() == null) {
            return;
        }
        UUID groupId = group.getId();
        passwordStore.remove(groupId);
        membersByGroup.remove(groupId);
        groups.remove(groupId.toString());
        Set<UUID> owned = createdByPlayer.get(svgPlayer.getUniqueId());
        if (owned != null) {
            owned.remove(groupId);
        }
        closeVoiceGate(svgPlayer.getUniqueId());
        applyVoiceMembership(svgPlayer.getUniqueId(), null);
        try {
            // Best-effort remove from SVC when the API supports it via empty membership.
            VoicechatServerApi api = getApi();
            if (api != null && !group.isPersistent()) {
                // Non-persistent groups without members are typically discarded by SVC.
                SvgCore.getLogger().info("[SVG] Cleaned up unassigned group " + groupId);
            }
        } catch (Exception ignored) {
            // Keep failure path deterministic even if SVC cleanup is unavailable.
        }
    }

    private OpResult checkCreateLimits(SvgPlayer svgPlayer, boolean enforceWebLimits) {
        int maxActive = SvgCore.getConfig().GROUPS_MAX_ACTIVE.get();
        if (maxActive > 0 && groups.size() >= maxActive) {
            return OpResult.fail("Maximum active groups reached.");
        }

        if (!enforceWebLimits) {
            return OpResult.ok(getRevision());
        }

        int maxPerPlayer = SvgCore.getConfig().GROUPS_MAX_CREATED_PER_PLAYER.get();
        Set<UUID> owned = createdByPlayer.getOrDefault(svgPlayer.getUniqueId(), Set.of());
        if (maxPerPlayer > 0 && owned.size() >= maxPerPlayer) {
            return OpResult.fail("You have created too many groups.");
        }

        int cooldownSeconds = SvgCore.getConfig().GROUPS_CREATION_COOLDOWN_SECONDS.get();
        if (cooldownSeconds > 0) {
            Long last = lastCreateMillis.get(svgPlayer.getUniqueId());
            if (last != null) {
                long elapsed = System.currentTimeMillis() - last;
                if (elapsed < cooldownSeconds * 1000L) {
                    return OpResult.fail("Please wait before creating another group.");
                }
            }
        }

        return OpResult.ok(getRevision());
    }

    private boolean isTypeAllowed(Type type) {
        List<String> allowed = SvgCore.getConfig().GROUPS_ALLOWED_TYPES.get();
        if (allowed == null || allowed.isEmpty()) {
            return true;
        }
        String name = typeToString(type);
        for (String entry : allowed) {
            if (entry != null && entry.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Translate a String to Group Type
     * NOTE: Returns A default group type: OPEN
     * @param string the translatable String
     * @return The group type
     */
    public Type stringToType(String string) {
        if (string == null) {
            return Type.OPEN;
        }
        if ("isolated".equalsIgnoreCase(string)) {
            return Type.ISOLATED;
        } else if ("normal".equalsIgnoreCase(string)) {
            return Type.NORMAL;
        } else if ("open".equalsIgnoreCase(string)) {
            return Type.OPEN;
        } else {
            return Type.OPEN;
        }
    }

    private static String typeToString(Type type) {
        if (type == null || type == Type.OPEN) {
            return "open";
        }
        if (type == Type.ISOLATED) {
            return "isolated";
        }
        if (type == Type.NORMAL) {
            return "normal";
        }
        return String.valueOf(type).toLowerCase(Locale.ROOT);
    }

    /**
     * Join by group name (commands / forms).
     *
     * @param svgPlayer player
     * @param groupName group name
     * @param password password
     * @return success
     */
    public boolean joinGroup(SvgPlayer svgPlayer, String groupName, String password) {
        if (groupName == null || groupName.isBlank()) {
            return false;
        }
        Optional<Group> group = findVisibleByName(groupName.trim());
        if (group.isEmpty()) {
            SvgCore.getLogger().warning("[SVG] Unknown group '" + groupName
                    + "' requested by " + (svgPlayer == null ? "?" : svgPlayer.getName()));
            return false;
        }
        return joinGroup(svgPlayer, group.get().getId(), password).success();
    }

    /**
     * Join by group UUID (web + internal).
     *
     * @param svgPlayer player
     * @param groupId group id
     * @param password password
     * @return operation result
     */
    public OpResult joinGroup(SvgPlayer svgPlayer, UUID groupId, String password) {
        if (svgPlayer == null || groupId == null) {
            return OpResult.fail("Invalid request.");
        }

        if (!svgPlayer.hasPermission("svg.vc.group.join")) {
            svgPlayer.sendMessage(SvgCore.getPrefix() + SvgColor.RED
                    + "You do not have permission to join groups.");
            return OpResult.fail("Permission denied.");
        }

        String rateKey = svgPlayer.getUniqueId().toString();
        RateLimitService limits = SvgCore.getRateLimitService();
        if (limits != null) {
            var passwordGate = limits.tryGroupPassword(rateKey);
            if (!passwordGate.allowed()) {
                return OpResult.fail("Too many failed join attempts. Try again later.");
            }
        }

        VoicechatServerApi api = getApi();
        if (api == null) {
            return OpResult.fail("Voice chat unavailable.");
        }

        // Re-fetch connection immediately before mutation (never reuse a stale snapshot).
        VoicechatConnection connection = api.getConnectionOf(svgPlayer.getUniqueId());
        if (connection == null) {
            SvgCore.getLogger().warning("[SVG] No voice connection found for svgPlayer "
                    + svgPlayer.getName());
            return OpResult.fail("Voice chat connection unavailable.");
        }

        Group group = groups.get(groupId.toString());
        if (group == null || group.isHidden()) {
            // Nonpersistent groups may only appear after assignment — try live API lookup.
            group = findLiveGroup(api, groupId);
            if (group == null || group.isHidden()) {
                return OpResult.fail("Unknown group.");
            }
            groups.put(groupId.toString(), group);
            membersByGroup.computeIfAbsent(groupId, ignored -> ConcurrentHashMap.newKeySet());
        }

        // Idempotent if already in that group.
        if (connection.isInGroup()
                && connection.getGroup() != null
                && groupId.equals(connection.getGroup().getId())) {
            ReconcileResult reconciled = reconcilePlayerState(svgPlayer.getUniqueId(), true);
            return OpResult.okJoined(reconciled.revision(), groupId);
        }

        GroupPasswordVerifier.Result verify = passwordVerifier.verify(group, password);
        if (verify == GroupPasswordVerifier.Result.INVALID
                || verify == GroupPasswordVerifier.Result.UNAVAILABLE) {
            if (limits != null) {
                limits.recordGroupPasswordFailure(rateKey);
            }
            return OpResult.fail(verify == GroupPasswordVerifier.Result.UNAVAILABLE
                    ? "Password check unavailable."
                    : "Invalid password.");
        }

        connection.setGroup(group);

        // Re-fetch after mutation — do not reuse the pre-setGroup connection object.
        ReconcileResult reconciled = reconcilePlayerState(svgPlayer.getUniqueId(), true);
        if (reconciled.groupId() == null || !groupId.equals(reconciled.groupId())) {
            // One fresh re-resolve: some SVC builds lag a tick before isInGroup().
            VoicechatConnection retry = api.getConnectionOf(svgPlayer.getUniqueId());
            if (retry != null) {
                retry.setGroup(group);
            }
            reconciled = reconcilePlayerState(svgPlayer.getUniqueId(), true);
        }
        if (reconciled.groupId() == null || !groupId.equals(reconciled.groupId())) {
            SvgCore.getLogger().info("[SVG] join_unconfirmed player=" + svgPlayer.getName()
                    + " expected=" + groupId + " authoritative=" + reconciled.groupId());
            return OpResult.fail("Join failed: membership not confirmed.");
        }
        if (limits != null) {
            limits.resetGroupPassword(rateKey);
        }

        SvgCore.getLogger().debug("[SVG] " + svgPlayer.getName()
                + " successfully joined group '" + group.getName() + "'");
        return OpResult.okJoined(reconciled.revision(), groupId);
    }

    /**
     * Get a list of all known visible group names
     * @return the list of group names
     */
    public List<String> getGroupNames() {
        List<String> names = new ArrayList<>();
        for (Group group : groups.values()) {
            if (group != null && !group.isHidden()) {
                names.add(group.getName());
            }
        }
        return names;
    }

    /**
     * Returns the name of the current group the SvgPlayer is in
     * @param svgPlayer the SvgPlayer to get for
     * @return the group's name
     */
    public Optional<String> getJoinedGroupName(SvgPlayer svgPlayer) {
        VoicechatServerApi api = getApi();
        if (api == null) {
            return Optional.empty();
        }

        VoicechatConnection connection = api.getConnectionOf(svgPlayer.getUniqueId());
        if (connection == null || !connection.isInGroup()) {
            return Optional.empty();
        }

        Group group = connection.getGroup();
        if (group == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(group.getName());
    }

    /**
     * Removes SvgPlayer from any group (idempotent).
     * @param svgPlayer SvgPlayer to leave a group
     */
    public void leaveGroup(SvgPlayer svgPlayer) {
        leaveGroupDetailed(svgPlayer);
    }

    /**
     * Leave current group with a structured result.
     *
     * @param svgPlayer player
     * @return operation result
     */
    /**
     * Leave the current group with optional expected-group correlation.
     *
     * @param svgPlayer player
     * @return operation result
     */
    public OpResult leaveGroupDetailed(SvgPlayer svgPlayer) {
        return leaveGroupDetailed(svgPlayer, null);
    }

    /**
     * Leave the current group.
     *
     * @param svgPlayer player
     * @param expectedGroupId optional group the client believes it is leaving
     * @return operation result with left=true when membership is cleared
     */
    public OpResult leaveGroupDetailed(SvgPlayer svgPlayer, UUID expectedGroupId) {
        if (svgPlayer == null) {
            return OpResult.fail("Invalid player.");
        }

        VoicechatServerApi api = getApi();
        if (api == null) {
            return OpResult.fail("Voice chat unavailable.");
        }

        // Close the audio gate before mutating SVC / draining queues.
        closeVoiceGate(svgPlayer.getUniqueId());

        VoicechatConnection connection = api.getConnectionOf(svgPlayer.getUniqueId());
        if (connection == null) {
            reconcilePlayerState(svgPlayer.getUniqueId(), true);
            return OpResult.fail("Voice chat connection unavailable.");
        }

        if (!connection.isInGroup() || connection.getGroup() == null) {
            ReconcileResult reconciled = reconcilePlayerState(svgPlayer.getUniqueId(), true);
            // Already left — idempotent success.
            return OpResult.okLeft(reconciled.revision(), expectedGroupId);
        }

        UUID previousGroupId = connection.getGroup().getId();
        if (expectedGroupId != null && !expectedGroupId.equals(previousGroupId)) {
            ReconcileResult reconciled = reconcilePlayerState(svgPlayer.getUniqueId(), true);
            return OpResult.fail("Group membership changed.");
        }

        connection.setGroup(null);

        ReconcileResult reconciled = reconcilePlayerState(svgPlayer.getUniqueId(), true);
        if (reconciled.groupId() != null) {
            return OpResult.fail("Leave did not clear group membership.");
        }
        svgPlayer.sendMessage("[SVG] You left your group.");
        return OpResult.okLeft(reconciled.revision(), previousGroupId);
    }

    /**
     * Simply return whether the SvgPlayer is in a group
     * @param svgPlayer SvgPlayer is/isn't in group
     * @return whether the player is in a group
     */
    public boolean isInGroup(SvgPlayer svgPlayer) {
        VoicechatServerApi api = getApi();
        if (api == null) {
            return false;
        }

        VoicechatConnection connection = api.getConnectionOf(svgPlayer.getUniqueId());
        if (connection == null) {
            return false;
        }

        return connection.isInGroup();
    }

    /**
     * Easy way to get the SVC Api.
     * @return VoicechatServerApi the SimpleVoiceChat api
     */
    private VoicechatServerApi getApi() {
        return bridge.getVcServerApi();
    }

    /**
     * Easy way to see if a SvgPlayer can create the group type
     * @param svgPlayer the player to check
     * @param type the type of group to check
     * @param persistent whether the group is persistent
     * @return whether they can create it or not
     */
    public boolean canCreate(SvgPlayer svgPlayer, String type, boolean persistent) {
        if (type != null
                && type.equalsIgnoreCase("isolated")
                && !svgPlayer.hasPermission("svg.vc.group.type.isolated")) {
            return false;
        }
        return !persistent || svgPlayer.hasPermission("svg.vc.group.setpersistent");
    }

    /**
     * Add / refresh a known Group (keyed by UUID).
     * @param group the group to add
     */
    public void addGroup(Group group) {
        if (group == null || group.getId() == null) {
            return;
        }
        groups.put(group.getId().toString(), group);
        membersByGroup.computeIfAbsent(group.getId(), ignored -> ConcurrentHashMap.newKeySet());
        bumpRevisionIfDirectoryChanged();
    }

    /**
     * Reconcile directory against live SVC groups.
     *
     * @param liveGroups groups from api.getGroups()
     */
    public void reconcileFromApi(Collection<Group> liveGroups) {
        applyDirectoryFromLive(liveGroups);
        bumpRevisionIfDirectoryChanged();
    }

    /**
     * Authoritative reconcile after a create/join/leave mutation or SVC event.
     * Re-fetches SVC connection + groups, updates directory once, syncs the voice gate,
     * and publishes to browsers only when state actually changed.
     *
     * @param playerUuid player whose membership changed (may be null for directory-only)
     * @param publish whether to publish snapshots / membership events
     * @return reconcile result
     */
    public ReconcileResult reconcilePlayerState(UUID playerUuid, boolean publish) {
        VoicechatServerApi api = getApi();
        if (api == null) {
            if (playerUuid != null) {
                closeVoiceGate(playerUuid);
            }
            return new ReconcileResult(getRevision(), false, null);
        }

        applyDirectoryFromLive(api.getGroups());

        UUID authoritativeGroupId = null;
        if (playerUuid != null) {
            // Always re-fetch connection — never reuse a pre-mutation snapshot.
            VoicechatConnection fresh = api.getConnectionOf(playerUuid);
            if (fresh != null && fresh.isInGroup() && fresh.getGroup() != null) {
                authoritativeGroupId = fresh.getGroup().getId();
                trackMembership(authoritativeGroupId, playerUuid, true);
            } else {
                clearPlayerMembership(playerUuid);
            }
        }

        boolean revisionBumped = bumpRevisionIfDirectoryChanged();

        String membershipIdentity = playerUuid == null
                ? null
                : playerUuid + "|" + (authoritativeGroupId == null ? "none" : authoritativeGroupId);
        boolean membershipChanged = false;
        if (playerUuid != null && membershipIdentity != null) {
            String previous = lastMembershipIdentity.put(playerUuid, membershipIdentity);
            membershipChanged = previous == null || !previous.equals(membershipIdentity);
        }

        // Membership changes must always advance the authoritative revision even when
        // directory fingerprint (ids + member counts) looks unchanged.
        if (membershipChanged) {
            bumpMembershipRevision();
            if (!revisionBumped) {
                bumpRevision();
                revisionBumped = true;
                lastDirectoryFingerprint.set(directoryFingerprint());
            }
        }

        if (playerUuid != null) {
            applyVoiceMembership(playerUuid, authoritativeGroupId);
        }

        if (publish && (revisionBumped || membershipChanged)) {
            publishAfterReconcile(playerUuid, authoritativeGroupId, membershipChanged);
        }

        return new ReconcileResult(getRevision(), revisionBumped || membershipChanged, authoritativeGroupId);
    }

    /**
     * Handle an external SVC membership event with revision deduplication.
     *
     * @param playerUuid player
     * @param groupId group id (nullable on leave)
     * @param joined whether joined
     */
    public void onExternalMembershipEvent(UUID playerUuid, UUID groupId, boolean joined) {
        if (playerUuid == null) {
            return;
        }
        if (!joined) {
            // Close gate before async cleanup / reconcile.
            closeVoiceGate(playerUuid);
        }
        reconcilePlayerState(playerUuid, true);
    }

    /**
     * Handle external create/remove group events.
     *
     * @param publishCreatedGroupId optional created group id for incremental event
     * @param publishCreatedName optional created group name
     * @param removedGroupId optional removed group id
     */
    public void onExternalDirectoryEvent(
            String publishCreatedGroupId,
            String publishCreatedName,
            String removedGroupId,
            Group seedGroup
    ) {
        VoicechatServerApi api = getApi();
        if (api != null) {
            applyDirectoryFromLive(api.getGroups());
        }

        if (seedGroup != null && seedGroup.getId() != null) {
            String seedId = seedGroup.getId().toString();
            if (!groups.containsKey(seedId)) {
                groups.put(seedId, seedGroup);
                membersByGroup.computeIfAbsent(seedGroup.getId(), ignored -> ConcurrentHashMap.newKeySet());
            }
        }

        boolean bumped = bumpRevisionIfDirectoryChanged();
        if (publishCreatedGroupId != null && !bumped) {
            bumpRevision();
            bumped = true;
            lastDirectoryFingerprint.set(directoryFingerprint());
        }

        // Group removal must close transmit for any browser session still pointing at it.
        if (removedGroupId != null) {
            try {
                UUID removed = UUID.fromString(removedGroupId);
                Set<UUID> members = membersByGroup.getOrDefault(removed, Set.of());
                for (UUID member : Set.copyOf(members)) {
                    closeVoiceGate(member);
                    clearPlayerMembership(member);
                    lastMembershipIdentity.put(member, member + "|none");
                    applyVoiceMembership(member, null);
                }
                membersByGroup.remove(removed);
            } catch (IllegalArgumentException ignored) {
            }
        }

        GroupSyncService sync = SvgCore.getGroupSyncService();
        if (sync == null) {
            return;
        }
        if (publishCreatedGroupId != null) {
            sync.publishCreated(publishCreatedGroupId, publishCreatedName);
        }
        if (removedGroupId != null) {
            sync.publishRemoved(removedGroupId);
        }
        if (bumped || publishCreatedGroupId != null || removedGroupId != null) {
            sync.broadcastSnapshots();
        }
    }

    private void applyDirectoryFromLive(Collection<Group> liveGroups) {
        // null means the API could not provide a directory listing — do not wipe local state.
        if (liveGroups == null) {
            return;
        }

        Map<String, Group> next = new ConcurrentHashMap<>();
        for (Group group : liveGroups) {
            if (group == null || group.getId() == null) {
                continue;
            }
            next.put(group.getId().toString(), group);
            membersByGroup.computeIfAbsent(group.getId(), ignored -> ConcurrentHashMap.newKeySet());
        }

        // Preserve locally seeded groups that SVC has not listed yet (common for
        // non-persistent groups between build() and confirmed membership).
        for (Map.Entry<String, Group> entry : groups.entrySet()) {
            if (next.containsKey(entry.getKey())) {
                continue;
            }
            Group seeded = entry.getValue();
            if (seeded == null || seeded.getId() == null) {
                continue;
            }
            boolean owned = false;
            for (Set<UUID> ownedIds : createdByPlayer.values()) {
                if (ownedIds.contains(seeded.getId())) {
                    owned = true;
                    break;
                }
            }
            if (owned || membersByGroup.getOrDefault(seeded.getId(), Set.of()).size() > 0) {
                next.put(entry.getKey(), seeded);
                membersByGroup.computeIfAbsent(seeded.getId(), ignored -> ConcurrentHashMap.newKeySet());
            }
        }

        for (String id : groups.keySet()) {
            if (!next.containsKey(id)) {
                try {
                    UUID uuid = UUID.fromString(id);
                    passwordStore.remove(uuid);
                    membersByGroup.remove(uuid);
                    for (Set<UUID> owned : createdByPlayer.values()) {
                        owned.remove(uuid);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        groups.clear();
        groups.putAll(next);
    }

    private boolean bumpRevisionIfDirectoryChanged() {
        String fingerprint = directoryFingerprint();
        while (true) {
            String previous = lastDirectoryFingerprint.get();
            if (fingerprint.equals(previous)) {
                return false;
            }
            if (lastDirectoryFingerprint.compareAndSet(previous, fingerprint)) {
                bumpRevision();
                return true;
            }
        }
    }

    private String directoryFingerprint() {
        List<String> ids = new ArrayList<>(groups.keySet());
        Collections.sort(ids);
        StringJoiner joiner = new StringJoiner(",");
        for (String id : ids) {
            Group group = groups.get(id);
            if (group == null) {
                continue;
            }
            Set<UUID> members = membersByGroup.getOrDefault(group.getId(), Set.of());
            joiner.add(id + ":" + members.size() + ":" + (group.isHidden() ? "h" : "v"));
        }
        return joiner.toString();
    }

    private void clearPlayerMembership(UUID playerId) {
        for (Set<UUID> members : membersByGroup.values()) {
            members.remove(playerId);
        }
    }

    private void closeVoiceGate(UUID playerUuid) {
        SvgConnection connection = SvgCore.getConnectionManager() == null
                ? null
                : SvgCore.getConnectionManager().get(playerUuid);
        if (connection != null) {
            connection.closeVoiceTransmit(getRevision());
        }
    }

    private void applyVoiceMembership(UUID playerUuid, UUID groupId) {
        SvgConnection connection = SvgCore.getConnectionManager() == null
                ? null
                : SvgCore.getConnectionManager().get(playerUuid);
        if (connection == null) {
            return;
        }
        long rev = getRevision();
        SessionVoiceMembership membership = groupId == null
                ? SessionVoiceMembership.none(playerUuid, connection.getSessionGeneration(), rev)
                : SessionVoiceMembership.joined(playerUuid, connection.getSessionGeneration(), groupId, rev);
        connection.applyMembership(membership);
    }

    private void publishAfterReconcile(UUID playerUuid, UUID groupId, boolean membershipChanged) {
        GroupSyncService sync = SvgCore.getGroupSyncService();
        if (sync == null) {
            return;
        }
        if (membershipChanged && playerUuid != null) {
            sync.publishMembershipChanged(
                    groupId == null ? null : groupId.toString(),
                    playerUuid.toString(),
                    groupId != null
            );
        }
        sync.broadcastSnapshots();
    }

    private static Group findLiveGroup(VoicechatServerApi api, UUID groupId) {
        if (api == null || groupId == null) {
            return null;
        }
        Collection<Group> live = api.getGroups();
        if (live == null) {
            return null;
        }
        for (Group group : live) {
            if (group != null && groupId.equals(group.getId())) {
                return group;
            }
        }
        return null;
    }

    /**
     * Remove a group from the manager
     * @param group the group to remove
     */
    public void removeGroup(Group group) {
        if (group == null || group.getId() == null) {
            return;
        }
        Set<UUID> members = membersByGroup.getOrDefault(group.getId(), Set.of());
        for (UUID member : Set.copyOf(members)) {
            closeVoiceGate(member);
            lastMembershipIdentity.put(member, member + "|none");
            applyVoiceMembership(member, null);
        }
        groups.remove(group.getId().toString());
        passwordStore.remove(group.getId());
        membersByGroup.remove(group.getId());
        for (Set<UUID> owned : createdByPlayer.values()) {
            owned.remove(group.getId());
        }
        bumpRevisionIfDirectoryChanged();
    }

    /**
     * Track membership from SVC join/leave events.
     *
     * @param groupId group id
     * @param playerId player id
     * @param joined true on join, false on leave
     */
    public void trackMembership(UUID groupId, UUID playerId, boolean joined) {
        if (groupId == null || playerId == null) {
            return;
        }
        Set<UUID> members = membersByGroup.computeIfAbsent(groupId, ignored -> ConcurrentHashMap.newKeySet());
        if (joined) {
            // A player can only be in one group at a time.
            for (Map.Entry<UUID, Set<UUID>> entry : membersByGroup.entrySet()) {
                if (!entry.getKey().equals(groupId)) {
                    entry.getValue().remove(playerId);
                }
            }
            members.add(playerId);
        } else {
            members.remove(playerId);
        }
    }

    /**
     * Result of an authoritative reconcile.
     *
     * @param revision current revision
     * @param changed whether directory or membership changed
     * @param groupId authoritative group id (nullable)
     */
    public record ReconcileResult(long revision, boolean changed, UUID groupId) {
    }

    /**
     * Snapshot visible (non-hidden) groups for a viewer.
     *
     * @param viewerUuid viewer uuid (may be null)
     * @return snapshot
     */
    public GroupSnapshot snapshotVisible(UUID viewerUuid) {
        UUID joinedGroupId = null;
        if (viewerUuid != null) {
            VoicechatServerApi api = getApi();
            if (api != null) {
                VoicechatConnection connection = api.getConnectionOf(viewerUuid);
                if (connection != null && connection.isInGroup() && connection.getGroup() != null) {
                    joinedGroupId = connection.getGroup().getId();
                }
            }
        }

        List<GroupInfo> infos = new ArrayList<>();
        for (Group group : groups.values()) {
            if (group == null || group.isHidden()) {
                continue;
            }
            Set<UUID> members = membersByGroup.getOrDefault(group.getId(), Set.of());
            List<GroupMemberInfo> memberInfos = buildMemberInfos(members, viewerUuid);
            boolean protectedGroup = group.hasPassword();
            infos.add(new GroupInfo(
                    group.getId().toString(),
                    group.getName(),
                    group.getType() == null ? "normal" : typeToString(group.getType()),
                    protectedGroup,
                    protectedGroup,
                    group.isPersistent(),
                    members.size(),
                    joinedGroupId != null && joinedGroupId.equals(group.getId()),
                    memberInfos
            ));
        }

        infos.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        String currentGroupIdStr = joinedGroupId == null ? null : joinedGroupId.toString();
        return new GroupSnapshot(
                getRevision(),
                getMembershipRevision(),
                currentGroupIdStr,
                Collections.unmodifiableList(infos)
        );
    }

    private List<GroupMemberInfo> buildMemberInfos(Set<UUID> memberIds, UUID viewerUuid) {
        if (memberIds == null || memberIds.isEmpty()) {
            return List.of();
        }

        List<GroupMemberInfo> members = new ArrayList<>(memberIds.size());
        for (UUID memberId : memberIds) {
            if (memberId == null) {
                continue;
            }
            String name = resolveMemberName(memberId);
            if (name == null || name.isBlank()) {
                continue;
            }
            members.add(new GroupMemberInfo(name, viewerUuid != null && viewerUuid.equals(memberId)));
        }

        members.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return Collections.unmodifiableList(members);
    }

    private String resolveMemberName(UUID memberId) {
        SvgPlayer platformPlayer = SvgCore.getPlayerManager().getPlayer(memberId);
        if (platformPlayer != null && platformPlayer.getName() != null && !platformPlayer.getName().isBlank()) {
            return platformPlayer.getName();
        }

        VoicechatServerApi api = getApi();
        if (api != null) {
            VoicechatConnection connection = api.getConnectionOf(memberId);
            if (connection != null && connection.getPlayer() != null) {
                SvgPlayer resolved = SvgCore.getPlayerManager().getPlayer(connection.getPlayer().getUuid());
                if (resolved != null && resolved.getName() != null && !resolved.getName().isBlank()) {
                    return resolved.getName();
                }
            }
        }
        return null;
    }

    /**
     * Find a visible group by name (case-insensitive). Prefer exact, then unique ignore-case.
     *
     * @param name group name
     * @return group if uniquely resolved
     */
    public Optional<Group> findVisibleByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }

        Group exact = null;
        List<Group> ignoreCase = new ArrayList<>();
        for (Group group : groups.values()) {
            if (group == null || group.isHidden() || group.getName() == null) {
                continue;
            }
            if (group.getName().equals(name)) {
                exact = group;
            }
            if (group.getName().equalsIgnoreCase(name)) {
                ignoreCase.add(group);
            }
        }

        if (exact != null) {
            return Optional.of(exact);
        }
        if (ignoreCase.size() == 1) {
            return Optional.of(ignoreCase.get(0));
        }
        return Optional.empty();
    }

    /**
     * @param groupId group id
     * @return group if known
     */
    public Optional<Group> getGroup(UUID groupId) {
        if (groupId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(groups.get(groupId.toString()));
    }

    /**
     * Structured group operation result.
     *
     * @param success whether succeeded
     * @param error error message when failed
     * @param revision directory revision
     */
    public record OpResult(
            boolean success,
            String error,
            long revision,
            UUID groupId,
            Boolean joined,
            Boolean left,
            UUID previousGroupId,
            UUID currentGroupId,
            boolean partial,
            Boolean created,
            String errorCode
    ) {
        /**
         * @param revision revision
         * @return success
         */
        public static OpResult ok(long revision) {
            return new OpResult(true, null, revision, null, null, null, null, null, false, null, null);
        }

        /**
         * @param revision revision
         * @param groupId created/joined group
         * @return success with joined=true
         */
        public static OpResult okJoined(long revision, UUID groupId) {
            return new OpResult(true, null, revision, groupId, true, null, null, groupId, false, null, null);
        }

        /**
         * Create + verified join success.
         *
         * @param revision revision
         * @param groupId created group
         * @return success with created=true and joined=true
         */
        public static OpResult okCreatedJoined(long revision, UUID groupId) {
            return new OpResult(true, null, revision, groupId, true, null, null, groupId, false, true, null);
        }

        /**
         * @param revision revision
         * @param previousGroupId group left
         * @return success with left=true
         */
        public static OpResult okLeft(long revision, UUID previousGroupId) {
            return new OpResult(true, null, revision, null, false, true, previousGroupId, null, false, null, null);
        }

        /**
         * Creation succeeded but creator assignment failed — group remains joinable.
         *
         * @param groupId created group
         * @param error message
         * @param revision revision
         * @return partial failure
         */
        public static OpResult partialCreate(UUID groupId, String error, long revision) {
            return new OpResult(
                    false,
                    error,
                    revision,
                    groupId,
                    false,
                    null,
                    null,
                    null,
                    true,
                    true,
                    "GROUP_CREATED_JOIN_FAILED"
            );
        }

        /**
         * @param error error
         * @return failure
         */
        public static OpResult fail(String error) {
            return new OpResult(false, error, 0L, null, null, null, null, null, false, null, null);
        }
    }
}
