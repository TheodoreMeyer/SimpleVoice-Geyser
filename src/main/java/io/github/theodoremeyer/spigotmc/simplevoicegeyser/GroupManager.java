package io.github.theodoremeyer.spigotmc.simplevoicegeyser;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.Group.Type;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SVG Group System manager
 */
public class GroupManager {

    /**
     * Interface to group system
     */
    private final VoiceChatBridge bridge;

    /**
     * Groups that are listed/Available
     */
    protected final Map<String, Group> groups = new ConcurrentHashMap<>(); //list of active groups

    protected GroupManager(VoiceChatBridge api) {
        this.bridge = api;
    }

    /**
     * Create an SVC Group for Player
     * @param player Player creating the group
     * @param groupName name of the group being created
     * @param password password of the group, default is 1a2b
     * @param groupType Open, Normal, or Isolated. Default is Open.
     * @param persistent Whether the group stays with no players or not
     * @param created whether to allow join already created groups
     * @return True/False
     */
    public boolean createGroup(Player player, String groupName, String password, Type groupType, boolean persistent, boolean created) {
        VoicechatServerApi api = getApi();
        if (api == null) return false;

        VoicechatConnection connection = api.getConnectionOf(player.getUniqueId());

        if (groups.containsKey(groupName)) { //whether the group already exists
            if (!created) { //if we can add the player if the group is already created
                player.sendMessage(SVGPlugin.PREFIX + ChatColor.DARK_RED + "Group " + groupName + "already exists.");
                return false;
            } else {
                if (connection != null) {
                    connection.setGroup(groups.get(groupName));
                    player.sendMessage(SVGPlugin.PREFIX + ChatColor.DARK_BLUE + "joined Group " + groupName);
                    return true;
                } else {
                    player.sendMessage(SVGPlugin.PREFIX + ChatColor.DARK_RED + "Vc connection is null");
                    return false;
                }
            }
        }

        if (groupType == null) groupType = Type.NORMAL;

        Group group;
        if (password.isEmpty()) { //build the group
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
                player.sendMessage(SVGPlugin.PREFIX + ChatColor.RED + "Leaving Group: " + connection.getGroup().getName());
            }
            connection.setGroup(group);
            player.sendMessage(SVGPlugin.PREFIX + ChatColor.BLUE + "Joined Group: " + group.getName());
            return true;
        } else {
            player.sendMessage("Connection is null!");
        }

        player.sendMessage("Args not used correctly");
        return false; // The Group could not be set.
    }

    /**
     * Translate a String to Group Type
     * NOTE: Returns A default group type: OPEN
     * @param string the translatable String
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
     * Set Player Group
     * @param player player affected
     * @param groupName uuid of the group, fetched by group.getName or by groups
     * @param password password of the group
     * @return True/False
     */
    public boolean joinGroup(Player player, String groupName, String password) {

        VoicechatServerApi api = getApi();
        VoicechatConnection connection = api.getConnectionOf(player.getUniqueId());

        if (connection == null) {
            SVGPlugin.log().warning("[SVG] No voice connection found for player "
                    + player.getName());
            return false;
        }

        if (groupName == null) {
            SVGPlugin.log().warning("[SVG] Player " + player.getName()
                    + " attempted to join group with null name.");
            return false;
        }

        Group group = groups.get(groupName);

        if (group == null) {
            SVGPlugin.log().warning("[SVG] Unknown group '" + groupName
                    + "' requested by " + player.getName());
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
            SVGPlugin.log().warning("[SVG] Failed to reflect password of group '"
                    + group.getName() + "' (" + group.getId() + "): " + e.getMessage());
        }

        // Debug log password state
        SVGPlugin.debug("[GROUPS]", "Player: " + player.getName()
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
            SVGPlugin.debug("[SVG] ", player.getName()
                    + " left group " + connection.getGroup().getName());
            player.sendMessage("You left group: "
                    + connection.getGroup().getName());
        }

        connection.setGroup(group);

        SVGPlugin.debug("[SVG]", player.getName()
                + " successfully joined group '" + groupName + "'");

        return true;
    }

    public List<String> getGroupNames() {
        List<String> names = new ArrayList<>();

        for (Group group : groups.values()) {
            names.add(group.getName());
        }
        return names;
    }
    /**
     * Returns the name of the current group the player is in
     * @param player
     * @return
     */
    public Optional<String> getJoinedGroupName(Player player) {
        VoicechatServerApi api = getApi();
        if (api == null) return Optional.empty();

        VoicechatConnection connection = api.getConnectionOf(player.getUniqueId());
        if (connection == null || !connection.isInGroup()) Optional.empty();

        Group group = connection.getGroup();
        if (group == null) return Optional.empty();

        return Optional.ofNullable(group.getName());
    }
    /**
     * Removes player from any group
     * @param player player to leave a group
     */
    public void leaveGroup(Player player) {
        VoicechatServerApi api = getApi();
        if (api == null) return;

        VoicechatConnection connection = api.getConnectionOf(player.getUniqueId());
        if (connection == null) return; //if player exists

        connection.setGroup(null); //set the players group to a group that doesn't exist
        player.sendMessage("[SVG] You left your group.");
    }

    /**
     * Simply return whether the player is in a group
     * @param player player is/isn't in group
     */
    public boolean isInGroup(Player player) {
        VoicechatServerApi api = getApi();
        if (api == null) return false;

        VoicechatConnection connection = api.getConnectionOf(player.getUniqueId());
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
     * Easy way to see if a player can create the group type
     */
    public boolean canCreate(Player player, String type, boolean persistent) {

        if (type.equalsIgnoreCase("isolated")
                && !player.hasPermission("svg.vc.group.type.isolated")) {
            return false;

        } else if (persistent
                && !player.hasPermission("svg.vc.creategroup.setpersistent")) {

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

    public void removeGroup(Group group) {
        groups.remove(group.getName());
    }
}
