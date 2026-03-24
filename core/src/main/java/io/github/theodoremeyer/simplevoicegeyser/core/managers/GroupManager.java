package io.github.theodoremeyer.simplevoicegeyser.core.managers;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.Group.Type;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgColor;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.svc.VoiceChatBridge;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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
     * @param groupType Open, Normal, or Isolated. Default is Open.
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
                    return true;
                } else {
                    SvgPlayer.sendMessage(SvgCore.getPrefix() + SvgColor.DARK_RED + "Vc connection is null");
                    return false;
                }
            }
        }

        if (groupType == null) groupType = Type.NORMAL;

        Group group;
        if (password == null || password.isEmpty()) { //build the group
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

        if (connection != null) {
            if (connection.getGroup() !=null) {
                SvgPlayer.sendMessage(SvgCore.getPrefix() + SvgColor.RED + "Leaving Group: " + connection.getGroup().getName());
            }
            connection.setGroup(group);
            SvgPlayer.sendMessage(SvgCore.getPrefix() + SvgColor.BLUE + "Joined Group: " + group.getName());
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
     * Set SvgPlayer Group
     * @param SvgPlayer SvgPlayer affected
     * @param groupName uuid of the group, fetched by group.getName or by groups
     * @param password password of the group
     * @return True/False
     */
    public boolean joinGroup(SvgPlayer SvgPlayer, String groupName, String password) {

        VoicechatServerApi api = getApi();
        VoicechatConnection connection = api.getConnectionOf(SvgPlayer.getUniqueId());

        if (connection == null) {
            SvgCore.getLogger().warning("[SVG] No voice connection found for SvgPlayer "
                    + SvgPlayer.getName());
            return false;
        }

        if (groupName == null) {
            SvgCore.getLogger().warning("[SVG] SvgPlayer " + SvgPlayer.getName()
                    + " attempted to join group with null name.");
            return false;
        }

        Group group = groups.get(groupName);

        if (group == null) {
            SvgCore.getLogger().warning("[SVG] Unknown group '" + groupName
                    + "' requested by " + SvgPlayer.getName());
            return false;
        }

        String groupPassword = null;

        try {
            Field groupField = group.getClass().getDeclaredField("group");
            groupField.setAccessible(true);
            Object groupObject = groupField.get(group);

            Field passwordField = groupObject.getClass()
                    .getDeclaredField("password");
            passwordField.setAccessible(true);

            groupPassword = (String) passwordField.get(groupObject);

        } catch (Throwable e) {
            SvgCore.getLogger().warning("[SVG] Failed to reflect password of group '"
                    + group.getName() + "' (" + group.getId() + "): " + e.getMessage());
        }

        // Debug getLogger password state
        SvgCore.debug("[GROUPS]", "SvgPlayer: " + SvgPlayer.getName()
                + " | Group: " + groupName
                + " | Provided Password: " + password
                + " | Actual Password: " + groupPassword);

        // Handle password check safely
        if (groupPassword != null) {

            if (password == null) {
                return false;
            }

            if (!groupPassword.equals(password)) {
                return false;
            }
        }

        // Leave previous group
        if (connection.isInGroup()) {
            SvgCore.debug("[SVG] ", SvgPlayer.getName()
                    + " left group " + connection.getGroup().getName());
            SvgPlayer.sendMessage("You left group: "
                    + connection.getGroup().getName());
        }

        connection.setGroup(group);

        SvgCore.debug("[SVG]", SvgPlayer.getName()
                + " successfully joined group '" + groupName + "'");

        return true;
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
     * Removes SvgPlayer from any group
     * @param SvgPlayer SvgPlayer to leave a group
     */
    public void leaveGroup(SvgPlayer SvgPlayer) {
        VoicechatServerApi api = getApi();
        if (api == null) return;

        VoicechatConnection connection = api.getConnectionOf(SvgPlayer.getUniqueId());
        if (connection == null) return; //if SvgPlayer exists

        connection.setGroup(null); //set the SvgPlayers group to a group that doesn't exist
        SvgPlayer.sendMessage("[SVG] You left your group.");
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

        } else if (persistent
                && !svgPlayer.hasPermission("svg.vc.group.setpersistent")) {

            return false;
        }

        return true;
    }

    /**
     * Add a known Group
     * @param group the group to add
     */
    public void addGroup(Group group) {
        groups.put(group.getName(), group);
    }

    /**
     * Remove a group from the manager
     * @param group the group to remove
     */
    public void removeGroup(Group group) {
        groups.remove(group.getName());
    }
}
