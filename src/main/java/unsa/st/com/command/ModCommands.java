package unsa.st.com.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import unsa.st.com.ShortcutTerminal;
import unsa.st.com.core.CoreCommandExecutor;
import unsa.st.com.dummy.PlayerMacroManager;
import unsa.st.com.network.ModNetwork;
import unsa.st.com.network.TriggerSyncPayload;
import unsa.st.com.pkg.PkgManager;

public class ModCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("st")
                .then(Commands.literal("run")
                    .then(Commands.literal("macro")
                        .then(Commands.argument("action", StringArgumentType.word())
                            .then(Commands.argument("interval_ms", StringArgumentType.word())
                                .executes(ctx -> {
                                    String action = StringArgumentType.getString(ctx, "action");
                                    String interval = StringArgumentType.getString(ctx, "interval_ms");
                                    return executeMacro(ctx.getSource(), action, interval);
                                })
                            )
                        )
                    )
                    .then(Commands.literal("synchrony")
                        .then(Commands.literal("-local")
                            .executes(ctx -> {
                                ServerPlayer player = ctx.getSource().getPlayer();
                                if (player != null) {
                                    PacketDistributor.sendToPlayer(new TriggerSyncPayload(true), player);
                                    ctx.getSource().sendSuccess(() -> Component.literal("§aTriggering local → server sync..."), false);
                                }
                                return 1;
                            })
                        )
                        .then(Commands.literal("-server")
                            .executes(ctx -> {
                                ServerPlayer player = ctx.getSource().getPlayer();
                                if (player != null) {
                                    PacketDistributor.sendToPlayer(new TriggerSyncPayload(false), player);
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
                                        return executeSpoof(ctx.getSource(), action, target, params);
                                    })
                                )
                            )
                        )
                    )
                )
                .then(Commands.literal("pkg")
                    .then(Commands.literal("update")
                        .executes(ctx -> {
                            String result = PkgManager.updateIndex();
                            ctx.getSource().sendSuccess(() -> Component.literal(result), false);
                            return 1;
                        })
                    )
                    .then(Commands.literal("install")
                        .then(Commands.argument("package", StringArgumentType.word())
                            .executes(ctx -> {
                                String pkg = StringArgumentType.getString(ctx, "package");
                                String result = PkgManager.install(pkg, false);
                                ctx.getSource().sendSuccess(() -> Component.literal(result), false);
                                return 1;
                            })
                        )
                    )
                )
        );
        ShortcutTerminal.LOGGER.info("Shortcut Terminal commands registered");
    }

    private static int executeMacro(CommandSourceStack source, String action, String intervalStr) {
        try {
            long interval = Long.parseLong(intervalStr);
            PlayerMacroManager.startMacro(action, interval);  // 修正为正确的参数列表 (String, long)
            source.sendSuccess(() -> Component.literal("Macro started: " + action + " every " + interval + "ms"), true);
        } catch (NumberFormatException e) {
            source.sendFailure(Component.literal("Invalid interval"));
        }
        return 1;
    }

    private static int executeSpoof(CommandSourceStack source, String action, ServerPlayer target, String[] params) {
        CoreCommandExecutor executor = new CoreCommandExecutor(false);
        executor.setPlayer(target);
        String result = executor.execute("run", buildSpoofArgs(action, target.getName().getString(), params));
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

    public static void executePkgInstall(CommandSourceStack source, String pkg) {
        String result = PkgManager.install(pkg, false);
        source.sendSuccess(() -> Component.literal(result), false);
    }
}