package io.github.theodoremeyer.simplevoicegeyser.core.commands.group;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgColor;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.Sender;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.commands.SubCommand;
import io.github.theodoremeyer.simplevoicegeyser.core.managers.GroupManager;

public final class LeaveGroupCommand implements SubCommand<Void> {

    private final GroupManager groupManager;

    public LeaveGroupCommand(GroupManager groupManager) {
        this.groupManager = groupManager;
    }

    @Override
    public String name() {
        return "lgroup";
    }

    @Override
    public void execute(Sender sender, Void args) {
        if (!(sender instanceof SvgPlayer player)) {
            sender.sendMessage(SvgCore.getPrefix() + SvgColor.RED + "Only players can leave groups.");
            return;
        }

        groupManager.leaveGroup(player);
    }
}