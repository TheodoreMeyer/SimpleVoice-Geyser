package io.github.theodoremeyer.simplevoicegeyser.core.commands.svg;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgColor;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.Sender;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.commands.CommandArgs;
import io.github.theodoremeyer.simplevoicegeyser.core.commands.SubCommand;
import io.github.theodoremeyer.simplevoicegeyser.core.managers.GroupManager;

/**
 * Represents /svg jgroup
 */
public final class JoinGroupCommand implements SubCommand {

    /**
     * Group Manager
     */
    private final GroupManager groupManager;

    /**
     * Create an instance of the command
     * @param groupManager group manager to join groups with
     */
    public JoinGroupCommand(GroupManager groupManager) {
        this.groupManager = groupManager;
    }

    /**
     * sub command name
     * @return jgroup
     */
    @Override
    public String name() {
        return "jgroup";
    }

    /**
     * Execute this sub command
     * @param args args to execute with
     * @return success
     */
    @Override
    public boolean execute(CommandArgs args) {
        Sender sender = args.getSender();
        if (!(sender instanceof SvgPlayer player)) {
            sender.sendMessage(SvgCore.getPrefix() + SvgColor.RED + "Only players can join groups.");
            return true;
        }

        String name = args.get("name");
        String password = args.get("password");

        if (name == null || name.isBlank()) {
            sender.sendMessage(SvgCore.getPrefix() + SvgColor.RED +
                    "Usage: /svg jgroup <name> [password]");
            return true;
        }

        boolean success = groupManager.joinGroup(player, name, password);

        if (!success) {
            sender.sendMessage(SvgCore.getPrefix() + SvgColor.RED +
                    "Failed to join svg. Check name and password.");
            return true;
        }

        sender.sendMessage(SvgCore.getPrefix() + SvgColor.GREEN +
                "Joined svg '" + name + "'.");

        return true;
    }
}