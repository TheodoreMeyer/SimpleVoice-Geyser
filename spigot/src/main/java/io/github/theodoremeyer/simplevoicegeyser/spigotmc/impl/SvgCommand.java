package io.github.theodoremeyer.simplevoicegeyser.spigotmc.impl;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.Sender;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.commands.CommandArgs;
import io.github.theodoremeyer.simplevoicegeyser.core.commands.CommandFlagParser;
import io.github.theodoremeyer.simplevoicegeyser.spigotmc.impl.sender.BukkitConsole;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;


public class SvgCommand implements CommandExecutor, TabCompleter {

    //-----
    //COMMAND EXECUTION
     //-----
    @Override
    public boolean onCommand(CommandSender sender, Command command, String s, String[] args) {

        if (sender instanceof Player p) {
            SvgPlayer player = SvgCore.getPlayerManager().getPlayer(p.getUniqueId());

            if (player == null) {
                sender.sendMessage("Unable to find the SvgPlayer instance for you. Please try again.");
                return true;
            }

            return onCommand(player, args);

        } else if (sender instanceof ConsoleCommandSender) {
            return onCommand(new BukkitConsole(), args);
        } else {
            sender.sendMessage("Only players and console can use this command.");
            return true;
        }
    }

    public boolean onCommand(Sender sender, String[] args) {

        if (args.length == 0) {
            return SvgCore.getCommand().formOrHelp(sender);
        }

        String sub = args[0].toLowerCase();

        // Parse args into CommandArgs
        CommandArgs parsedArgs = parseArgs(new CommandArgs(sub, sender), sub, args);

        return SvgCore.getCommand().execute(parsedArgs);
    }

    /**
     * Spigot-style argument parsing layer
     */
    private CommandArgs parseArgs(CommandArgs out, String sub, String[] args) {

        switch (sub) {
            case "pswd" -> {
                if (args.length >= 2) {
                    out.put("password", args[1]);
                }
            }

            case "jgroup" -> {
                if (args.length >= 2) {
                    out.put("name", args[1]);
                }
                if (args.length >= 3) {
                    out.put("password", args[2]);
                }
            }

            case "cgroup" -> {
                out.put("name", args[1]);
                out.putAll(CommandFlagParser.parse(CommandFlagParser.CGROUP_FLAGS, Arrays.copyOfRange(args, 2, args.length), 0));
            }
        }

        return out;
    }

    //---------------
    // TAB COMPLETION
    //---------------
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {

        if (args.length == 1) {
            return suggestSubcommands(args[0]);
        }

        String sub = args[0].toLowerCase();

        return switch (sub) {
            case "pswd" -> Collections.emptyList();
            case "cgroup" -> suggestCreateGroup(args);
            default -> Collections.emptyList();
        };
    }

    private List<String> suggestSubcommands(String input) {
        return filter(input, List.of("help", "lgroup", "pswd", "jgroup", "cgroup"));
    }

    private List<String> suggestCreateGroup(String[] args) {

        // /svg cgroup <name>
        if (args.length == 2) {
            return Collections.emptyList();
        }

        return suggestFlags(args);
    }

    private List<String> filter(String input, List<String> options) {
        String lower = input.toLowerCase();
        return options.stream()
                .filter(opt -> opt.toLowerCase().startsWith(lower))
                .toList();
    }

    private List<String> suggestFlags(String[] args) {

        String current = args[args.length - 1].toLowerCase();

        Map<String, Boolean> flags = Map.of(
                "-t", true,
                "-p", true,
                "-ps", false
        );

        Set<String> used = new HashSet<>();

        for (String arg : args) {
            if (flags.containsKey(arg.toLowerCase())) {
                used.add(arg.toLowerCase());
            }
        }

        // If previous arg expects value → don't suggest flags
        if (args.length >= 2) {
            String prev = args[args.length - 2].toLowerCase();
            if (flags.getOrDefault(prev, false)) {
                return Collections.emptyList();
            }
        }

        return flags.keySet().stream()
                .filter(flag -> !used.contains(flag))
                .filter(flag -> flag.startsWith(current))
                .toList();
    }
}


