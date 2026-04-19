package unsa.st.com.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import unsa.st.com.terminal.TerminalManager;
import unsa.st.com.terminal.TerminalSession;
import unsa.st.com.filesystem.UserFileSystem;
import unsa.st.com.util.CommandExecutor;
import unsa.st.com.util.OfflineTeleportManager;
import unsa.st.com.plugin.BinaryPluginManager;
import unsa.st.com.pkg.PkgManager;
import unsa.st.com.chunk.ChunkLoadManager;
import unsa.st.com.chunk.ChunkRequestManager;
import unsa.st.com.fakeplayer.FakePlayerManager;
import unsa.st.com.fakeplayer.FakePlayerController;
import unsa.st.com.fakeplayer.FakePlayerEntity;
import unsa.st.com.fakeplayer.FakePlayerController;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class ModCommands {
    private static final CommandExecutor executor = new CommandExecutor();

    private static final SuggestionProvider<CommandSourceStack> ACTIONS = (ctx, builder) ->
        SharedSuggestionProvider.suggest(new String[]{"switchingmode", "changebirthpoint", "transmitto_online", "transmitto_offline"}, builder);

    private static final SuggestionProvider<CommandSourceStack> PKG_SUGGEST = (ctx, builder) ->
        SharedSuggestionProvider.suggest(PkgManager.listAvailable(), builder);

    private static final SuggestionProvider<CommandSourceStack> PKG_INSTALLED = (ctx, builder) ->
        SharedSuggestionProvider.suggest(PkgManager.listInstalled(false), builder);

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
                            }))))
                .then(Commands.literal("pkg")
                    .then(Commands.literal("update").executes(ctx -> executePkgUpdate(ctx.getSource())))
                    .then(Commands.literal("install")
                        .then(Commands.argument("package", StringArgumentType.word()).suggests(PKG_SUGGEST)
                            .executes(ctx -> executePkgInstall(ctx.getSource(), StringArgumentType.getString(ctx, "package")))))
                    .then(Commands.literal("remove")
                        .then(Commands.argument("package", StringArgumentType.word()).suggests(PKG_INSTALLED)
                            .executes(ctx -> executePkgRemove(ctx.getSource(), StringArgumentType.getString(ctx, "package")))))
                    .then(Commands.literal("list").executes(ctx -> executePkgList(ctx.getSource())))
                    .then(Commands.literal("search")
                        .then(Commands.argument("keyword", StringArgumentType.word())
                            .executes(ctx -> executePkgSearch(ctx.getSource(), StringArgumentType.getString(ctx, "keyword")))))
                    .then(Commands.literal("info")
                        .then(Commands.argument("package", StringArgumentType.word()).suggests(PKG_SUGGEST)
                            .executes(ctx -> executePkgInfo(ctx.getSource(), StringArgumentType.getString(ctx, "package")))))
                    .then(Commands.literal("path").executes(ctx -> executePkgPath(ctx.getSource()))))
                .then(Commands.literal("run")
                    .then(Commands.literal("strongloading")
                        .then(Commands.literal("now")
                            .executes(ctx -> executeStrongLoading(ctx.getSource(), null, true)))
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                            .then(Commands.argument("y", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                    .executes(ctx -> executeStrongLoading(ctx.getSource(),
                                        new BlockPos(
                                            IntegerArgumentType.getInteger(ctx, "x"),
                                            IntegerArgumentType.getInteger(ctx, "y"),
                                            IntegerArgumentType.getInteger(ctx, "z")
                                        ), false))))))
                    .then(Commands.literal("macro")
                        .then(Commands.argument("args", StringArgumentType.greedyString())
                            .executes(ctx -> executeMacro(ctx.getSource(), StringArgumentType.getString(ctx, "args")))))
                    .then(Commands.literal("dummymodule")
                        .then(Commands.argument("args", StringArgumentType.greedyString())
                            .executes(ctx -> executeDummyModule(ctx.getSource(), StringArgumentType.getString(ctx, "args"))))))
                .then(Commands.literal("stop")
                    .then(Commands.literal("macro")
                        .executes(ctx -> executeStopMacro(ctx.getSource()))))
                .then(Commands.literal("fakeplayer")
                    .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .executes(ctx -> executeFakePlayerCreate(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                    .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .executes(ctx -> executeFakePlayerRemove(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                    .then(Commands.literal("list")
                        .executes(ctx -> executeFakePlayerList(ctx.getSource())))
                    .then(Commands.literal("walk")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .executes(ctx -> executeFakePlayerWalk(ctx.getSource(), StringArgumentType.getString(ctx, "name"), true))))
                    .then(Commands.literal("stopwalk")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .executes(ctx -> executeFakePlayerWalk(ctx.getSource(), StringArgumentType.getString(ctx, "name"), false))))
                    .then(Commands.literal("jump")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .executes(ctx -> executeFakePlayerJump(ctx.getSource(), StringArgumentType.getString(ctx, "name"))))))
        );
        dispatcher.register(Commands.literal("allow")
            .then(Commands.argument("requestId", StringArgumentType.word())
                .executes(ctx -> executeAllow(ctx.getSource(), StringArgumentType.getString(ctx, "requestId")))));
        dispatcher.register(Commands.literal("cancel")
            .then(Commands.argument("requestId", StringArgumentType.word())
                .executes(ctx -> executeCancel(ctx.getSource(), StringArgumentType.getString(ctx, "requestId")))));
    }

    private static int showHelp(CommandSourceStack source) {
        String help = """
                §a=== Shortcut Terminal Commands ===
                §f/ST Help §7- Show this help
                §f/ST ls §7- List files
                §f/ST mkdir <name> §7- Create directory
                §f/ST touch <name> §7- Create file
                §f/ST rm <name> §7- Remove file
                §f/ST cat <name> §7- View file
                §f/ST cd <path> §7- Change directory
                §f/ST pwd §7- Print working directory
                §f/ST whoami §7- Show username
                §f/ST clear §7- Clear screen
                §f/ST refresh bf §7- Refresh binary plugins
                §f/ST User <player> <action> §7- Admin commands
                §f/ST pkg ... §7- Package manager
                §f/ST run strongloading §7- Force chunk load
                §f/ST run macro §7- Player macro
                §f/ST run dummymodule §7- Create fake player
                §f/ST stop macro §7- Stop macro
                §f/ST fakeplayer create <name> §7- Create fake player
                §a================================
                """;
        source.sendSuccess(() -> Component.literal(help), false);
        return 1;
    }

    // ========== 原有基础指令 (ls, mkdir, cd 等) 保持不变 ==========
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
        if (executor.wasCdSuccessful()) session.setCurrentPath(executor.getCurrentPath());
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
        if (!source.hasPermission(2)) { source.sendFailure(Component.literal("Permission denied")); return 0; }
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
    private static int executePkgUpdate(CommandSourceStack source) {
        String result = PkgManager.updateIndex();
        source.sendSuccess(() -> Component.literal(result), false);
        return 1;
    }
    private static int executePkgInstall(CommandSourceStack source, String packageName) {
        String result = PkgManager.install(packageName, false);
        if (result.startsWith("Package installed") || result.startsWith("Package not found") || result.startsWith("Package already"))
            source.sendSuccess(() -> Component.literal(result), false);
        else source.sendFailure(Component.literal(result));
        return 1;
    }
    private static int executePkgRemove(CommandSourceStack source, String packageName) {
        String result = PkgManager.remove(packageName, false);
        if (result.startsWith("Package removed") || result.startsWith("Package not installed"))
            source.sendSuccess(() -> Component.literal(result), false);
        else source.sendFailure(Component.literal(result));
        return 1;
    }
    private static int executePkgList(CommandSourceStack source) {
        List<String> installed = PkgManager.listInstalled(false);
        if (installed.isEmpty()) source.sendSuccess(() -> Component.literal("No packages installed."), false);
        else source.sendSuccess(() -> Component.literal("Installed packages:\n" + String.join("\n", installed)), false);
        return 1;
    }
    private static int executePkgSearch(CommandSourceStack source, String keyword) {
        List<String> results = PkgManager.search(keyword);
        if (results.isEmpty()) source.sendSuccess(() -> Component.literal("No packages found matching: " + keyword), false);
        else source.sendSuccess(() -> Component.literal("Found packages:\n" + String.join("\n", results)), false);
        return 1;
    }
    private static int executePkgInfo(CommandSourceStack source, String packageName) {
        String info = PkgManager.showInfo(packageName);
        source.sendSuccess(() -> Component.literal(info), false);
        return 1;
    }
    private static int executePkgPath(CommandSourceStack source) {
        List<String> path = PkgManager.getPathEntries(false);
        if (path.isEmpty()) source.sendSuccess(() -> Component.literal("PATH is empty."), false);
        else source.sendSuccess(() -> Component.literal("Current PATH:\n" + String.join("\n", path)), false);
        return 1;
    }
    private static int executeStrongLoading(CommandSourceStack source, BlockPos targetPos, boolean useCurrent) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        ServerLevel level = source.getLevel();
        ChunkPos chunkPos = useCurrent ? new ChunkPos(player.blockPosition()) : new ChunkPos(targetPos);
        if (source.hasPermission(2)) {
            ChunkLoadManager.forceLoadChunk(level, chunkPos);
            source.sendSuccess(() -> Component.literal("Chunk " + chunkPos + " is now force loaded."), true);
            return 1;
        }
        ChunkRequestManager.PendingChunkRequest request = new ChunkRequestManager.PendingChunkRequest(
            player.getUUID(), player.getName().getString(), chunkPos, level.dimension());
        ChunkRequestManager.addRequest(request);
        source.sendSuccess(() -> Component.translatable("st.chunk.request.sent"), false);
        Component message = Component.translatable("st.chunk.request.broadcast", player.getName().getString(), chunkPos.x, chunkPos.z);
        for (ServerPlayer admin : level.getServer().getPlayerList().getPlayers())
            if (admin.hasPermissions(2)) {
                admin.sendSystemMessage(message);
                admin.sendSystemMessage(Component.literal("[Allow] ").withStyle(ChatFormatting.GREEN)
                    .append(Component.literal("[/allow " + request.getId() + "]").withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/allow " + request.getId()))))
                    .append(Component.literal(" [Cancel] ").withStyle(ChatFormatting.RED))
                    .append(Component.literal("[/cancel " + request.getId() + "]").withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cancel " + request.getId())))));
            }
        return 1;
    }
    private static int executeAllow(CommandSourceStack source, String requestId) {
        if (!source.hasPermission(2)) { source.sendFailure(Component.literal("Permission denied")); return 0; }
        try {
            UUID id = UUID.fromString(requestId);
            ChunkRequestManager.PendingChunkRequest req = ChunkRequestManager.getRequest(id);
            if (req == null) { source.sendFailure(Component.literal("Request not found")); return 0; }
            ServerLevel level = source.getServer().getLevel(req.getDimension());
            if (level == null) { source.sendFailure(Component.literal("Dimension not found")); return 0; }
            ChunkLoadManager.forceLoadChunk(level, req.getChunkPos());
            ChunkRequestManager.removeRequest(id);
            source.sendSuccess(() -> Component.literal("Chunk force loaded: " + req.getChunkPos()), true);
            ServerPlayer requester = source.getServer().getPlayerList().getPlayer(req.getPlayerUuid());
            if (requester != null) requester.sendSystemMessage(Component.literal("Your strong loading request was approved!"));
        } catch (IllegalArgumentException e) { source.sendFailure(Component.literal("Invalid request ID")); }
        return 1;
    }
    private static int executeCancel(CommandSourceStack source, String requestId) {
        if (!source.hasPermission(2)) { source.sendFailure(Component.literal("Permission denied")); return 0; }
        try {
            UUID id = UUID.fromString(requestId);
            ChunkRequestManager.PendingChunkRequest req = ChunkRequestManager.getRequest(id);
            if (req == null) { source.sendFailure(Component.literal("Request not found")); return 0; }
            ChunkRequestManager.removeRequest(id);
            source.sendSuccess(() -> Component.literal("Request cancelled."), true);
            ServerPlayer requester = source.getServer().getPlayerList().getPlayer(req.getPlayerUuid());
            if (requester != null) requester.sendSystemMessage(Component.literal("Your strong loading request was denied."));
        } catch (IllegalArgumentException e) { source.sendFailure(Component.literal("Invalid request ID")); }
        return 1;
    }
    private static int executeMacro(CommandSourceStack source, String args) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        String[] parts = args.split("\\s+");
        String result = executor.execute(player, "macro", parts, "");
        source.sendSuccess(() -> Component.literal(result), false);
        return 1;
    }
    private static int executeStopMacro(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        String result = executor.execute(player, "stop", new String[]{"macro"}, "");
        source.sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    // ========== 新增假人相关指令 ==========
    private static int executeFakePlayerCreate(CommandSourceStack source, String name) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        if (FakePlayerManager.exists(name)) {
            source.sendFailure(Component.literal("Fake player already exists: " + name));
            return 0;
        }
        FakePlayerEntity fp = FakePlayerManager.createFakePlayer(name, source.getLevel(), player.blockPosition());
        source.sendSuccess(() -> Component.literal("Fake player created: " + name), true);
        return 1;
    }
    private static int executeFakePlayerRemove(CommandSourceStack source, String name) {
        if (FakePlayerManager.removeFakePlayer(name))
            source.sendSuccess(() -> Component.literal("Fake player removed: " + name), true);
        else
            source.sendFailure(Component.literal("Fake player not found: " + name));
        return 1;
    }
    private static int executeFakePlayerList(CommandSourceStack source) {
        Collection<FakePlayerEntity> fakes = FakePlayerManager.getAllFakePlayers();
        if (fakes.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No fake players."), false);
        } else {
            StringBuilder sb = new StringBuilder("Fake players:\n");
            for (FakePlayerEntity fp : fakes)
                sb.append(fp.getGameProfile().getName()).append(" at ").append(fp.blockPosition()).append("\n");
            source.sendSuccess(() -> Component.literal(sb.toString()), false);
        }
        return 1;
    }
    private static int executeFakePlayerWalk(CommandSourceStack source, String name, boolean start) {
        FakePlayerEntity fp = FakePlayerManager.getFakePlayer(name);
        if (fp == null) {
            source.sendFailure(Component.literal("Fake player not found: " + name));
            return 0;
        }
        if (start) {
            FakePlayerController.startAutoWalk(fp, 0.15);
            source.sendSuccess(() -> Component.literal(name + " is now walking."), true);
        } else {
            FakePlayerController.stopAutoWalk();
            source.sendSuccess(() -> Component.literal(name + " stopped walking."), true);
        }
        return 1;
    }
    private static int executeFakePlayerJump(CommandSourceStack source, String name) {
        FakePlayerEntity fp = FakePlayerManager.getFakePlayer(name);
        if (fp == null) {
            source.sendFailure(Component.literal("Fake player not found: " + name));
            return 0;
        }
        FakePlayerController.jump(fp);
        source.sendSuccess(() -> Component.literal(name + " jumped."), true);
        return 1;
    }

    // ========== 映射 run dummymodule ==========
    private static int executeDummyModule(CommandSourceStack source, String args) {
        // 解析参数: -name:test operate:w-d interval:2s
        String name = "dummy";
        String operate = "";
        long interval = 2;
        String[] parts = args.split("\\s+");
        for (String p : parts) {
            if (p.startsWith("-name:")) name = p.substring(6);
            else if (p.startsWith("operate:")) operate = p.substring(8);
            else if (p.startsWith("interval:")) {
                String t = p.substring(9);
                if (t.endsWith("s")) interval = Long.parseLong(t.substring(0, t.length()-1));
                else if (t.endsWith("ms")) interval = Long.parseLong(t.substring(0, t.length()-2)) / 1000;
                else interval = Long.parseLong(t);
            }
        }
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        // 创建假人
        if (!FakePlayerManager.exists(name)) {
            FakePlayerManager.createFakePlayer(name, source.getLevel(), player.blockPosition());
        }
        FakePlayerEntity fp = FakePlayerManager.getFakePlayer(name);
        if (fp == null) {
            source.sendFailure(Component.literal("Failed to create fake player"));
            return 0;
        }
        // 启动行走
        FakePlayerController.startAutoWalk(fp, 0.15);
        final String finalName = name;
        source.sendSuccess(() -> Component.literal("Dummy module started. Fake player: " + finalName), false);
        return 1;
    }
}
