package unsa.st.com.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import unsa.st.com.terminal.TerminalManager;
import unsa.st.com.terminal.TerminalSession;
import unsa.st.com.filesystem.UserFileSystem;
import unsa.st.com.util.CommandExecutor;
import unsa.st.com.util.OfflineTeleportManager;
import unsa.st.com.plugin.BinaryPluginManager;

import java.util.UUID;

public class ModCommands {
    private static final CommandExecutor executor = new CommandExecutor();

    private static final SuggestionProvider<CommandSourceStack> ACTIONS = (ctx, builder) ->
        SharedSuggestionProvider.suggest(new String[]{"switchingmode", "changebirthpoint", "transmitto_online", "transmitto_offline"}, builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var builder = Commands.literal("ST")
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
            .then(Commands.literal("open").then(Commands.literal("terminal").then(Commands.literal("page")
                .executes(ctx -> openTerminal(ctx.getSource())))))
            .then(Commands.literal("User")
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("action", StringArgumentType.word()).suggests(ACTIONS)
                        .then(Commands.argument("params", StringArgumentType.greedyString())
                            .executes(ctx -> executeUser(ctx.getSource(),
                                EntityArgument.getPlayer(ctx, "player"),
                                StringArgumentType.getString(ctx, "action"),
                                StringArgumentType.getString(ctx, "params"))))
                        .executes(ctx -> {
                            ctx.getSource().sendFailure(Component.translatable("st.command.user.usage"));
                            return 0;
                        }))));

        dispatcher.register(builder);
    }

    private static int showHelp(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        TerminalSession session = TerminalManager.getSession(player);
        String path = session != null ? session.getCurrentPath() : "";
        String help = executor.execute(player, "help", new String[0], path);
        source.sendSuccess(() -> Component.literal(help), false);
        return 1;
    }

    private static int executeLs(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        TerminalSession session = TerminalManager.getSession(player);
        String path = session != null ? session.getCurrentPath() : "";
        String result = executor.execute(player, "ls", new String[0], path);
        source.sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    private static int executeMkdir(CommandSourceStack source, String name) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        TerminalSession session = TerminalManager.getSession(player);
        String path = session != null ? session.getCurrentPath() : "";
        String result = executor.execute(player, "mkdir", new String[]{name}, path);
        source.sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    private static int executeTouch(CommandSourceStack source, String name) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        TerminalSession session = TerminalManager.getSession(player);
        String path = session != null ? session.getCurrentPath() : "";
        String result = executor.execute(player, "touch", new String[]{name}, path);
        source.sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    private static int executeRm(CommandSourceStack source, String name) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        TerminalSession session = TerminalManager.getSession(player);
        String path = session != null ? session.getCurrentPath() : "";
        String result = executor.execute(player, "rm", new String[]{name}, path);
        source.sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    private static int executeCat(CommandSourceStack source, String name) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        TerminalSession session = TerminalManager.getSession(player);
        String path = session != null ? session.getCurrentPath() : "";
        String result = executor.execute(player, "cat", new String[]{name}, path);
        source.sendSuccess(() -> Component.literal(result), false);
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
        source.sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    private static int executePwd(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        TerminalSession session = TerminalManager.getSession(player);
        String path = session != null ? session.getCurrentPath() : "";
        String result = "/" + player.getUUID() + (path.isEmpty() ? "" : "/" + path);
        source.sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    private static int executeEcho(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int executeClear(CommandSourceStack source) {
        for (int i = 0; i < 20; i++) source.sendSystemMessage(Component.literal(""));
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

    private static int openTerminal(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        TerminalManager.enterTerminalMode(player);
        UserFileSystem.createUserDirectory(player.getUUID());
        player.sendSystemMessage(Component.literal("Entered terminal mode. Type 'exit' to leave."));
        return 1;
    }

    private static int executeUser(CommandSourceStack source, ServerPlayer target, String action, String params) {
        if (!source.hasPermission(2)) {
            source.sendFailure(Component.literal("Permission denied"));
            return 0;
        }
        String[] args = params.split(" ");
        switch (action.toLowerCase()) {
            case "switchingmode":
                if (args.length < 1) return 0;
                GameType gameType = switch (args[0].toLowerCase()) {
                    case "creative", "1" -> GameType.CREATIVE;
                    case "survival", "0" -> GameType.SURVIVAL;
                    case "adventure", "2" -> GameType.ADVENTURE;
                    case "spectator", "3" -> GameType.SPECTATOR;
                    default -> null;
                };
                if (gameType == null) return 0;
                target.setGameMode(gameType);
                source.sendSuccess(() -> Component.translatable("st.command.user.mode_set", target.getName().getString(), gameType.getName()), true);
                break;
            case "changebirthpoint":
                BlockPos pos = target.blockPosition();
                target.setRespawnPosition(target.level().dimension(), pos, 0, true, false);
                source.sendSuccess(() -> Component.translatable("st.command.user.birth_set", target.getName().getString()), true);
                break;
            case "transmitto_online":
                if (args[0].equalsIgnoreCase("home")) {
                    BlockPos home = target.getRespawnPosition();
                    if (home == null) home = target.serverLevel().getSharedSpawnPos();
                    target.teleportTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
                } else {
                    double x = Double.parseDouble(args[0]), y = Double.parseDouble(args[1]), z = Double.parseDouble(args[2]);
                    target.teleportTo(x, y, z);
                }
                source.sendSuccess(() -> Component.literal("Teleported " + target.getName().getString()), true);
                break;
            case "transmitto_offline":
                UUID uuid = target.getUUID();
                if (args[0].equalsIgnoreCase("home")) OfflineTeleportManager.saveHomeTeleport(uuid);
                else OfflineTeleportManager.saveCoordTeleport(uuid, Double.parseDouble(args[0]), Double.parseDouble(args[1]), Double.parseDouble(args[2]));
                source.sendSuccess(() -> Component.literal("Offline teleport scheduled for " + target.getName().getString()), true);
                break;
        }
        return 1;
    }
}
