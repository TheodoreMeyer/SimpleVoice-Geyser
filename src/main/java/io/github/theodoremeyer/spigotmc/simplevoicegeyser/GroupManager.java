package io.github.theodoremeyer.spigotmc.simplevoicegeyser;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.Group.Type;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

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
     * @param type Open, Normal, or Isolated. Default is Open.
     * @param persistent Whether the group stays with no players or not
     * @param created whether to allow join already created groups
     * @return True/False
     */
    public static boolean createGroup(Player player, String groupName, String password, String type, boolean persistent, boolean created) {
        VoicechatServerApi api = getApi();
        if (api == null) return false;

        if (groups.containsKey(groupName)) { //whether the group already exists
            if (!created) { //if we can add the player if the group is already created
                player.sendMessage(ChatColor.DARK_RED + "Group " + groupName + "already exists.");
                return false;
            } else {
                VoicechatConnection connection = api.getConnectionOf(player.getUniqueId()); //if player is null
                if (connection != null) {
                    connection.setGroup(groups.get(groupName));
                    player.sendMessage(ChatColor.DARK_BLUE + "Successfully Joined Group " + groupName);
                    return true;
                } else {
                    player.sendMessage(ChatColor.DARK_RED + "Vc connection is null");
                    return false;
                }
            }
        }

        Type groupType = Type.OPEN; // Default to OPEN if no type specified.
        if ("isolated".equalsIgnoreCase(type)) {
            groupType = Type.ISOLATED;
        } else if ("normal".equalsIgnoreCase(type)) {
            groupType = Type.NORMAL;
        }

        Group group = api.groupBuilder() //build the group
                .setName(groupName)
                .setPassword(password)
                .setType(groupType)
                .setPersistent(persistent)
                .build();

        VoicechatConnection connection = api.getConnectionOf(player.getUniqueId());
        if (connection != null) {
            if (connection.getGroup() !=null) {
                player.sendMessage(ChatColor.RED + "Leaving Group: " + connection.getGroup().getName());
            }
            connection.setGroup(group);
            player.sendMessage(ChatColor.BLUE + "Joined Group: " + group.getName());
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
        Group group = groups.get(groupName);
        if (connection != null) { //if player exists
            if (connection.isInGroup()) {
                player.sendMessage("You left a group");
            }
            connection.setGroup(group);

        }
        return false;
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
    }

    /**
     * Easy way to get the SVC Api.
     * @return VoicechatServerApi the SimpleVoiceChat api
     */
    private static VoicechatServerApi getApi() {
        return SVGPlugin.getBridge().getVcServerApi();
    }
}
