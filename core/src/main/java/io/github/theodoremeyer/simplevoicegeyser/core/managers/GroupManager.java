package io.github.theodoremeyer.simplevoicegeyser.core.managers;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.Group.Type;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgColor;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.svc.VoiceChatBridge;

import io.github.theodoremeyer.simplevoicegeyser.core.server.packets.GroupsBroadcaster;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SVG Group System manager
 */
public final class GroupManager {

    /**
     * Interface to group system
     */
    private final VoiceChatBridge bridge;

    /**
     * Groups that are listed/Available
     */
    private final Map<String, Group> groups = new ConcurrentHashMap<>(); //list of active groups

    /**
     * When &gt; 0, {@link #addGroup(Group, boolean)} skips broadcasting so SVG
     * {@link #createGroup} can {@code setGroup} before the first {@code groups} push.
     */
    private final AtomicInteger suppressBroadcastDepth = new AtomicInteger();

    /**
     * Create a group manager to control groups with
     * @param api the bridge to SVG
     */
    public GroupManager(VoiceChatBridge api) {
        this.bridge = api;
    }

    /**
     * Create an SVC Group for SvgPlayer
     * @param SvgPlayer SvgPlayer creating the group
     * @param groupName name of the group being created
     * @param password password of the group, default is 1a2b
     * @param groupType Open, Normal, or Isolated. Default is Normal.
     * @param persistent Whether the group stays with no SvgPlayers or not
     * @param created whether to allow join already created groups
     * @return True/False
     */
    public boolean createGroup(SvgPlayer SvgPlayer, String groupName, String password, Type groupType, boolean persistent, boolean created) {
        VoicechatServerApi api = getApi();
        if (api == null) return false;

        VoicechatConnection connection = api.getConnectionOf(SvgPlayer.getUniqueId());

        if (groups.containsKey(groupName)) { //whether the group already exists
            if (!created) { //if we can add the SvgPlayer if the group is already created
                SvgPlayer.sendMessage(SvgCore.getPrefix() + SvgColor.DARK_RED + "Group " + groupName + "already exists.");
                return false;
            } else {
                if (connection != null) {
                    connection.setGroup(groups.get(groupName));
                    SvgPlayer.sendMessage(SvgCore.getPrefix() + SvgColor.DARK_BLUE + "joined Group " + groupName);
                    GroupsBroadcaster.broadcast();
                    return true;
                } else {
                    SvgPlayer.sendMessage(SvgCore.getPrefix() + SvgColor.DARK_RED + "Vc connection is null");
                    return false;
                }
            }
        }

        if (groupType == null) groupType = Type.NORMAL;

        // Suppress CreateGroupEvent → addGroup broadcast until setGroup so web
        // clients do not clear pending-open on a premature current=null push.
        suppressBroadcastDepth.incrementAndGet();
        Group group;
        try {
            if (password == null || password.isEmpty()) {
                group = api.groupBuilder()
                        .setName(groupName)
                        .setType(groupType)
                        .setPersistent(persistent)
                        .build();
            } else {
                group = api.groupBuilder()
                        .setName(groupName)
                        .setPassword(password)
                        .setType(groupType)
                        .setPersistent(persistent)
                        .build();
            }
        } finally {
            suppressBroadcastDepth.decrementAndGet();
        }

        if (connection != null) {
            if (connection.getGroup() !=null) {
                SvgPlayer.sendMessage(SvgCore.getPrefix() + SvgColor.RED + "Leaving Group: " + connection.getGroup().getName());
            }
            connection.setGroup(group);
            SvgPlayer.sendMessage(SvgCore.getPrefix() + SvgColor.BLUE + "Joined Group: " + group.getName());
            GroupsBroadcaster.broadcast();
            return true;
        } else {
            SvgPlayer.sendMessage("Connection is null!");
        }

        SvgPlayer.sendMessage("Args not used correctly");
        return false; // The Group could not be set.
    }

