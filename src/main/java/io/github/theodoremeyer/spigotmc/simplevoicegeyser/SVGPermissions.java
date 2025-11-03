package io.github.theodoremeyer.spigotmc.simplevoicegeyser;

import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

/**
 * The Class controlling plugin permissions
 */
public class SVGPermissions {

    /**
     * Permission stating whether player can join the voicechat
     */
    public static final Permission VC_JOIN = new Permission("svg.vc.join", PermissionDefault.TRUE);
    /**
     * Whether the player can create groups
     */
    public static final Permission VC_CREATE_GROUP = new Permission("svg.vc.creategroup.create", PermissionDefault.TRUE);
    /**
     * Whether the player can create group types other than open
     */
    public static final Permission VC_CREATE_GROUP_TYPE = new Permission("svg.vc.creategroup.type", PermissionDefault.TRUE);
    /**
     * Whether the player can create an isolated group
     */
    public static final Permission VC_CREATE_GROUP_TYPE_ISOLATED = new Permission("svg.vc.creategroup.type.isolated", PermissionDefault.OP);
    /**
     * Whether the player can create a persistent group
     */
    public static final Permission VC_CREATE_GROUP_SET_PERSISTENT = new Permission("svg.vc.creategroup.setpersistant", PermissionDefault.FALSE);
}
