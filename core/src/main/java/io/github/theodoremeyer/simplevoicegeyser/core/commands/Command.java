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

/**
 * Represents SVG-G's /svg command. This class is responsible for registering and executing subcommands, as well as handling the command form for Bedrock players.
 */
public final class Command {

    /**
     * Map of sub commands
     */
    private final Map<String, SubCommand> commands = new HashMap<>();

    /**
     * Form Handler
     */
    private final FormHandler formHandler;

    /**
     * create the Command
     * @param groupManager group manager to pass to subcommands
     */
    public Command(GroupManager groupManager) {
        this.formHandler = new FormHandler(groupManager);

        register(new PswdCommand());
        register(new CreateGroupCommand(groupManager));
        register(new JoinGroupCommand(groupManager));
        register(new LeaveGroupCommand(groupManager));
        register(new HelpCommand());
    }

    /**
     * Get a specific sub command
     * @param name the name of sub command
     * @return command
     */
    public SubCommand get(String name) {
        return commands.get(name.toLowerCase());
    }

    /**
     * Execute a command
     * @param args command args
     * @return if it executed successfully
     */
    public boolean execute(CommandArgs args) {
        SubCommand cmd = get(args.sub());

        if (cmd == null) {
            return formOrHelp(args.getSender());
        }

        return cmd.execute(args);
    }

    /**
     * register a command
     * @param cmd sub command to register
     */
    private void register(SubCommand cmd) {
        commands.put(cmd.name(), cmd);
    }


    /**
     * Try opening the form for a player
     * @param sender the executor
     * @return success
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