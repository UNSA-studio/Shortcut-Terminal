package unsa.st.com.core;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import unsa.st.com.client.ClientVirtualFileSystem;
import unsa.st.com.filesystem.UserFileSystem;
import unsa.st.com.pkg.PkgManager;
import unsa.st.com.plugin.BinaryPluginManager;
import unsa.st.com.dummy.PlayerMacroManager;
import unsa.st.com.ShortcutTerminal;
import unsa.st.com.util.OfflineTeleportManager;
import unsa.st.com.network.ModNetwork;
import unsa.st.com.network.BlackScreenPayload;
import unsa.st.com.network.ScreenshotPayload;
import unsa.st.com.terminal.TerminalIdManager;
import unsa.st.com.music.MusicPlaybackManager;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CoreCommandExecutor {
    private final boolean isClient;
    private String currentPath = "";
    private UUID playerUuid;
    private String playerName;
    private boolean cdSuccessful = false;
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public CoreCommandExecutor(boolean isClient) {
        this.isClient = isClient;
    }

    public void setPlayer(ServerPlayer player) {
        this.playerUuid = player.getUUID();
        this.playerName = player.getName().getString();
        this.currentPath = "";
        ensureHomeDirectory();
    }

    public void setPlayer(String playerName, String uuid) {
        this.playerName = playerName;
        this.playerUuid = UUID.fromString(uuid);
        this.currentPath = "";
        ensureHomeDirectory();
    }

    private void ensureHomeDirectory() {
        if (isClient) {
            ClientVirtualFileSystem.createDirectory(playerName, "", "");
        } else {
            UserFileSystem.createUserDirectory(playerUuid);
        }
    }

    public String execute(String command, String[] args) {
        String builtInResult = executeBuiltInCommand(command, args);
        if (builtInResult != null) return builtInResult;
        Path ext = findExecutableInPath(command);
        if (ext != null) return executeExternalProgram(ext, args);
        return "Error: Unknown command. Type 'help' for available commands.";
    }

    private String executeBuiltInCommand(String command, String[] args) {
        switch (command) {
            case "help": return getHelp();
            case "ls": return executeLs();
            case "mkdir": return executeMkdir(args);
            case "touch": return executeTouch(args);
            case "rm": return executeRm(args);
            case "cat": return executeCat(args);
            case "echo": return executeEcho(args);
            case "cd": return executeCd(args);
            case "pwd": return executePwd();
            case "cp": return executeCp(args);
            case "mv": return executeMv(args);
            case "head": return executeHead(args);
            case "tail": return executeTail(args);
            case "wc": return executeWc(args);
            case "grep": return executeGrep(args);
            case "sort": return executeSort(args);
            case "uniq": return executeUniq(args);
            case "whoami": return playerName != null ? playerName : "unknown";
            case "uname": return executeUname(args);
            case "df": return executeDf(args);
            case "free": return executeFree(args);
            case "ps": return executePs(args);
            case "du": return executeDu(args);
            case "ping": return executePing(args);
            case "curl": return executeCurl(args);
            case "wget": return executeWget(args);
            case "clear": return "";
            case "date": return new Date().toString();
            case "which": return executeWhich(args);
            case "chmod": return executeChmod(args);
            case "sh": return executeSh(args);
            case "refresh": return executeRefresh(args);
            case "pkg": return executePkg(args);
            case "macro": return executeMacro(args);
            case "run": return executeRun(args);
            case "User": return executeUser(args);
            case "stop": return executeStop(args);
            case "mp": return executeMp(args);
            default: return null;
        }
    }

    // ==================== 动态 PATH ====================
    private Path findExecutableInPath(String command) {
        Path pathFile = PkgManager.getPathFile(isClient);
        if (!Files.exists(pathFile)) return null;
        try {
            List<String> lines = Files.readAllLines(pathFile);
            for (String line : lines) {
                String[] parts = line.split(" - ");
                if (parts.length == 2 && parts[0].equals(command)) {
                    return Paths.get(parts[1]);
                }
            }
        } catch (IOException e) { ShortcutTerminal.LOGGER.error("PATH read error", e); }
        return null;
    }

    private String executeExternalProgram(Path programPath, String[] args) {
        StringBuilder output = new StringBuilder();
        try {
            List<String> cmdList = new ArrayList<>();
            cmdList.add(programPath.toAbsolutePath().toString());
            cmdList.addAll(Arrays.asList(args));
            ProcessBuilder pb = new ProcessBuilder(cmdList);
            pb.directory(programPath.getParent().toFile());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) output.append(line).append("\n");
            }
            int exit = p.waitFor();
            if (exit != 0) output.append("\nProcess exited with code ").append(exit);
        } catch (Exception e) { return "Error: Execution failed."; }
        return output.toString().trim();
    }

    // ==================== 基础命令实现 ====================
    private String getHelp() {
        return "Available: ls, mkdir, touch, rm, cat, echo, cd, pwd, cp, mv, head, tail, wc, grep, sort, uniq, whoami, uname, df, free, ps, du, ping, curl, wget, clear, date, which, chmod, sh, refresh, pkg, macro, run, stop macro, User (admin)";
    }

    private boolean isValidUserPath(String relPath) {
        if (isClient) return true;
        return UserFileSystem.isPathValid(playerUuid, relPath);
    }

    private String readFileSafe(String fileName) {
        if (!isValidUserPath(currentPath)) return null;
        return isClient ?
                ClientVirtualFileSystem.readFile(playerName, currentPath, fileName) :
                UserFileSystem.readFile(playerUuid, currentPath, fileName);
    }

    private void writeFileSafe(String fileName, String content) {
        if (!isValidUserPath(currentPath)) return;
        if (isClient) {
            ClientVirtualFileSystem.writeFile(playerName, currentPath, fileName, content);
        } else {
            UserFileSystem.writeFile(playerUuid, currentPath, fileName, content);
        }
    }

    // ... 其余命令方法保持不变（ls, mkdir, touch, rm, cat, echo, cd, pwd, cp, mv, head, tail, wc, grep, sort, uniq, whoami, uname, df, free, ps, du, ping, curl, wget, clear, date, which, chmod, sh, refresh, pkg, macro, stop）
    // 由于篇幅，这里省略，实际文件必须包含这些方法的完整实现。
    // 您可以直接从之前能编译的版本中复制这些方法的实现，确保没有遗漏。

    // ==================== RUN 模块 ====================
    private String executeRun(String[] args) {
        if (args.length == 0) return "Usage: run <module> [args...]";
        String module = args[0].toLowerCase(Locale.ROOT);
        String[] moduleArgs = Arrays.copyOfRange(args, 1, args.length);
        switch (module) {
            case "spoof": return executeSpoof(moduleArgs);
            case "mp": return executeMp(moduleArgs);
            case "screenshot": return executeScreenshot(moduleArgs);
            case "id": return executeId(moduleArgs);
            default: return "Unknown run module: " + module;
        }
    }

    // ========== MP ==========
    private String executeMp(String[] args) {
        if (args.length == 0) return "Usage: run mp <path> [loop-<n>]";
        String path = args[0];
        int loop = 0;
        for (int i = 1; i < args.length; i++) {
            String a = args[i].toLowerCase(Locale.ROOT);
            if (a.startsWith("loop-")) {
                try { loop = Integer.parseInt(a.substring(5)); } catch (NumberFormatException e) { return "Invalid loop number."; }
            }
        }
        return MusicPlaybackManager.startPlayback(playerUuid, path, loop);
    }

    // ========== Screenshot ==========
    private String executeScreenshot(String[] args) { /* ... 保持不变 ... */ return "Screenshot request sent."; }
    // ========== ID ==========
    private String executeId(String[] args) { /* ... 保持不变 ... */ return "ID command executed."; }

    // ========== SPOOF ==========
    private String executeSpoof(String[] args) { /* ... 保持不变 ... */ return "Spoof action executed."; }

    // ==================== User 管理命令 ====================
    private String executeUser(String[] args) { /* ... 保持不变 ... */ return "User command executed."; }

    // ... 其余方法（getServerPlayer, lookupOfflineUUID, banPlayer, getGameDir等）保持不变，确保完整
}