    /**
     * Translate a String to Group Type
     * NOTE: Returns A default group type: OPEN
     * @param string the translatable String
     * @return The group type
     */
    public Type stringToType(String string) {
        if ("isolated".equalsIgnoreCase(string)) {
            return Type.ISOLATED;
        } else if ("normal".equalsIgnoreCase(string)) {
            return Type.NORMAL;
        } else {
            return Type.OPEN; // Default to OPEN if no type specified.
        }
    }

    /**
     * Set svgPlayer Group
     * @param svgPlayer svgPlayer affected
     * @param groupName uuid of the group, fetched by group.getName or by groups
     * @param password password of the group
     * @return True/False
     */
    public boolean joinGroup(SvgPlayer svgPlayer, String groupName, String password) {

        if (!svgPlayer.hasPermission("svg.vc.group.join")) {
            svgPlayer.sendMessage(SvgCore.getPrefix() + SvgColor.RED +
                    "You do not have permission to join groups.");
            return false;
        }

        VoicechatServerApi api = getApi();
        VoicechatConnection connection = api.getConnectionOf(svgPlayer.getUniqueId());

        if (connection == null) {
            SvgCore.getLogger().warning("[SVG] No voice connection found for svgPlayer "
                    + svgPlayer.getName());
            return false;
        }

        if (groupName == null) {
            SvgCore.getLogger().warning("[SVG] svgPlayer " + svgPlayer.getName()
                    + " attempted to join group with null name.");
            return false;
        }

        Group group = groups.get(groupName);

        if (group == null) {
            SvgCore.getLogger().warning("[SVG] Unknown group '" + groupName
                    + "' requested by " + svgPlayer.getName());
            return false;
        }

        String groupPassword = readGroupPassword(group);

        // Debug getLogger password state
        SvgCore.getLogger().debug("[GROUPS]: svgPlayer: " + svgPlayer.getName()
                + " | Group: " + groupName
                + " | Provided Password: " + password
                + " | Actual Password: " + groupPassword);

        // Handle password check safely
        if (groupPassword != null && !groupPassword.isEmpty()) {

            if (password == null) {
                return false;
            }

            if (!groupPassword.equals(password)) {
                return false;
            }
        }

        // Leave previous group
        if (connection.isInGroup()) {
            SvgCore.getLogger().debug("[SVG] " + svgPlayer.getName()
                    + " left group " + connection.getGroup().getName());
            svgPlayer.sendMessage("You left group: "
                    + connection.getGroup().getName());
        }

        connection.setGroup(group);

        SvgCore.getLogger().debug("[SVG]" + svgPlayer.getName()
                + " successfully joined group '" + groupName + "'");

        GroupsBroadcaster.broadcast();
        return true;
    }

    /**
     * Whether a known group requires a password (never exposes the secret).
     */
    public boolean hasPassword(String groupName) {
        Group group = groups.get(groupName);
        if (group == null) {
            return false;
        }
        return hasPassword(group);
    }

    /**
     * Whether a group requires a password (never exposes the secret).
     */
    public boolean hasPassword(Group group) {
        String password = readGroupPassword(group);
        return password != null && !password.isEmpty();
    }

    /**
     * Snapshot of all tracked groups.
     */
    public Collection<Group> getGroups() {
        return Collections.unmodifiableCollection(groups.values());
    }

    private String readGroupPassword(Group group) {
        if (group == null) {
            return null;
        }
        try {
            Field groupField = group.getClass().getDeclaredField("group");
            groupField.setAccessible(true);
            Object groupObject = groupField.get(group);

            Field passwordField = groupObject.getClass()
                    .getDeclaredField("password");
            passwordField.setAccessible(true);

            return (String) passwordField.get(groupObject);
        } catch (Throwable e) {
            SvgCore.getLogger().warning("[SVG] Failed to reflect password of group '"
                    + group.getName() + "' (" + group.getId() + "): " + e.getMessage());
            return null;
        }
    }

