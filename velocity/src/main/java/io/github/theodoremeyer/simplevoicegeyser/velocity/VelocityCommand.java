package io.github.theodoremeyer.simplevoicegeyser.velocity;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.RawCommand;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.Player;
import io.github.theodoremeyer.simplevoicegeyser.velocity.impl.data.ProxyPasswordStore;
import io.github.theodoremeyer.simplevoicegeyser.velocity.impl.sender.VelocityConsole;
import io.github.theodoremeyer.simplevoicegeyser.velocity.impl.sender.VelocityPlayer;
import net.kyori.adventure.text.Component;

import java.util.Collections;
import java.util.List;

public final class VelocityCommand implements RawCommand {

    private final ProxyPasswordStore passwordStore;

    public VelocityCommand(ProxyPasswordStore passwordStore) {
        this.passwordStore = passwordStore;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments().isBlank() ? new String[0] : invocation.arguments().split(" ", -1);

        if (source instanceof Player player) {
            VelocityPlayer svgPlayer = new VelocityPlayer(player);
            onCommand(svgPlayer, args);
        } else if (source instanceof ConsoleCommandSource console) {
            onCommand(new VelocityConsole(console), args);
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments().isBlank() ? new String[0] : invocation.arguments().split(" ", -1);
        if (args.length <= 1) {
            String input = args.length == 0 ? "" : args[0].toLowerCase();
            return List.of("help", "pswd").stream()
                    .filter(s -> s.startsWith(input))
                    .toList();
        }
        return Collections.emptyList();
    }

    private void onCommand(VelocityPlayer player, String[] args) {
        if (args.length == 0 || args[0].isBlank() || "help".equalsIgnoreCase(args[0])) {
            player.sendMessage("/svg pswd <password>");
            return;
        }

        if (!"pswd".equalsIgnoreCase(args[0])) {
            player.sendMessage("Unknown subcommand.");
            return;
        }

        if (args.length < 2 || args[1].isBlank()) {
            player.sendMessage("Usage: /svg pswd <password>");
            return;
        }

        passwordStore.setPassword(player.getUniqueId(), player.getName(), args[1]);
        player.sendMessage("Password set successfully.");
    }

    private void onCommand(VelocityConsole console, String[] args) {
        if (args.length == 0 || args[0].isBlank() || "help".equalsIgnoreCase(args[0])) {
            console.sendMessage("Use /svg pswd <password> from a player account.");
            return;
        }

        console.sendMessage("This command can only be used by players.");
    }
}
