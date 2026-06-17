package io.github.theodoremeyer.simplevoicegeyser.velocity;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.RawCommand;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.Player;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.Sender;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.commands.CommandArgs;
import io.github.theodoremeyer.simplevoicegeyser.velocity.impl.sender.VelocityConsole;
import net.kyori.adventure.text.Component;

import java.util.Collections;
import java.util.List;

public class VelocityCommand implements RawCommand {

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments().split(" ", -1);

        Sender sender;
        if (source instanceof Player player) {
            SvgPlayer svgPlayer = SvgCore.getPlayerManager().getPlayer(player.getUniqueId());
            if (svgPlayer == null) {
                source.sendMessage(Component.text("Unable to find your player instance."));
                return;
            }
            sender = svgPlayer;
        } else if (source instanceof ConsoleCommandSource console) {
            sender = new VelocityConsole(console);
        } else {
            return;
        }

        executeCore(sender, args);
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments().split(" ", -1);
        if (args.length <= 1) {
            String input = args[0];
            List<String> subs = List.of("help", "pswd", "jgroup", "lgroup", "cgroup");
            if (input.isEmpty()) return subs;
            return subs.stream()
                    .filter(s -> s.startsWith(input.toLowerCase()))
                    .toList();
        }
        return Collections.emptyList();
    }

    private void executeCore(Sender sender, String[] args) {
        if (args.length == 0 || args[0].isEmpty()) {
            SvgCore.getCommand().formOrHelp(sender);
            return;
        }

        String sub = args[0];
        CommandArgs parsedArgs = new CommandArgs(sub, sender);

        switch (sub) {
            case "pswd" -> {
                if (args.length >= 2) parsedArgs.put("password", args[1]);
            }
            case "jgroup" -> {
                if (args.length >= 2) parsedArgs.put("name", args[1]);
                if (args.length >= 3) parsedArgs.put("password", args[2]);
            }
            case "cgroup" -> {
                if (args.length >= 2) parsedArgs.put("name", args[1]);
            }
        }

        SvgCore.getCommand().execute(parsedArgs);
    }
}
