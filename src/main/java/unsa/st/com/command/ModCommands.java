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
import unsa.st.com.dummy.PlayerMacroManager;
import unsa.st.com.network.TriggerSyncPayload;
import unsa.st.com.pkg.PkgManager;

public class ModCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("ST");

        // ========== 1. 所有原有内置命令（完整恢复） ==========
        String[] builtinCommands = {
            "ls", "mkdir", "touch", "rm", "cat", "echo", "cd", "pwd", "cp", "mv",
            "head", "tail", "wc", "grep", "sort", "uniq", "whoami", "uname", "df",
            "free", "ps", "du", "ping", "curl", "wget", "clear", "date", "which",
            "chmod", "sh", "refresh", "stop", "user", "help"
        };

        for (String cmd : builtinCommands) {
            root.then(Commands.literal(cmd)
                .then(Commands.argument("args", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayer();
                        if (player == null) return 0;
                        String argsStr = StringArgumentType.getString(ctx, "args");
                        String[] args = argsStr.isEmpty() ? new String[0] : argsStr.split(" ");
                        CoreCommandExecutor executor = new CoreCommandExecutor(false);
                        executor.setPlayer(player);
                        String result = executor.execute(cmd, args);
                        ctx.getSource().sendSuccess(() -> Component.literal(result), false);
                        return 1;
                    })
                )
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayer();
                    if (player == null) return 0;
                    CoreCommandExecutor executor = new CoreCommandExecutor(false);
                    executor.setPlayer(player);
                    String result = executor.execute(cmd, new String[0]);
                    ctx.getSource().sendSuccess(() -> Component.literal(result), false);
                    return 1;
                })
            );
        }

        // ========== 2. run 系列命令 ==========
        root.then(Commands.literal("run")
            .then(Commands.literal("strongloading")
                .then(Commands.argument("distance", StringArgumentType.word())
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayer();
                        if (player == null) return 0;
                        String distance = StringArgumentType.getString(ctx, "distance");
                        CoreCommandExecutor executor = new CoreCommandExecutor(false);
                        executor.setPlayer(player);
                        String result = executor.execute("run", new String[]{"strongloading", distance});
                        ctx.getSource().sendSuccess(() -> Component.literal(result), false);
                        return 1;
                    })
                )
            )
            .then(Commands.literal("macro")
                .then(Commands.argument("action", StringArgumentType.word())
                    .then(Commands.argument("interval_ms", StringArgumentType.word())
                        .executes(ctx -> {
                            String action = StringArgumentType.getString(ctx, "action");
                            String interval = StringArgumentType.getString(ctx, "interval_ms");
                            try {
                                long ms = Long.parseLong(interval);
                                PlayerMacroManager.startMacro(action, ms);
                                ctx.getSource().sendSuccess(() -> Component.literal("Macro started: " + action + " every " + ms + "ms"), true);
                            } catch (NumberFormatException e) {
                                ctx.getSource().sendFailure(Component.literal("Invalid interval"));
                            }
                            return 1;
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
                                String[] spoofArgs = new String[params.length + 2];
                                spoofArgs[0] = "spoof";
                                spoofArgs[1] = action;
                                System.arraycopy(params, 0, spoofArgs, 2, params.length);
                                String result = executor.execute("run", spoofArgs);
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
                    if (player == null) return 0;
                    CoreCommandExecutor executor = new CoreCommandExecutor(false);
                    executor.setPlayer(player);
                    String result = executor.execute("run", new String[]{"dummymodule"});
                    ctx.getSource().sendSuccess(() -> Component.literal(result), false);
                    return 1;
                })
            )
        );

        // ========== 3. pkg 系列命令 ==========
        root.then(Commands.literal("pkg")
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
        );

        dispatcher.register(root);
        ShortcutTerminal.LOGGER.info("Shortcut Terminal commands registered");
    }
}