package io.github.theodoremeyer.spigotmc.simplevoicegeyser;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.Group.Type;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SVG Group System manager
 */
public class GroupManager {

    /**
     * Groups that are listed/Available
     */
    protected static final Map<String, Group> groups = new ConcurrentHashMap<>(); //list of active groups

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
    public static boolean createGroup(Player player, String groupName, String password, Type groupType, boolean persistent, boolean created) {
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

        Group group = api.groupBuilder() //build the group
                .setName(groupName)
                .setPassword(password)
                .setType(groupType)
                .setPersistent(persistent)
                .build();

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
     * Set Player Group
     * @param player player affected
     * @param groupName uuid of the group, fetched by group.getName or by groups
     * @param password password of the group
     * @return True/False
     */
    public static boolean joinGroup(Player player, String groupName, String password) {

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
                SVGPlugin.debug("[SVG]", "Player " + player.getName()
                        + " tried to join password-protected group '"
                        + groupName + "' without providing a password.");
                return false;
            }

            if (!groupPassword.equals(password)) {
                SVGPlugin.debug("[SVG]","Incorrect password for group '"
                        + groupName + "' by " + player.getName());
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


    /**
     * Removes player from any group
     * @param player player to leave a group
     */
    public static void leaveGroup(Player player) {
        VoicechatServerApi api = getApi();
        if (api == null) return;

        VoicechatConnection connection = api.getConnectionOf(player.getUniqueId());
        if (connection == null) return; //if player exists

        connection.setGroup(null); //set the players group to a group that doesn't exist
        player.sendMessage("[SVG] You left your group.");
    }

    /**
     * Easy way to get the SVC Api.
     * @return VoicechatServerApi the SimpleVoiceChat api
     */
    private static VoicechatServerApi getApi() {
        return SVGPlugin.getBridge().getVcServerApi();
    }
}
