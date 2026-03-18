package io.github.theodoremeyer.simplevoicegeyser.core;

import de.maxhenkel.voicechat.api.Group;
import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgColor;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.Sender;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.geyser.FormHandler;
import io.github.theodoremeyer.simplevoicegeyser.core.geyser.GeyserHook;
import io.github.theodoremeyer.simplevoicegeyser.core.managers.GroupManager;

/**
 * Class that controls the main command for plugin: /svg
 */
public final class Command {

    /**
     * Interface to Group System
     */
    private final GroupManager groupManager;

    /**
     * Cumulus forms
     */
    private final FormHandler formHandler;

    /**
     * The Svg Command
     * @param groupManager the manager to work with groups
     */
    protected Command(GroupManager groupManager) {
        this.groupManager = groupManager;
        this.formHandler = new FormHandler(groupManager);
    }

    /**
     * When command runs
     * @param sender who ran the command
     * @param args the args used in the command
     * @return whether it successfully ran or not
     */
    public boolean onCommand(Sender sender, String[] args) {

        if (args.length == 0) {
            return formOrHelp(sender);
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            //command to create password for logging into SimpleVoice-Geyser
            case "pswd": {
                SvgPlayer player = requirePlayer(sender, "Only players can set their password.");
                if (player == null) return true;

                if (args.length != 2) {
                    sendUsage(sender, "/svg pswd <new-password>", "Sets your SimpleVoiceChat login password.");
                    return true;
                }

                String newPassword = args[1];

                SvgCore.getPasswordManager().setPassword(player, newPassword);
                return true;
            }

            case "cgroup": {
                SvgPlayer player = requirePlayer(sender, "Only players can create groups.");
                if (player == null) return true;

                if (!player.hasPermission("svg.vc.creategroup.create")) {
                    sender.sendMessage(SvgCore.getPrefix() + SvgColor.RED + "You do not have permission to create groups.");
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

                Group.Type type = groupManager.stringToType(groupType);
                boolean created = groupManager.createGroup(player, groupName, password, type, persistent, false);

                if (!created) {
                    sender.sendMessage(SvgCore.getPrefix() + SvgColor.RED + "Failed to create group.");
                    return true;
                }

                sender.sendMessage(SvgCore.getPrefix() + SvgColor.GREEN + "Group '" + groupName + "' created successfully.");
                return true;
            }

            case "jgroup": {
                SvgPlayer player = requirePlayer(sender, "Only players can join groups.");
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

                boolean success = groupManager.joinGroup(player, groupName, groupPassword);

                if (!success) {
                    sender.sendMessage(SvgCore.getPrefix() + SvgColor.RED + "Failed to join group. Check name and password.");
                    return true;
                }

                sender.sendMessage(SvgCore.getPrefix() + SvgColor.GREEN +
                        "Joined group '" + groupName + "'.");
                return true;
            }

            case "lgroup": {
                SvgPlayer player = requirePlayer(sender, "Only Players can leave groups!");
                if (player == null) return true;
                groupManager.leaveGroup(player);
                return true;
            }

            case "help": {
                sender.sendMessage(SvgCore.getPrefix() + SvgColor.AQUA + " " + SvgColor.BOLD + "Commands");
                sender.sendMessage(SvgColor.GRAY + "----------------------------------");

                sender.sendMessage(SvgColor.YELLOW + "/svg pswd <password> "
                        + SvgColor.GRAY + "- Set your voice chat password");

                sender.sendMessage(SvgColor.YELLOW + "/svg cgroup -name <name> [-t type] [-p password] [-ps] "
                        + SvgColor.GRAY + "- Create a voice chat group");

                sender.sendMessage(SvgColor.YELLOW + "/svg jgroup -n <name> [-p password] "
                        + SvgColor.GRAY + "- Join a voice chat group");

                sender.sendMessage(SvgColor.YELLOW + "/svg lgroup "
                        + SvgColor.GRAY + "- Leave your current group");

                sender.sendMessage(SvgColor.YELLOW + "/svg help "
                        + SvgColor.GRAY + "- Show this help menu");

                return true;
            }

            case null, default: {
                return formOrHelp(sender);
            }
        }
    }

    private boolean formOrHelp(Sender sender) {
        if (sender instanceof SvgPlayer player) {
            Boolean isBedrock = GeyserHook.isBedrock(player.getUniqueId());

            if (isBedrock != null && isBedrock) {
                return formHandler.openCommand(player);
            } else {
                sender.sendMessage(SvgCore.getPrefix() + "§cUnknown subcommand. Try /svg help");
            }
            return true;
        }

        sender.sendMessage(SvgCore.getPrefix() + "§cUnknown subcommand. Try /svg help");
        return true;
    }

    private SvgPlayer requirePlayer(Sender sender, String errorMessage) {
        if (!(sender instanceof SvgPlayer player)) {
            sender.sendMessage(SvgCore.getPrefix() + SvgColor.RED + errorMessage);
            return null;
        }
        return player;
    }

    private void sendUsage(Sender sender, String usage, String description) {
        sender.sendMessage(SvgCore.getPrefix() + SvgColor.YELLOW + "Usage: " + SvgColor.WHITE + usage);
        if (description != null && !description.isBlank()) {
            sender.sendMessage(SvgColor.GRAY + description);
        }
    }
}
