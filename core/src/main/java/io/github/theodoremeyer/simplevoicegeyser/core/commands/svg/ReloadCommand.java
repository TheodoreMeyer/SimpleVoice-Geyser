package io.github.theodoremeyer.simplevoicegeyser.core.commands.svg;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.DataType;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.Sender;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgConsole;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.commands.CommandArgs;
import io.github.theodoremeyer.simplevoicegeyser.core.commands.SubCommand;

public class ReloadCommand implements SubCommand {
    @Override
    public String name() {
        return "reload";
    }

    @Override
    public boolean execute(CommandArgs args) {
        if (args.getSender() instanceof SvgPlayer p) {
            if (!p.hasPermission("svg.admin")) {
                p.sendMessage("You don't have permission to use this command.");
                return true;
            }
            reload(p);
        }
        if (args.getSender() instanceof SvgConsole console) {
            reload(console);
        }
        return true;
    }

    private void reload(Sender sender) {
        SvgCore.getPlatform().getFile(DataType.CONFIG).reload();
        SvgCore.getConfig().applyDefaults();

        sender.sendMessage(SvgCore.getPrefix() + "Reloaded SimpleVoiceGeyser Config");
        sender.sendMessage("The reload will not update all config values used, several (like server port) require" +
                " the server to be restarted to take effect.");

    }
}
