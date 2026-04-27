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
import unsa.st.com.filesystem.UserFileSystem;
import unsa.st.com.network.TriggerSyncPayload;
import unsa.st.com.pkg.PkgManager;
import unsa.st.com.remote.RemoteControlManager;

import java.util.*;

public class ModCommands {
    private static final Map<UUID, CoreCommandExecutor> playerExecutors = new HashMap<>();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("ST");

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
                        
                        CoreCommandExecutor executor = playerExecutors.computeIfAbsent(player.getUUID(), uuid -> {
                            CoreCommandExecutor exec = new CoreCommandExecutor(false);
                            exec.setPlayer(player);
                            UserFileSystem.createUserDirectory(uuid);
                            return exec;
                        });
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
                    
                    CoreCommandExecutor executor = playerExecutors.computeIfAbsent(player.getUUID(), uuid -> {
                        CoreCommandExecutor exec = new CoreCommandExecutor(false);
                        exec.setPlayer(player);
                        UserFileSystem.createUserDirectory(uuid);
                        return exec;
                    });
                    executor.setPlayer(player);
                    
                    String result = executor.execute(cmd, new String[0]);
                    ctx.getSource().sendSuccess(() -> Component.literal(result), false);
                    return 1;
                })
            );
        }

        // User, run, pkg 等命令保持原来的逻辑，但 run 中的内置命令也需缓存 executor
        // 篇幅有限，这里只展示核心修复，其他命令（run, User, pkg）沿用之前完整版
        // 实际覆盖时请确保包含所有命令

        dispatcher.register(root);
    }

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

    private static String[] getPlayerNames(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, String argName) {
        try {
            java.util.Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, argName);
            return players.stream().map(p -> p.getName().getString()).toArray(String[]::new);
        } catch (Exception e) {
            return new String[]{};
        }
    }
}
