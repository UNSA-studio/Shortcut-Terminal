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

        // ========== 1. 所有原有内置命令 ==========
        String[] builtinCommands = {
            "ls", "mkdir", "touch", "rm", "cat", "echo", "cd", "pwd", "cp", "mv",
            "head", "tail", "wc", "grep", "sort", "uniq", "whoami", "uname", "df",
            "free", "ps", "du", "ping", "curl", "wget", "clear", "date", "which",
            "chmod", "sh", "refresh", "stop", "help"
        };

        for (String cmd : builtinCommands) {
            root.then(Commands.literal(cmd)
                .then(Commands.argument("args", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayer();
                        if (player == null) return 0;
                        CoreCommandExecutor executor = new CoreCommandExecutor(false);
                        executor.setPlayer(player);
                        String argsStr = StringArgumentType.getString(ctx, "args");
                        String[] args = argsStr.isEmpty() ? new String[0] : argsStr.split(" ");
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

        // ========== 2. User 管理命令 ==========
        root.then(Commands.literal("User")
            .then(Commands.argument("player", EntityArgument.players())
                .then(Commands.literal("switchingmode")
                    .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            builder.suggest("creative");
                            builder.suggest("survival");
                            builder.suggest("spectator");
                            builder.suggest("1");
                            builder.suggest("2");
                            builder.suggest("3");
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            ServerPlayer admin = ctx.getSource().getPlayer();
                            if (admin == null) return 0;
                            String mode = StringArgumentType.getString(ctx, "mode");
                            String[] targets = getPlayerNames(ctx, "player");
                            StringBuilder results = new StringBuilder();
                            for (String target : targets) {
                                CoreCommandExecutor executor = new CoreCommandExecutor(false);
                                executor.setPlayer(admin);
                                String result = executor.execute("User", new String[]{target, "switchingmode", mode});
                                results.append(result).append("\n");
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal(results.toString().trim()), false);
                            return 1;
                        })
                    )
                )
                .then(Commands.literal("transport-Online")
                    .then(Commands.argument("coordinates", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            ServerPlayer admin = ctx.getSource().getPlayer();
                            if (admin == null) return 0;
                            String coords = StringArgumentType.getString(ctx, "coordinates");
                            String[] targets = getPlayerNames(ctx, "player");
                            StringBuilder results = new StringBuilder();
                            for (String target : targets) {
                                CoreCommandExecutor executor = new CoreCommandExecutor(false);
                                executor.setPlayer(admin);
                                String result = executor.execute("User", new String[]{target, "transport-Online", coords});
                                results.append(result).append("\n");
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal(results.toString().trim()), false);
                            return 1;
                        })
                    )
                )
                .then(Commands.literal("transport-Offline")
                    .then(Commands.argument("coordinates", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            ServerPlayer admin = ctx.getSource().getPlayer();
                            if (admin == null) return 0;
                            String coords = StringArgumentType.getString(ctx, "coordinates");
                            String[] targets = getPlayerNames(ctx, "player");
                            StringBuilder results = new StringBuilder();
                            for (String target : targets) {
                                CoreCommandExecutor executor = new CoreCommandExecutor(false);
                                executor.setPlayer(admin);
                                String result = executor.execute("User", new String[]{target, "transport-Offline", coords});
                                results.append(result).append("\n");
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal(results.toString().trim()), false);
                            return 1;
                        })
                    )
                )
                .then(Commands.literal("ban")
                    .executes(ctx -> {
                        ServerPlayer admin = ctx.getSource().getPlayer();
                        if (admin == null) return 0;
                        String[] targets = getPlayerNames(ctx, "player");
                        StringBuilder results = new StringBuilder();
                        for (String target : targets) {
                            CoreCommandExecutor executor = new CoreCommandExecutor(false);
                            executor.setPlayer(admin);
                            String result = executor.execute("User", new String[]{target, "ban"});
                            results.append(result).append("\n");
                        }
                        ctx.getSource().sendSuccess(() -> Component.literal(results.toString().trim()), false);
                        return 1;
                    })
                )
                .then(Commands.literal("op")
                    .executes(ctx -> {
                        ServerPlayer admin = ctx.getSource().getPlayer();
                        if (admin == null) return 0;
                        String[] targets = getPlayerNames(ctx, "player");
                        StringBuilder results = new StringBuilder();
                        for (String target : targets) {
                            CoreCommandExecutor executor = new CoreCommandExecutor(false);
                            executor.setPlayer(admin);
                            String result = executor.execute("User", new String[]{target, "op"});
                            results.append(result).append("\n");
                        }
                        ctx.getSource().sendSuccess(() -> Component.literal(results.toString().trim()), false);
                        return 1;
                    })
                )
            )
        );

        // ========== 3. run 系列命令 ==========
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
                            ctx.getSource().sendSuccess(() -> Component.literal("Triggering local -> server sync..."), false);
                        }
                        return 1;
                    })
                )
                .then(Commands.literal("-server")
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayer();
                        if (player != null) {
                            PacketDistributor.sendToPlayer(player, new TriggerSyncPayload(false));
                            ctx.getSource().sendSuccess(() -> Component.literal("Triggering server -> local sync..."), false);
                        }
                        return 1;
                    })
                )
            )
            // ===== spoof 命令（修复：玩家名字在前面，操作在后面） =====
            .then(Commands.literal("spoof")
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.literal("ray")
                        .then(Commands.argument("params", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                String paramsStr = StringArgumentType.getString(ctx, "params");
                                return executeSpoofAction(ctx.getSource(), target, "ray", paramsStr);
                            })
                        )
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            return executeSpoofAction(ctx.getSource(), target, "ray", "");
                        })
                    )
                    .then(Commands.literal("creeper")
                        .then(Commands.argument("params", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                String paramsStr = StringArgumentType.getString(ctx, "params");
                                return executeSpoofAction(ctx.getSource(), target, "creeper", paramsStr);
                            })
                        )
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            return executeSpoofAction(ctx.getSource(), target, "creeper", "");
                        })
                    )
                    .then(Commands.literal("flyup")
                        .then(Commands.argument("params", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                String paramsStr = StringArgumentType.getString(ctx, "params");
                                return executeSpoofAction(ctx.getSource(), target, "flyup", paramsStr);
                            })
                        )
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            return executeSpoofAction(ctx.getSource(), target, "flyup", "");
                        })
                    )
                    .then(Commands.literal("evasiveground")
                        .then(Commands.argument("params", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                String paramsStr = StringArgumentType.getString(ctx, "params");
                                return executeSpoofAction(ctx.getSource(), target, "evasiveground", paramsStr);
                            })
                        )
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            return executeSpoofAction(ctx.getSource(), target, "evasiveground", "");
                        })
                    )
                    .then(Commands.literal("stop")
                        .then(Commands.argument("params", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                String paramsStr = StringArgumentType.getString(ctx, "params");
                                return executeSpoofAction(ctx.getSource(), target, "stop", paramsStr);
                            })
                        )
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            return executeSpoofAction(ctx.getSource(), target, "stop", "");
                        })
                    )
                    .then(Commands.literal("quickly")
                        .then(Commands.argument("params", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                String paramsStr = StringArgumentType.getString(ctx, "params");
                                return executeSpoofAction(ctx.getSource(), target, "quickly", paramsStr);
                            })
                        )
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            return executeSpoofAction(ctx.getSource(), target, "quickly", "");
                        })
                    )
                    .then(Commands.literal("tortoise")
                        .then(Commands.argument("params", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                String paramsStr = StringArgumentType.getString(ctx, "params");
                                return executeSpoofAction(ctx.getSource(), target, "tortoise", paramsStr);
                            })
                        )
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            return executeSpoofAction(ctx.getSource(), target, "tortoise", "");
                        })
                    )
                    .then(Commands.literal("blackscreen")
                        .then(Commands.argument("params", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                String paramsStr = StringArgumentType.getString(ctx, "params");
                                return executeSpoofAction(ctx.getSource(), target, "blackscreen", paramsStr);
                            })
                        )
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            return executeSpoofAction(ctx.getSource(), target, "blackscreen", "");
                        })
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

        // ========== 4. pkg 系列命令 ==========
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

    // spoof 命令执行辅助方法
    private static int executeSpoofAction(CommandSourceStack source, ServerPlayer target, String action, String paramsStr) {
        CoreCommandExecutor executor = new CoreCommandExecutor(false);
        executor.setPlayer(target);
        String[] params = paramsStr.isEmpty() ? new String[0] : paramsStr.split(" ");
        String[] spoofArgs = new String[params.length + 2];
        spoofArgs[0] = "spoof";
        spoofArgs[1] = action;
        System.arraycopy(params, 0, spoofArgs, 2, params.length);
        String result = executor.execute("run", spoofArgs);
        source.sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    // 辅助方法：从命令上下文中获取玩家名列表
    private static String[] getPlayerNames(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, String argName) {
        try {
            java.util.Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, argName);
            return players.stream().map(p -> p.getName().getString()).toArray(String[]::new);
        } catch (Exception e) {
            return new String[]{};
        }
    }
}