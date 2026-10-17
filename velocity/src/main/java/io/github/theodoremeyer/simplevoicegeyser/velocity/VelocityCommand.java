
package io.github.theodoremeyer.simplevoicegeyser.velocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.proxy.Player;
import io.github.theodoremeyer.simplevoicegeyser.velocity.impl.data.ProxyPasswordStore;
import io.github.theodoremeyer.simplevoicegeyser.velocity.impl.sender.VelocityPlayer;

import java.util.Arrays;

/**
 * Intercepts /svg pswd <password> on Velocity.
 * <br>
 * A bare /svg pswd is forwarded to the backend so its Geyser
 * integration can open the Bedrock password form.
 * Other /svg commands are also forwarded to the backend.
 * <br>
 * TODO: implement /svg reload to reload proxy config too.
 */
public final class VelocityCommand {

    private static final String ROOT_COMMAND = "svg";
    private static final String PASSWORD_SUBCOMMAND = "pswd";

    private final ProxyPasswordStore passwordStore;

    public VelocityCommand(ProxyPasswordStore passwordStore) {
        this.passwordStore = passwordStore;
    }

    @Subscribe
    public void onCommandExecute(CommandExecuteEvent event) {

        // Only intercept player commands.
        if (!(event.getCommandSource() instanceof Player player)) {
            return;
        }

        String command = event.getCommand();

        if (command == null || command.isBlank()) {
            return;
        }

        String[] args = command.trim().split("\\s+");

        // Ignore commands other than /svg.
        if (args.length == 0
                || !ROOT_COMMAND.equalsIgnoreCase(args[0])) {
            return;
        }

        // Forward /svg itself, all non-password subcommands,
        // and bare /svg pswd to the backend to allow geyser's cumulus form if needed.
        if (args.length < 3
                || !PASSWORD_SUBCOMMAND.equalsIgnoreCase(args[1])) {
            event.setResult(
                    CommandExecuteEvent.CommandResult.forwardToServer()
            );
            return;
        }

        // Only /svg pswd <password> is handled on Velocity.
        event.setResult(
                CommandExecuteEvent.CommandResult.denied()
        );

        handlePasswordCommand(new VelocityPlayer(player), args);
    }

    private void handlePasswordCommand(
            VelocityPlayer player,
            String[] args
    ) {
        String password = String.join(
                " ",
                Arrays.copyOfRange(args, 2, args.length)
        );

        if (password.isBlank()) {
            player.sendMessage("Usage: /svg pswd <password>");
            player.sendMessage("Password must be between 8 and 32 characters.");
            return;
        }

        if (!isPasswordLengthValid(password)) {
            player.sendMessage("Password must be between 8 and 32 characters.");
            return;
        }

        passwordStore.setPassword(
                player.getUniqueId(),
                player.getName(),
                password
        );

        player.sendMessage("Password set successfully.");
    }

    public boolean isPasswordLengthValid(String password) {
        return password != null && password.length() >= 8 && password.length() <= 32;
    }
}
