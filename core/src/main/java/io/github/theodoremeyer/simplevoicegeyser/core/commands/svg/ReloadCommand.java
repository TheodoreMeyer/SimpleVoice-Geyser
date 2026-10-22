package io.github.theodoremeyer.simplevoicegeyser.core.commands.svg;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.DataType;
import io.github.theodoremeyer.simplevoicegeyser.core.api.data.SvgFile;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.Sender;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgConsole;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.commands.CommandArgs;
import io.github.theodoremeyer.simplevoicegeyser.core.commands.SubCommand;

/**
 * The Command for reloading the server
 */
public class ReloadCommand implements SubCommand {

    /**
     * Create the reload command
     */
    public ReloadCommand() {}

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
        SvgFile configFile = SvgCore.getPlatform().getFile(DataType.CONFIG);

        configFile.reload();
        SvgCore.getConfig().applyDefaults();

        // Apply debug before web restart so Jetty logs use the new flag
        SvgCore.getLogger().setDebug(SvgCore.getConfig().DEBUG.get());

        boolean webRestarted = SvgCore.restartWebServer();

        sender.sendMessage(SvgCore.getPrefix() + "Reloaded SimpleVoiceGeyser Config");
        if (webRestarted) {
            sender.sendMessage(
                    "Web server restarted — bind/port/context-path/idle-timeout and web branding apply now. "
                            + "Connected web clients were disconnected and must rejoin."
            );
        } else {
            sender.sendMessage(
                    "Config reloaded, but the web server could not be restarted. Check the console and restart the Minecraft server if needed."
            );
        }
    }
}
