package io.github.theodoremeyer.simplevoicegeyser.core.commands.group;

import de.maxhenkel.voicechat.api.Group;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.Sender;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.commands.SubCommand;
import io.github.theodoremeyer.simplevoicegeyser.core.managers.GroupManager;

public final class CreateGroupCommand implements SubCommand<CreateGroupArgs> {

    private final GroupManager groupManager;

    public CreateGroupCommand(GroupManager groupManager) {
        this.groupManager = groupManager;
    }

    @Override
    public String name() {
        return "cgroup";
    }

    @Override
    public void execute(Sender sender, CreateGroupArgs args) {

        if (!(sender instanceof SvgPlayer player)) {
            sender.sendMessage("Only players can create groups.");
            return;
        }

        if (!player.hasPermission("svg.vc.group.create")) {
            sender.sendMessage("No permission.");
            return;
        }

        Group.Type type = groupManager.stringToType(args.type());

        boolean created = groupManager.createGroup(
                player,
                args.name(),
                args.password(),
                type,
                args.persistent(),
                false
        );

        if (!created) {
            sender.sendMessage("Failed to create group.");
            return;
        }

        sender.sendMessage("Group created: " + args.name());
    }
}