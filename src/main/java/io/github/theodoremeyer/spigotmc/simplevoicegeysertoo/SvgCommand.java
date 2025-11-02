package io.github.theodoremeyer.spigotmc.simplevoicegeysertoo;

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
            sender.sendMessage("[SVG] §eUsage:");
            sender.sendMessage("/svg pswd <new-password>");
            sender.sendMessage("/svg cgroup <group-name> -t[type] -p[password] -ps[setpersistent]");
            return true;
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            //command to create pasword for logging into SimpleVoice-Geyser
            case "pswd":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cOnly players can set their password.");
                    return true;
                }

                if (args.length < 2) {
                    sender.sendMessage(ChatColor.DARK_AQUA + "Usage: /svg pswd <new-password>");
                    return true;
                }

                String newPassword = args[1];
                if (!PlayerVcPswd.isPasswordLengthValid(newPassword)) {
                    sender.sendMessage("§cPassword must be between 5 and 20 characters.");
                    return true;
                }

                PlayerVcPswd.setPassword(player, newPassword);
                sender.sendMessage("§aYour voice chat password has been set.");
                return true;

            case "cgroup":
                if (!(sender instanceof Player playerSender)) {
                    sender.sendMessage("§cOnly players can create groups.");
                    return true;
                }

                if (!playerSender.hasPermission("svg.vc.creategroup.create")) {
                    sender.sendMessage("§cYou do not have permission to create groups.");
                    return true;
                }

                String groupName = null;
                String groupType = "open"; // Default to open
                String password = "1a2b"; // Default password
                boolean persistent = false; // Default persistent false

                // Parse arguments for group creation
                for (int i = 1; i < args.length; i++) {
                    switch (args[i]) {
                        case "-t":
                            if (i + 1 <= args.length) {
                                groupType = args[++i].toLowerCase();
                            }
                            break;
                        case "-p":
                            if (i + 1 <= args.length) {
                                password = args[++i];
                            }
                            break;
                        case "-ps":
                            persistent = true;
                            break;
                        case "-name":
                            if (i + 1 <= args.length) {
                                groupName = args[++i];
                            }
                    }
                }

                // Check if the player has permission to create isolated groups. May be moved.
                if ("isolated".equalsIgnoreCase(groupType) && !playerSender.hasPermission("svg.vc.creategroup.type.isolated")) {
                    sender.sendMessage("§cYou don't have permission to create an isolated group.");
                    return true;
                }

                // Check if the player has permission to set persistent. May be moved.
                if (persistent && !playerSender.hasPermission("svg.vc.creategroup.setpersistant")) {
                    sender.sendMessage("§cYou don't have permission to set persistent for the group.");
                    return true;
                }

                //create the group
                boolean groupCreated = GroupManager.createGroup(playerSender, groupName, password, groupType, persistent, false);
                if (groupCreated) {
                    sender.sendMessage("§aGroup '" + groupName + "' created successfully.");
                } else {
                    sender.sendMessage("§cFailed to create group.");
                }

                return true;
            case  "jgroup":
                String groupname = null;
                String grouppassword = null;

                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Only Players Can join groups!");
                    return true;
                }

                for (int i = 1; i < args.length; i++) {
                    switch (args[i]) {
                        case "-p":
                            if (i + 1 < args.length) {
                                grouppassword = args[++i];
                            }
                            break;
                        case "-n":
                            if (i + 1 <= args.length) {
                                groupname = args[++i];
                            }
                    }
                }
                boolean success = GroupManager.joinGroup(player, groupname, grouppassword);
                if (!success) {
                    SVGPlugin.log().warning("failed to join group for " + player.getName());
                    player.sendMessage("Failed to join group " + groupname);
                }
                return true;

            default:
                sender.sendMessage("§cUnknown subcommand. Try /svg [pswd | cgroup | jgroup]");
                return true;
        }
    }
}
