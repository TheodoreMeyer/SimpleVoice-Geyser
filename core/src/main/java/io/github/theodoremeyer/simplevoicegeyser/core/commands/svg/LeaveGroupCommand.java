package io.github.theodoremeyer.simplevoicegeyser.core.commands.svg;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgColor;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.Sender;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.commands.CommandArgs;
import io.github.theodoremeyer.simplevoicegeyser.core.commands.SubCommand;
import io.github.theodoremeyer.simplevoicegeyser.core.managers.GroupManager;

public final class LeaveGroupCommand implements SubCommand {

    private final GroupManager groupManager;

    public LeaveGroupCommand(GroupManager groupManager) {
        this.groupManager = groupManager;
    }

    @Override
    public String name() {
        return "lgroup";
    }

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