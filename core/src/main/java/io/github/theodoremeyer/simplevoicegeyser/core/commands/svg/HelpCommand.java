package io.github.theodoremeyer.simplevoicegeyser.core.commands.svg;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgColor;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.Sender;
import io.github.theodoremeyer.simplevoicegeyser.core.commands.CommandArgs;
import io.github.theodoremeyer.simplevoicegeyser.core.commands.SubCommand;

public final class HelpCommand implements SubCommand {

    @Override
    public String name() {
        return "help";
    }

    @Override
    public boolean execute(CommandArgs args) {
        Sender sender = args.getSender();
        sender.sendMessage(SvgCore.getPrefix() + SvgColor.AQUA + " " + SvgColor.BOLD + "Commands");
        sender.sendMessage(SvgColor.GRAY + "----------------------------------");

        sender.sendMessage(SvgColor.YELLOW + "/svg pswd <password> "
                + SvgColor.GRAY + "- Set your voice chat password");

        sender.sendMessage(SvgColor.YELLOW + "/svg cgroup -name <name> [-t type] [-p password] [-ps] "
                + SvgColor.GRAY + "- Create a voice chat svg");

        sender.sendMessage(SvgColor.YELLOW + "/svg jgroup <name> [password] "
                + SvgColor.GRAY + "- Join a voice chat svg");

        sender.sendMessage(SvgColor.YELLOW + "/svg lgroup "
                + SvgColor.GRAY + "- Leave your current svg");

        sender.sendMessage(SvgColor.YELLOW + "/svg help "
                + SvgColor.GRAY + "- Show this help menu");

        return true;
    }
}