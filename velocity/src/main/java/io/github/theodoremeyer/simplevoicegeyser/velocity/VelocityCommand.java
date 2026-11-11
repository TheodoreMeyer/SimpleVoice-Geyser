package io.github.theodoremeyer.simplevoicegeyser.velocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.proxy.Player;
import io.github.theodoremeyer.simplevoicegeyser.velocity.geyser.GeyserForms;
import io.github.theodoremeyer.simplevoicegeyser.velocity.impl.data.ProxyPasswordStore;
import io.github.theodoremeyer.simplevoicegeyser.velocity.impl.sender.VelocityPlayer;

import java.util.Arrays;

/**
 * Intercepts /svg and /svg pswd <password> on Velocity.
 * <br>
 * A bare /svg or /svg pswd opens the optional proxy Geyser form for Bedrock players,
 * so proxy users never update the backend password store. Java players and other
 * subcommands are forwarded to the backend server.
 * <br>
 * TODO: implement /svg reload to reload proxy config too.
 */
public final class VelocityCommand {

    private static final String ROOT_COMMAND = "svg";
    private static final String PASSWORD_SUBCOMMAND = "pswd";

    private final ProxyPasswordStore passwordStore;
    private final GeyserForms bedrockPasswordForms;

    public VelocityCommand(ProxyPasswordStore passwordStore, GeyserForms bedrockPasswordForms) {
        this.passwordStore = passwordStore;
        this.bedrockPasswordForms = bedrockPasswordForms;
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
        if (args.length == 0 || !ROOT_COMMAND.equalsIgnoreCase(args[0])) {
            return;
        }

        // Handle bare /svg: open the form for Bedrock players, forward to server for Java players.
        if (args.length == 1) {
            if (bedrockPasswordForms != null && bedrockPasswordForms.openBedrockPasswordForm(player)) {
                event.setResult(CommandExecuteEvent.CommandResult.denied());
            } else {
                event.setResult(CommandExecuteEvent.CommandResult.forwardToServer());
            }
            return;
        }

        // Forward any other non-password subcommands (e.g., /svg reload) to the backend.
        if (!PASSWORD_SUBCOMMAND.equalsIgnoreCase(args[1])) {
            event.setResult(CommandExecuteEvent.CommandResult.forwardToServer());
            return;
        }

        // Password commands (/svg pswd ...) belong exclusively to the proxy authentication store.
        event.setResult(CommandExecuteEvent.CommandResult.denied());

        if (args.length == 2
                && bedrockPasswordForms != null
                && bedrockPasswordForms.openBedrockPasswordForm(player)) {
            return;
        }

        handlePasswordCommand(new VelocityPlayer(player), args);
    }

    private void handlePasswordCommand(VelocityPlayer player, String[] args) {
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
