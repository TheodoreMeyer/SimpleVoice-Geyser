package io.github.theodoremeyer.simplevoicegeyser.core.commands;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgColor;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.Sender;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;

public final class PswdCommand implements SubCommand<String> {

    @Override
    public String name() {
        return "pswd";
    }

    @Override
    public void execute(Sender sender, String password) {
        if (!(sender instanceof SvgPlayer player)) {
            sender.sendMessage(SvgCore.getPrefix() + SvgColor.RED + "Only players can set their password.");
            return;
        }

        SvgCore.getPasswordManager().setPassword(player, password);
    }
}