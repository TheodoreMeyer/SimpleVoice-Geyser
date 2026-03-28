package io.github.theodoremeyer.simplevoicegeyser.core.commands;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgColor;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.Sender;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.commands.svg.*;
import io.github.theodoremeyer.simplevoicegeyser.core.geyser.FormHandler;
import io.github.theodoremeyer.simplevoicegeyser.core.geyser.GeyserHook;
import io.github.theodoremeyer.simplevoicegeyser.core.managers.GroupManager;

import java.util.HashMap;
import java.util.Map;

public final class Command {

    private final Map<String, SubCommand> commands = new HashMap<>();
    private final FormHandler formHandler;

    public Command(GroupManager groupManager) {
        this.formHandler = new FormHandler(groupManager);

        register(new PswdCommand());
        register(new CreateGroupCommand(groupManager));
        register(new JoinGroupCommand(groupManager));
        register(new LeaveGroupCommand(groupManager));
        register(new HelpCommand());
    }

    public SubCommand get(String name) {
        return commands.get(name.toLowerCase());
    }

    public boolean execute(CommandArgs args) {
        SubCommand cmd = get(args.sub());

        if (cmd == null) {
            return formOrHelp(args.getSender());
        }

        return cmd.execute(args);
    }

    private void register(SubCommand cmd) {
        commands.put(cmd.name(), cmd);
    }


    /**
     * Try opening the form for a player
     */
    public boolean formOrHelp(Sender sender) {
        if (sender instanceof SvgPlayer player) {
            Boolean isBedrock = GeyserHook.isBedrock(player.getUniqueId());

            if (isBedrock != null && isBedrock) {
                return formHandler.openCommand(player);
            } else {
                sender.sendMessage(SvgCore.getPrefix() + SvgColor.RED +
                        "Unknown subcommand. Try /svg help");
            }
            return true;
        }

        sender.sendMessage(SvgCore.getPrefix() + SvgColor.RED +
                "Unknown subcommand. Try /svg help");
        return true;
    }
}