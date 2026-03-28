package io.github.theodoremeyer.simplevoicegeyser.fabric.impl;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.commands.CommandArgs;
import io.github.theodoremeyer.simplevoicegeyser.core.commands.CommandFlagParser;
import io.github.theodoremeyer.simplevoicegeyser.fabric.impl.sender.FabricConsole;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

import static net.minecraft.commands.Commands.argument;
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

                        // /svg
                        .executes(ctx ->  execute(ctx, ""))

                        // /svg help
                        .then(literal("help")
                                .executes(ctx -> execute(ctx, "help"))
                        )

                        // /svg lgroup
                        .then(literal("lgroup")
                                .executes(ctx -> execute(ctx, "lgroup"))
                        )

                        // /svg pswd <password>
                        .then(literal("pswd")
                                .then(argument("password", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String password = StringArgumentType.getString(ctx, "password");
                                            return execute(ctx, "pswd", args -> args.put("password", password));
                                        })
                                )
                        )

                        // /svg jgroup <name> [password]
                        .then(literal("jgroup")
                                .then(argument("name", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            return execute(ctx, "jgroup", args -> args.put("name", name));
                                        })
                                        .then(argument("password", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    String name = StringArgumentType.getString(ctx, "name");
                                                    String password = StringArgumentType.getString(ctx, "password");

                                                    return execute(ctx, "jgroup", args -> {
                                                        args.put("name", name);
                                                        args.put("password", password);
                                                    });
                                                })
                                        )
                                )
                        )

                        // /svg cgroup (basic example, expand later)
                        .then(literal("cgroup")
                                // required name
                                .then(argument("name", StringArgumentType.word())

                                        // no flags case
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");

                                            return execute(ctx, "cgroup", args -> {
                                                args.put("name", name);
                                                args.put("type", "open");
                                                args.put("password", "");
                                                args.put("persistent", false);
                                            });
                                        })

                                        // flags case
                                        .then(argument("flags", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    String name = StringArgumentType.getString(ctx, "name");
                                                    String input = StringArgumentType.getString(ctx, "flags");

                                                    Map<String, Object> parsed = CommandFlagParser.parse(CommandFlagParser.CGROUP_FLAGS, input.split("\\s+"), 0);

                                                    return execute(ctx, "cgroup", args -> {
                                                        args.put("name", name); // authoritative source
                                                        args.putAll(parsed);
                                                    });
                                                })
                                        )
                                )
                        )
        );
    }

    /**
     * Handles root: /svg
     */
    private int executeRoot(CommandContext<CommandSourceStack> ctx) {
        return execute(ctx, "");
    }

    /**
     * Core executor (no args modifier)
     */
    private int execute(CommandContext<CommandSourceStack> ctx, String sub) {
        return execute(ctx, sub, args -> {});
    }

    /**
     * Core executor with args builder
     */
    private int execute(CommandContext<CommandSourceStack> ctx,
                        String sub,
                        java.util.function.Consumer<CommandArgs> argBuilder) {

        CommandSourceStack source = ctx.getSource();

        // PLAYER
        ServerPlayer playerEntity = null;
        try {
            playerEntity = source.getPlayerOrException();
        } catch (Exception ignored) {}

        CommandArgs args;

        if (playerEntity != null) {
            SvgPlayer player = SvgCore.getPlayerManager().getPlayer(playerEntity.getUUID());
            if (player == null) return 1;

            args = new CommandArgs(sub, player);
        } else {
            args = new CommandArgs(sub, new FabricConsole());
        }

        // Apply arguments
        argBuilder.accept(args);

        // Execute core command system
        SvgCore.getCommand().execute(args);

        return 1;
    }
}