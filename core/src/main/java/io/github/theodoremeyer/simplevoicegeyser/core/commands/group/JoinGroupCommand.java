package io.github.theodoremeyer.simplevoicegeyser.core.commands.group;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgColor;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.Sender;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.commands.SubCommand;
import io.github.theodoremeyer.simplevoicegeyser.core.managers.GroupManager;

public final class JoinGroupCommand implements SubCommand<JoinGroupArgs> {

    private final GroupManager groupManager;

    public JoinGroupCommand(GroupManager groupManager) {
        this.groupManager = groupManager;
    }

    @Override
    public String name() {
        return "jgroup";
    }

    @Override
    public void execute(Sender sender, JoinGroupArgs args) {
        if (!(sender instanceof SvgPlayer player)) {
            sender.sendMessage(SvgCore.getPrefix() + SvgColor.RED + "Only players can join groups.");
            return;
        }

        boolean success = groupManager.joinGroup(player, args.name(), args.password());

        if (!success) {
            sender.sendMessage(SvgCore.getPrefix() + SvgColor.RED +
                    "Failed to join group. Check name and password.");
            return;
        }

        sender.sendMessage(SvgCore.getPrefix() + SvgColor.GREEN +
                "Joined group '" + args.name() + "'.");
    }
}