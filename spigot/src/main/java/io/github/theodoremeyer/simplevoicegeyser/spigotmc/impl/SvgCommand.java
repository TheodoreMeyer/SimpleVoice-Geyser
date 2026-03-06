package io.github.theodoremeyer.simplevoicegeyser.spigotmc.impl;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.spigotmc.impl.sender.BukkitConsole;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class SvgCommand implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {

        if (commandSender instanceof Player p) {
            SvgPlayer player = SvgCore.getPlayerManager().getPlayer(p.getUniqueId());

            return SvgCore.getCommand().onCommand(player, strings);
        } else if (commandSender instanceof ConsoleCommandSender) {
            return SvgCore.getCommand().onCommand(new BukkitConsole(), strings);
        } else {
            return false;
        }
    }
}
