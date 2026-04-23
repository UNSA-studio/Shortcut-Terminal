package unsa.st.com.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import unsa.st.com.ShortcutTerminal;
import unsa.st.com.core.CoreCommandExecutor;
import unsa.st.com.network.TriggerSyncPayload;
import unsa.st.com.pkg.PkgManager;

public class ModCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("ST")
                .then(Commands.literal("run")
                    .then(Commands.literal("strongloading")
                        .then(Commands.argument("distance", StringArgumentType.word())
                            .executes(ctx -> {
                                String distance = StringArgumentType.getString(ctx, "distance");
                                ServerPlayer player = ctx.getSource().getPlayer();
                                return runCoreCommand(ctx.getSource(), player, "run", "strongloading", distance);
                            })
                        )
                    )
                    .then(Commands.literal("macro")
                        .then(Commands.argument("action", StringArgumentType.word())
                            .then(Commands.argument("interval_ms", StringArgumentType.word())
                                .executes(ctx -> {
                                    String action = StringArgumentType.getString(ctx, "action");
                                    String interval = StringArgumentType.getString(ctx, "interval_ms");
                                    ServerPlayer player = ctx.getSource().getPlayer();
                                    return runCoreCommand(ctx.getSource(), player, "run", "macro", action, interval);
                                })
                            )
                        )
                    )
                    .then(Commands.literal("synchrony")
                        .then(Commands.literal("-local")
                            .executes(ctx -> {
                                ServerPlayer player = ctx.getSource().getPlayer();
                                if (player != null) {
                                    PacketDistributor.sendToPlayer(player, new TriggerSyncPayload(true));
                                    ctx.getSource().sendSuccess(() -> Component.literal("§aTriggering local → server sync..."), false);
                                }
                                return 1;
                            })
                        )
                        .then(Commands.literal("-server")
                            .executes(ctx -> {
                                ServerPlayer player = ctx.getSource().getPlayer();
                                if (player != null) {
                                    PacketDistributor.sendToPlayer(player, new TriggerSyncPayload(false));
                                    ctx.getSource().sendSuccess(() -> Component.literal("§aTriggering server → local sync..."), false);
                                }
                                return 1;
                            })
                        )
                    )
                    .then(Commands.literal("spoof")
                        .then(Commands.argument("action", StringArgumentType.word())
                            .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("params", StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        String action = StringArgumentType.getString(ctx, "action");
                                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                        String paramsStr = StringArgumentType.getString(ctx, "params");
                                        String[] params = paramsStr.split(" ");
                                        CoreCommandExecutor executor = new CoreCommandExecutor(false);
                                        executor.setPlayer(target);
                                        String result = executor.execute("run", buildSpoofArgs(action, target.getName().getString(), params));
                                        ctx.getSource().sendSuccess(() -> Component.literal(result), false);
                                        return 1;
                                    })
                                )
                            )
                        )
                    )
                    .then(Commands.literal("dummymodule")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayer();
                            return runCoreCommand(ctx.getSource(), player, "run", "dummymodule");
                        })
                    )
                )
                .then(Commands.literal("pkg")
                    .then(Commands.literal("update")
                        .executes(ctx -> {
                            String result = PkgManager.updateIndex(false, true);
                            ctx.getSource().sendSuccess(() -> Component.literal(result), false);
                            return 1;
                        })
                    )
                    .then(Commands.literal("install")
                        .then(Commands.argument("package", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                                PkgManager.listAvailable().forEach(builder::suggest);
                                return builder.buildFuture();
                            })
                            .executes(ctx -> {
                                String pkg = StringArgumentType.getString(ctx, "package");
                                String result = PkgManager.install(pkg, false);
                                ctx.getSource().sendSuccess(() -> Component.literal(result), false);
                                return 1;
                            })
                        )
                    )
                    .then(Commands.literal("search")
                        .then(Commands.argument("keyword", StringArgumentType.word())
                            .executes(ctx -> {
                                String keyword = StringArgumentType.getString(ctx, "keyword");
                                java.util.List<String> results = PkgManager.search(keyword);
                                String output = results.isEmpty() ? "No packages found." : String.join("\n", results);
                                ctx.getSource().sendSuccess(() -> Component.literal(output), false);
                                return 1;
                            })
                        )
                    )
                    .then(Commands.literal("list")
                        .executes(ctx -> {
                            java.util.List<String> installed = PkgManager.listInstalled(false);
                            String output = installed.isEmpty() ? "No packages installed." : String.join("\n", installed);
                            ctx.getSource().sendSuccess(() -> Component.literal(output), false);
                            return 1;
                        })
                    )
                    .then(Commands.literal("remove")
                        .then(Commands.argument("package", StringArgumentType.word())
                            .executes(ctx -> {
                                String pkg = StringArgumentType.getString(ctx, "package");
                                String result = PkgManager.remove(pkg, false);
                                ctx.getSource().sendSuccess(() -> Component.literal(result), false);
                                return 1;
                            })
                        )
                    )
                )
        );
        ShortcutTerminal.LOGGER.info("Shortcut Terminal commands registered");
    }

    // 通用调用 CoreCommandExecutor
    private static int runCoreCommand(CommandSourceStack source, ServerPlayer player, String module, String... args) {
        if (player == null) {
            source.sendFailure(Component.literal("This command can only be run by a player."));
            return 0;
        }
        CoreCommandExecutor executor = new CoreCommandExecutor(false);
        executor.setPlayer(player);
        String[] fullArgs = new String[args.length + 1];
        fullArgs[0] = module;
        System.arraycopy(args, 0, fullArgs, 1, args.length);
        String result = executor.execute("run", fullArgs);
        source.sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    private static String[] buildSpoofArgs(String action, String playerName, String[] params) {
        String[] args = new String[params.length + 2];
        args[0] = "spoof";
        args[1] = action;
        System.arraycopy(params, 0, args, 2, params.length);
        return args;
    }
}