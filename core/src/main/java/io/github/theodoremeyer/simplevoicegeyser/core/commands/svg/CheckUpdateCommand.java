package io.github.theodoremeyer.simplevoicegeyser.core.commands.svg;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgColor;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.Sender;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.commands.CommandArgs;
import io.github.theodoremeyer.simplevoicegeyser.core.commands.SubCommand;
import io.github.theodoremeyer.simplevoicegeyser.core.update.UpdateChecker;

/**
 * /svg cgroup command
 */
public final class CheckUpdateCommand implements SubCommand {

    private final SvgCore core;

    /**
     * Create the sub-Command
     * @param core SvgCore
     */
    public CheckUpdateCommand(SvgCore core) {

        this.core = core;
    }

    /**
     * Name of command
     * @return cgroup
     */
    @Override
    public String name() {
        return "checkUpdate";
    }

    /**
     * Execute the sub command
     * @param args args passed by executor
     * @return success
     */
    @Override
    public boolean execute(CommandArgs args) {
        Sender sender = args.getSender();

        if (sender instanceof SvgPlayer player) {
            if (!player.hasPermission("svg.admin.checkUpdate")) {
                sender.sendMessage(SvgCore.getPrefix() + SvgColor.RED +
                        "You do not have permission to check for updates.");
                return true;
            }
        }
        UpdateChecker checker = new UpdateChecker(SvgCore.VERSION, core.platform);
        checker.check();

        // TODO: Make UpdateChecker send update message to sender
        sender.sendMessage(SvgCore.getPrefix() + SvgColor.GREEN +
                "Checking for updates... This may take a few seconds.");
        sender.sendMessage(SvgCore.getPrefix() + SvgColor.GREEN +
                "If there is an update available, it will be logged in server console.");

        return true;
    }
}