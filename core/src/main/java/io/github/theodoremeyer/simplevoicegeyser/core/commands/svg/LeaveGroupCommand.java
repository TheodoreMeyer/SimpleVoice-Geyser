package io.github.theodoremeyer.simplevoicegeyser.core.commands.svg;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgColor;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.Sender;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.commands.CommandArgs;
import io.github.theodoremeyer.simplevoicegeyser.core.commands.SubCommand;
import io.github.theodoremeyer.simplevoicegeyser.core.managers.GroupManager;

/**
 * Represents /svg lgroup
 */
public final class LeaveGroupCommand implements SubCommand {

    /**
     * Group Manager
     */
    private final GroupManager groupManager;

    /**
     * create an instance of the command
     * @param groupManager group manager
     */
    public LeaveGroupCommand(GroupManager groupManager) {
        this.groupManager = groupManager;
    }

    /**
     * Name of sub command
     * @return lgroup
     */
    @Override
    public String name() {
        return "lgroup";
    }

    /**
     * Execute this subCommand
     * @param args args to execute with
     * @return success
     */
    @Override
    public boolean execute(CommandArgs args) {
        Sender sender = args.getSender();
        if (!(sender instanceof SvgPlayer player)) {
            sender.sendMessage(SvgCore.getPrefix() + SvgColor.RED +
                    "Only players can leave groups.");
            return true;
        }

        groupManager.leaveGroup(player);
        return true;
    }
}