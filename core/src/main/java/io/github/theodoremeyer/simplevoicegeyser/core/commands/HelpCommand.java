package io.github.theodoremeyer.simplevoicegeyser.core.commands;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgColor;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.Sender;

public final class HelpCommand implements SubCommand<Void> {

    @Override
    public String name() {
        return "help";
    }

    @Override
    public void execute(Sender sender, Void args) {
        sender.sendMessage(SvgCore.getPrefix() + SvgColor.AQUA + " " + SvgColor.BOLD + "Commands");
        sender.sendMessage(SvgColor.GRAY + "----------------------------------");

        sender.sendMessage(SvgColor.YELLOW + "/svg pswd <password> "
                + SvgColor.GRAY + "- Set your voice chat password");

        sender.sendMessage(SvgColor.YELLOW + "/svg cgroup -name <name> [-t type] [-p password] [-ps] "
                + SvgColor.GRAY + "- Create a voice chat group");

        sender.sendMessage(SvgColor.YELLOW + "/svg jgroup <name> [optional: password] "
                + SvgColor.GRAY + "- Join a voice chat group");

        sender.sendMessage(SvgColor.YELLOW + "/svg lgroup "
                + SvgColor.GRAY + "- Leave your current group");

        sender.sendMessage(SvgColor.YELLOW + "/svg help "
                + SvgColor.GRAY + "- Show this help menu");
    }
}