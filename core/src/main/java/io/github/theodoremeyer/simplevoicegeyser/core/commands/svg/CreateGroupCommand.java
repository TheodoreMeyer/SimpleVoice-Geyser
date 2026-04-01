package io.github.theodoremeyer.simplevoicegeyser.core.commands.svg;

import de.maxhenkel.voicechat.api.Group;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgColor;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.Sender;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.commands.CommandArgs;
import io.github.theodoremeyer.simplevoicegeyser.core.commands.SubCommand;
import io.github.theodoremeyer.simplevoicegeyser.core.managers.GroupManager;

/**
 * /svg cgroup command
 */
public final class CreateGroupCommand implements SubCommand {

    /**
     * GroupManager
     */
    private final GroupManager groupManager;

    /**
     * Create the sub-Command
     * @param groupManager group manager
     */
    public CreateGroupCommand(GroupManager groupManager) {
        this.groupManager = groupManager;
    }

    /**
     * Name of command
     * @return cgroup
     */
    @Override
    public String name() {
        return "cgroup";
    }

    /**
     * Execute the sub command
     * @param args args passed by executor
     * @return success
     */
    @Override
    public boolean execute(CommandArgs args) {
        Sender sender = args.getSender();

        if (!(sender instanceof SvgPlayer player)) {
            sender.sendMessage(SvgCore.getPrefix() + SvgColor.RED +
                    "Only players can create groups.");
            return true;
        }

        if (!player.hasPermission("svg.vc.svg.create")) {
            sender.sendMessage(SvgCore.getPrefix() + SvgColor.RED +
                    "You do not have permission to create groups.");
            return true;
        }

        String name = args.get("name");
        String typeStr = args.has("type") ? args.get("type") : "open";
        String password = args.has("password") ? args.get("password") : "";
        boolean persistent = args.has("persistent") && Boolean.TRUE.equals(args.get("persistent"));

        // Validation (you were missing this)
        if (name == null || name.isBlank()) {
            sender.sendMessage(SvgCore.getPrefix() + SvgColor.RED +
                    "Usage: /svg cgroup -name <name> [-t type] [-p password] [-ps]");
            return true;
        }

        Group.Type type = groupManager.stringToType(typeStr);

        boolean created = groupManager.createGroup(
                player,
                name,
                password,
                type,
                persistent,
                false
        );

        if (!created) {
            sender.sendMessage(SvgCore.getPrefix() + SvgColor.RED +
                    "Failed to create svg.");
            return true;
        }

        sender.sendMessage(SvgCore.getPrefix() + SvgColor.GREEN +
                "Group '" + name + "' created successfully.");

        return true;
    }
}