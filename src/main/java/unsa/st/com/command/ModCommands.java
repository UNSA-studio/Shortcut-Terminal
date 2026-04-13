package unsa.st.com.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import unsa.st.com.terminal.TerminalManager;
import unsa.st.com.terminal.TerminalSession;
import unsa.st.com.filesystem.UserFileSystem;
import unsa.st.com.util.CommandExecutor;
import unsa.st.com.plugin.BinaryPluginManager;

import java.util.List;
import java.util.Locale;

public class ModCommands {
    private static final CommandExecutor executor = new CommandExecutor();

    // 玩家名称补全建议
    private static final SuggestionProvider<CommandSourceStack> ONLINE_PLAYERS = (ctx, builder) -> {
        return SharedSuggestionProvider.suggest(ctx.getSource().getOnlinePlayerNames(), builder);
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("ST")
                .then(Commands.literal("Help").executes(ctx -> showHelp(ctx.getSource())))
                .then(Commands.literal("ls").executes(ctx -> executeLs(ctx.getSource())))
                .then(Commands.literal("mkdir").then(Commands.argument("name", StringArgumentType.string())
                    .executes(ctx -> executeMkdir(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("touch").then(Commands.argument("name", StringArgumentType.string())
                    .executes(ctx -> executeTouch(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("rm").then(Commands.argument("name", StringArgumentType.string())
                    .executes(ctx -> executeRm(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("cat").then(Commands.argument("name", StringArgumentType.string())
                    .executes(ctx -> executeCat(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("cd").then(Commands.argument("path", StringArgumentType.string())
                    .executes(ctx -> executeCd(ctx.getSource(), StringArgumentType.getString(ctx, "path")))))
                .then(Commands.literal("pwd").executes(ctx -> executePwd(ctx.getSource())))
                .then(Commands.literal("echo").then(Commands.argument("text", StringArgumentType.greedyString())
                    .executes(ctx -> executeEcho(ctx.getSource(), StringArgumentType.getString(ctx, "text")))))
                .then(Commands.literal("clear").executes(ctx -> executeClear(ctx.getSource())))
                .then(Commands.literal("whoami").executes(ctx -> executeWhoami(ctx.getSource())))
                .then(Commands.literal("refresh").then(Commands.literal("bf")
                    .executes(ctx -> executeRefresh(ctx.getSource()))))
                .then(Commands.literal("User")
                    .then(Commands.argument("player", StringArgumentType.word()).suggests(ONLINE_PLAYERS)
                        .then(Commands.argument("action", StringArgumentType.word())
                            .then(Commands.argument("params", StringArgumentType.greedyString())
                                .executes(ctx -> executeUser(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "player"),
                                    StringArgumentType.getString(ctx, "action"),
                                    StringArgumentType.getString(ctx, "params")))))))
        );
    }

    private static int showHelp(CommandSourceStack source) {
        String help = executor.execute(source.getPlayer(), "help", new String[0], "");
        source.sendSuccess(() -> Component.literal(help), false);
        return 1;
    }

    private static int executeLs(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        TerminalSession session = TerminalManager.getSession(player);
        String currentPath = session != null ? session.getCurrentPath() : "";
        String result = executor.execute(player, "ls", new String[0], currentPath);
        source.sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    private static int executeMkdir(CommandSourceStack source, String name) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        TerminalSession session = TerminalManager.getSession(player);
        String currentPath = session != null ? session.getCurrentPath() : "";
        String result = executor.execute(player, "mkdir", new String[]{name}, currentPath);
        source.sendSuccess(() -> Component.literal(result).withStyle(
            result.startsWith("Error:") ? ChatFormatting.RED : ChatFormatting.WHITE), false);
        return 1;
    }

    private static int executeTouch(CommandSourceStack source, String name) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        TerminalSession session = TerminalManager.getSession(player);
        String currentPath = session != null ? session.getCurrentPath() : "";
        String result = executor.execute(player, "touch", new String[]{name}, currentPath);
        source.sendSuccess(() -> Component.literal(result).withStyle(
            result.startsWith("Error:") ? ChatFormatting.RED : ChatFormatting.WHITE), false);
        return 1;
    }

    private static int executeRm(CommandSourceStack source, String name) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        TerminalSession session = TerminalManager.getSession(player);
        String currentPath = session != null ? session.getCurrentPath() : "";
        String result = executor.execute(player, "rm", new String[]{name}, currentPath);
        source.sendSuccess(() -> Component.literal(result).withStyle(
            result.startsWith("Error:") ? ChatFormatting.RED : ChatFormatting.WHITE), false);
        return 1;
    }

    private static int executeCat(CommandSourceStack source, String name) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        TerminalSession session = TerminalManager.getSession(player);
        String currentPath = session != null ? session.getCurrentPath() : "";
        String result = executor.execute(player, "cat", new String[]{name}, currentPath);
        source.sendSuccess(() -> Component.literal(result).withStyle(
            result.startsWith("Error:") ? ChatFormatting.RED : ChatFormatting.WHITE), false);
        return 1;
    }

    private static int executeCd(CommandSourceStack source, String path) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        TerminalSession session = TerminalManager.getSession(player);
        if (session == null) {
            TerminalManager.enterTerminalMode(player);
            session = TerminalManager.getSession(player);
        }
        String result = executor.execute(player, "cd", new String[]{path}, session.getCurrentPath());
        if (executor.wasCdSuccessful()) {
            session.setCurrentPath(executor.getCurrentPath());
        }
        source.sendSuccess(() -> Component.literal(result).withStyle(
            result.contains("permission") ? ChatFormatting.RED : ChatFormatting.WHITE), false);
        return 1;
    }

    private static int executePwd(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        TerminalSession session = TerminalManager.getSession(player);
        String currentPath = session != null ? session.getCurrentPath() : "";
        String result = "/" + player.getUUID().toString() + (currentPath.isEmpty() ? "" : "/" + currentPath);
        source.sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    private static int executeEcho(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int executeClear(CommandSourceStack source) {
        for (int i = 0; i < 20; i++) {
            source.sendSystemMessage(Component.literal(""));
        }
        return 1;
    }

    private static int executeWhoami(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        source.sendSuccess(() -> Component.literal(player.getName().getString()), false);
        return 1;
    }

    private static int executeRefresh(CommandSourceStack source) {
        BinaryPluginManager.refreshPlugins();
        int count = BinaryPluginManager.getPluginCount();
        source.sendSuccess(() -> Component.literal("Binary plugins refreshed. Found " + count + " plugins."), false);
        return 1;
    }

    private static int executeUser(CommandSourceStack source, String targetPlayer, String action, String params) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        if (!player.hasPermissions(2)) {
            source.sendFailure(Component.literal("You do not have permission to use this command").withStyle(ChatFormatting.RED));
            return 0;
        }
        String result = executor.execute(player, "user", new String[]{targetPlayer, action, params}, "");
        source.sendSuccess(() -> Component.literal(result), false);
        return 1;
    }
}
