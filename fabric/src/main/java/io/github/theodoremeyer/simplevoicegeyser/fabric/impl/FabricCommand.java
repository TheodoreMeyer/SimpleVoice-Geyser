package io.github.theodoremeyer.simplevoicegeyser.fabric.impl;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.fabric.impl.sender.FabricConsole;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.literal;

public class FabricCommand {

    public FabricCommand() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                register(dispatcher)
        );
    }

    private void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                literal("svg")
                        .then(literal("pswd").executes(this::execute))
                        .then(literal("lgroup").executes(this::execute))
                        .then(literal("jgroup").executes(this::execute))
                        .then(literal("help").executes(this::execute))
                        .then(literal("cgroup").executes(this::execute))
                        .executes(this::execute)
        );
    }

    private int execute(CommandContext<CommandSourceStack> ctx) {

        CommandSourceStack source = ctx.getSource();
        String[] args = parseArgs(ctx);

        // PLAYER
        ServerPlayer playerEntity = null;

        try {
            playerEntity = source.getPlayerOrException();
        } catch (Exception ignored) {}

        if (playerEntity != null) {

            SvgPlayer player =
                    SvgCore.getPlayerManager().getPlayer(playerEntity.getUUID());

            if (player == null) return 1;

            SvgCore.getCommand().onCommand(player, args);
            return 1;
        }

        // CONSOLE
        SvgCore.getCommand().onCommand(new FabricConsole(), args);

        return 1;
    }

    private String[] parseArgs(CommandContext<CommandSourceStack> ctx) {
        String raw = ctx.getInput(); // "/svg pswd test"

        String[] split = raw.split(" ");

        if (split.length <= 1) {
            return new String[0];
        }

        String[] args = new String[split.length - 1];

        System.arraycopy(split, 1, args, 0, args.length);

        return args;
    }
}