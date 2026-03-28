package io.github.theodoremeyer.simplevoicegeyser.core.commands.svg;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgColor;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.Sender;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.commands.CommandArgs;
import io.github.theodoremeyer.simplevoicegeyser.core.commands.SubCommand;

public final class PswdCommand implements SubCommand {

    @Override
    public String name() {
        return "pswd";
    }

    @Override
    public boolean execute(CommandArgs args) {
        Sender sender = args.getSender();
        if (!(sender instanceof SvgPlayer player)) {
            sender.sendMessage(SvgCore.getPrefix() + SvgColor.RED +
                    "Only players can set their password.");
            return true;
        }

        String password = args.get("password");

        if (password == null || password.isBlank()) {
            sender.sendMessage(SvgCore.getPrefix() + SvgColor.RED +
                    "Usage: /svg pswd <password>");
            return true;
        }

        SvgCore.getPasswordManager().setPassword(player, password);
        return true;
    }
}