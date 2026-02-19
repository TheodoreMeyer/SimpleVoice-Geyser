package io.github.theodoremeyer.spigotmc.simplevoicegeyser;

import de.maxhenkel.voicechat.api.Group;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Class that controls the main command for plugin: /svg
 */
public class SvgCommand implements CommandExecutor {

    /**
     * When command runs
     * @param sender who ran the command
     * @param cmd the command
     * @param label the label
     * @param args the args used in the command
     * @return whether it successfully ran or not
     */
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (args.length == 0) {
            sender.sendMessage(SVGPlugin.PREFIX + "§eUsage:");
            sender.sendMessage("/svg pswd <new-password>");
            sender.sendMessage("/svg cgroup <group-name> -t[type] -p[password] -ps[setpersistent]");
            return true;
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            //command to create pasword for logging into SimpleVoice-Geyser
            case "pswd": {
                Player player = requirePlayer(sender, "Only players can set their password.");
                if (player == null) return true;

                if (args.length != 2) {
                    sendUsage(sender, "/svg pswd <new-password>", "Sets your SimpleVoiceChat login password.");
                    return true;
                }

                String newPassword = args[1];

                if (!PlayerVcPswd.isPasswordLengthValid(newPassword)) {
                    sender.sendMessage(SVGPlugin.PREFIX + ChatColor.RED + "Password must be between 8 and 32 characters.");
                    return true;
                }

                PlayerVcPswd.setPassword(player, newPassword);
                sender.sendMessage(SVGPlugin.PREFIX + ChatColor.GREEN + "Your voice chat password has been set.");
                return true;
            }

            case "cgroup": {
                Player player = requirePlayer(sender, "Only players can create groups.");
                if (player == null) return true;

                if (!player.hasPermission("svg.vc.creategroup.create")) {
                    sender.sendMessage(SVGPlugin.PREFIX + ChatColor.RED + "You do not have permission to create groups.");
                    return true;
                }

                String groupName = null;
                String groupType = "open";
                String password = null;
                boolean persistent = false;

                for (int i = 1; i < args.length; i++) {
                    switch (args[i].toLowerCase()) {
                        case "-name":
                            if (i + 1 < args.length) groupName = args[++i];
                            break;
                        case "-t":
                            if (i + 1 < args.length) groupType = args[++i];
                            break;
                        case "-p":
                            if (i + 1 < args.length) password = args[++i];
                            break;
                        case "-ps":
                            persistent = true;
                            break;
                    }
                }

                if (groupName == null) {
                    sendUsage(sender,
                            "/svg cgroup -name <name> [-t open|normal|isolated] [-p password] [-ps]",
                            "Creates a new voice group.");
                    return true;
                }


                Group.Type type = Group.Type.OPEN; // Default to OPEN if no type specified.
                if ("isolated".equalsIgnoreCase(groupType)) {
                    type = Group.Type.ISOLATED;
                } else if ("normal".equalsIgnoreCase(groupType)) {
                    type = Group.Type.NORMAL;
                }

                if (type == Group.Type.ISOLATED &&
                        !player.hasPermission("svg.vc.creategroup.type.isolated")) {
                    sender.sendMessage(SVGPlugin.PREFIX + ChatColor.RED + "You don't have permission to create isolated groups.");
                    return true;
                }

                if (persistent &&
                        !player.hasPermission("svg.vc.creategroup.setpersistent")) {
                    sender.sendMessage(SVGPlugin.PREFIX + ChatColor.RED + "You don't have permission to set groups as persistent.");
                    return true;
                }

                boolean created = GroupManager.createGroup(
                        player, groupName, password, type, persistent, false);

                if (!created) {
                    sender.sendMessage(SVGPlugin.PREFIX + ChatColor.RED + "Failed to create group.");
                    return true;
                }

                sender.sendMessage(SVGPlugin.PREFIX + ChatColor.GREEN + "Group '" + groupName + "' created successfully.");
                return true;
            }

            case "jgroup": {
                Player player = requirePlayer(sender, "Only players can join groups.");
                if (player == null) return true;

                String groupName = null;
                String groupPassword = null;

                for (int i = 1; i < args.length; i++) {
                    switch (args[i].toLowerCase()) {
                        case "-n":
                            if (i + 1 < args.length) groupName = args[++i];
                            break;
                        case "-p":
                            if (i + 1 < args.length) groupPassword = args[++i];
                            break;
                    }
                }

                if (groupName == null) {
                    sendUsage(sender, "/svg jgroup -n <name> [-p password]", "Joins an existing voice group.");
                    return true;
                }

                boolean success = GroupManager.joinGroup(player, groupName, groupPassword);

                if (!success) {
                    sender.sendMessage(SVGPlugin.PREFIX + ChatColor.RED + "Failed to join group. Check name and password.");
                    return true;
                }

                sender.sendMessage(SVGPlugin.PREFIX + ChatColor.GREEN +
                        "Joined group '" + groupName + "'.");
                return true;
            }

            case "lgroup": {
                Player player = requirePlayer(sender, "Only Players can leave groups!");
                if (player == null) return true;
                GroupManager.leaveGroup(player);
                return true;
            }

            case "help": {
                sender.sendMessage(SVGPlugin.PREFIX + ChatColor.AQUA + " " + ChatColor.BOLD + "Commands");
                sender.sendMessage(ChatColor.GRAY + "----------------------------------");

                sender.sendMessage(ChatColor.YELLOW + "/svg pswd <password> "
                        + ChatColor.GRAY + "- Set your voice chat password");

                sender.sendMessage(ChatColor.YELLOW + "/svg cgroup -name <name> [-t type] [-p password] [-ps] "
                        + ChatColor.GRAY + "- Create a voice chat group");

                sender.sendMessage(ChatColor.YELLOW + "/svg jgroup -n <name> [-p password] "
                        + ChatColor.GRAY + "- Join a voice chat group");

                sender.sendMessage(ChatColor.YELLOW + "/svg lgroup "
                        + ChatColor.GRAY + "- Leave your current group");

                sender.sendMessage(ChatColor.YELLOW + "/svg help "
                        + ChatColor.GRAY + "- Show this help menu");

                return true;
            }


            default: {
                sender.sendMessage("§cUnknown subcommand. Try /svg [pswd | cgroup | jgroup | lgroup | help]");
                return true;
            }
        }
    }

    private Player requirePlayer(CommandSender sender, String errorMessage) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(SVGPlugin.PREFIX + ChatColor.RED + errorMessage);
            return null;
        }
        return player;
    }

    private void sendUsage(CommandSender sender, String usage, String description) {
        sender.sendMessage(SVGPlugin.PREFIX + ChatColor.YELLOW + "Usage: " + ChatColor.WHITE + usage);
        if (description != null && !description.isBlank()) {
            sender.sendMessage(ChatColor.GRAY + description);
        }
    }

}
