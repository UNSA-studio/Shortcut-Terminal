package unsa.st.com.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import unsa.st.com.pkg.PkgManager;
import unsa.st.com.ShortcutTerminal;
import unsa.st.com.network.ModNetwork;
import unsa.st.com.network.BlackScreenPayload;
import unsa.st.com.music.MusicPlaybackManager;
import unsa.st.com.client.ClientVirtualFileSystem;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ClientCommandExecutor {
    private final String playerName;
    private UUID playerUuid;
    private String currentPath = "/";
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final List<String> outputBuffer = new ArrayList<>();
    private List<String> commandHistory = new ArrayList<>();
    private boolean pendingChanges = false;

    public ClientCommandExecutor(String playerName) {
        this.playerName = playerName;
        if (Minecraft.getInstance().player != null) {
            this.playerUuid = Minecraft.getInstance().player.getUUID();
        }
    }

    public String getCurrentPath() { return currentPath; }
    public void setCurrentPath(String path) { this.currentPath = path; }
    public List<String> getCommandHistory() { return commandHistory; }
    public void setCommandHistory(List<String> history) { this.commandHistory = new ArrayList<>(history); }
    public void addCommandToHistory(String cmd) { commandHistory.add(cmd); pendingChanges = true; }
    public boolean hasPendingChanges() { return pendingChanges; }
    public void clearPendingChanges() { pendingChanges = false; }
    public UUID getPlayerUuid() { return playerUuid; }
    public String getPlayerName() { return playerName; }

    public String execute(String command, String[] args) {
        String result = executeBuiltInCommand(command, args);
        if (result != null) return result;
        Path ext = findExecutableInPath(command);
        if (ext != null) return executeExternalProgram(ext, args);
        return "Error: Unknown command. Type 'help' for available commands.";
    }

    private String executeBuiltInCommand(String command, String[] args) {
        switch (command.toLowerCase(Locale.ROOT)) {
            case "help": return getHelp();
            case "ls": return executeLs();
            case "mkdir": return executeMkdir(args);
            case "touch": return executeTouch(args);
            case "rm": return executeRm(args);
            case "cat": return executeCat(args);
            case "echo": return executeEcho(args);
            case "cd": return executeCd(args);
            case "pwd": return executePwd();
            case "clear": return "";
            case "pkg": return executePkg(args);
            case "run": return executeRun(args);
            default: return null;
        }
    }

    // ... 其余方法（findExecutableInPath, executeExternalProgram, getHelp, executeLs, executeMkdir, executeTouch, executeRm, executeCat, executeEcho, executeCd, executePwd, executePkg, executeRun, executeSpoof, executeMp, spoof子动作等）必须完整保留。
    // 由于篇幅，这里省略，实际覆盖时必须使用完整版本。
    // 请使用之前能编译通过的 ClientCommandExecutor.java 版本，并将 executeMp 方法更新为：
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
    // ... 并确保 getServerPlayer 方法使用了 ServerLifecycleHooks 或正确的服务端获取方式。
}