    /**
     * Get a list of all known group names
     * @return the list of group names
     */
    public List<String> getGroupNames() {
        List<String> names = new ArrayList<>();

        for (Group group : groups.values()) {
            names.add(group.getName());
        }
        return names;
    }

    /**
     * Online players currently in the named SVC group.
     * @return list of {uuid, name} maps as SvgPlayer snapshots
     */
    public List<SvgPlayer> getMembers(String groupName) {
        List<SvgPlayer> members = new ArrayList<>();
        if (groupName == null || groupName.isEmpty()) {
            return members;
        }
        VoicechatServerApi api = getApi();
        if (api == null) {
            return members;
        }
        for (SvgPlayer player : SvgCore.getPlayerManager().getAllPlayers()) {
            VoicechatConnection connection = api.getConnectionOf(player.getUniqueId());
            if (connection == null || !connection.isInGroup() || connection.getGroup() == null) {
                continue;
            }
            if (groupName.equals(connection.getGroup().getName())) {
                members.add(player);
            }
        }
        members.sort(Comparator.comparing(SvgPlayer::getName, String.CASE_INSENSITIVE_ORDER));
        return members;
    }

    /**
     * Look up a tracked group by name.
     */
    public Group getGroup(String groupName) {
        if (groupName == null) {
            return null;
        }
        return groups.get(groupName);
    }
    /**
     * Returns the name of the current group the SvgPlayer is in
     * @param SvgPlayer the SvgPlayer to get for
     * @return the group's name
     */
    public Optional<String> getJoinedGroupName(SvgPlayer SvgPlayer) {
        VoicechatServerApi api = getApi();
        if (api == null) return Optional.empty();

        VoicechatConnection connection = api.getConnectionOf(SvgPlayer.getUniqueId());
        if (connection == null || !connection.isInGroup()) return Optional.empty();

        Group group = connection.getGroup();
        if (group == null) return Optional.empty();

        return Optional.ofNullable(group.getName());
    }
    /**
     * Removes SvgPlayer from any group.
     * @return true if the player had a voice connection and left (or was already out)
     */
    public boolean leaveGroup(SvgPlayer SvgPlayer) {
        VoicechatServerApi api = getApi();
        if (api == null) return false;

        VoicechatConnection connection = api.getConnectionOf(SvgPlayer.getUniqueId());
        if (connection == null) return false;

        connection.setGroup(null);
        SvgPlayer.sendMessage("[SVG] You left your group.");
        GroupsBroadcaster.broadcast();
        return true;
    }

    /**
     * Simply return whether the SvgPlayer is in a group
     * @param SvgPlayer SvgPlayer is/isn't in group
     * @return whether the player is in a group
     */
    public boolean isInGroup(SvgPlayer SvgPlayer) {
        VoicechatServerApi api = getApi();
        if (api == null) return false;

        VoicechatConnection connection = api.getConnectionOf(SvgPlayer.getUniqueId());
        if (connection == null) return false;

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

        if (type.equalsIgnoreCase("isolated")
                && !svgPlayer.hasPermission("svg.vc.group.type.isolated")) {
            return false;

        } else return !persistent
                || svgPlayer.hasPermission("svg.vc.group.setpersistent");
    }

    /**
     * Add a known Group
     * @param group the group to add
     */
    public void addGroup(Group group) {
        addGroup(group, true);
    }

    /**
     * @param broadcast whether to push an updated list to web clients
     */
    public void addGroup(Group group, boolean broadcast) {
        if (group == null || group.getName() == null) {
            return;
        }
        groups.put(group.getName(), group);
        if (broadcast && suppressBroadcastDepth.get() == 0) {
            GroupsBroadcaster.broadcast();
        }
    }

    /**
     * Remove a group from the manager and push an updated list to web clients.
     * @param group the group to remove
     */
    public void removeGroup(Group group) {
        if (group == null || group.getName() == null) {
            return;
        }
        groups.remove(group.getName());
        GroupsBroadcaster.broadcast();
    }
}
