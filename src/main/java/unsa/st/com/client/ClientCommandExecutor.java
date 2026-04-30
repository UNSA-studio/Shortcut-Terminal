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

    // 动态 PATH
    private Path findExecutableInPath(String command) {
        Path pathFile = PkgManager.getPathFile(true);
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
        return "External execution not fully implemented yet.";
    }

    private String getHelp() {
        return "Available: ls, mkdir, touch, rm, cat, echo, cd, pwd, clear, pkg, run spoof, run mp";
    }

    // 内置命令实现
    private String executeLs() {
        List<String> files = ClientVirtualFileSystem.listDirectory(playerName, currentPath);
        if (files == null) return "Error: Directory not found.";
        return String.join("  ", files);
    }

    private String executeMkdir(String[] args) {
        if (args.length == 0) return "Usage: mkdir <directory>";
        boolean ok = ClientVirtualFileSystem.createDirectory(playerName, currentPath, args[0]);
        return ok ? "Directory created." : "Error: Failed to create directory.";
    }

    private String executeTouch(String[] args) {
        if (args.length == 0) return "Usage: touch <file>";
        boolean ok = ClientVirtualFileSystem.createFile(playerName, currentPath, args[0]);
        return ok ? "File created." : "Error: Failed to create file.";
    }

    private String executeRm(String[] args) {
        if (args.length == 0) return "Usage: rm [-r] <name>";
        boolean recursive = args[0].equals("-r");
        String target = recursive ? (args.length > 1 ? args[1] : "") : args[0];
        if (target.isEmpty()) return "Invalid target.";
        boolean ok = ClientVirtualFileSystem.delete(playerName, currentPath, target, recursive);
        return ok ? "Deleted." : "Error: Failed to delete.";
    }

    private String executeCat(String[] args) {
        if (args.length == 0) return "Usage: cat <file>";
        String content = ClientVirtualFileSystem.readFile(playerName, currentPath, args[0]);
        return content != null ? content : "Error: File not found.";
    }

    private String executeEcho(String[] args) {
        return String.join(" ", args);
    }

    private String executeCd(String[] args) {
        if (args.length == 0) return "Usage: cd <path>";
        String newPath = ClientVirtualFileSystem.normalizePath(currentPath, args[0]);
        if (ClientVirtualFileSystem.listDirectory(playerName, newPath) != null) {
            currentPath = newPath;
            return "Changed directory to: " + (currentPath.isEmpty() ? "/" : currentPath);
        }
        return "Error: Directory not found.";
    }

    private String executePwd() {
        return currentPath.isEmpty() ? "/" : currentPath;
    }

    private String executePkg(String[] args) {
        if (args.length == 0) return PkgManager.getHelp();
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "update": return PkgManager.updateIndex();
            case "search": return args.length > 1 ? String.join("\n", PkgManager.search(args[1])) : "Usage: pkg search <keyword>";
            case "install": return args.length > 1 ? PkgManager.install(args[1], true) : "Usage: pkg install <package>";
            case "remove": return args.length > 1 ? PkgManager.remove(args[1], true) : "Usage: pkg remove <package>";
            case "list": return String.join("\n", PkgManager.listInstalled(true));
            case "show": return args.length > 1 ? PkgManager.showInfo(args[1]) : "Usage: pkg show <package>";
            default: return "Unknown pkg command.";
        }
    }

    // ========== RUN ==========
    private String executeRun(String[] args) {
        if (args.length == 0) return "Usage: run <module> [args...]";
        String module = args[0].toLowerCase(Locale.ROOT);
        String[] moduleArgs = Arrays.copyOfRange(args, 1, args.length);
        switch (module) {
            case "spoof": return executeSpoof(moduleArgs);
            default: return "Unknown run module: " + module;
        }
    }

    // MP3 播放
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

    // ========== SPOOF ==========
    private String executeSpoof(String[] args) {
        if (args.length == 0) return "Usage: run spoof <action> [player] [parameters...]";
        String action = args[0].toLowerCase(Locale.ROOT);
        String targetPlayer = args.length > 1 && !args[1].contains("-") ? args[1] : playerName;
        String[] params = targetPlayer.equals(playerName) && args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : (args.length > 2 ? Arrays.copyOfRange(args, 2, args.length) : new String[0]);
        ServerPlayer target = getServerPlayer(targetPlayer);
        if (target == null) return "Player not found: " + targetPlayer;
        Map<String, String> paramMap = parseParams(params);
        switch (action) {
            case "ray": return spoofRay(target, paramMap);
            case "creeper": return spoofCreeper(target, paramMap);
            case "flyup": return spoofFlyup(target, paramMap);
            case "evasiveground": return spoofEvasiveGround(target, paramMap);
            case "stop": return spoofStop(target, paramMap);
            case "quickly": return spoofQuickly(target, paramMap);
            case "tortoise": return spoofTortoise(target, paramMap);
            case "blackscreen": return spoofBlackscreen(target, paramMap);
            default: return "Unknown spoof action: " + action;
        }
    }

    // 工具方法
    private Map<String, String> parseParams(String[] args) {
        Map<String, String> map = new HashMap<>();
        for (String arg : args) {
            int dash = arg.indexOf('-');
            if (dash > 0) map.put(arg.substring(0, dash).toLowerCase(), arg.substring(dash + 1));
        }
        return map;
    }

    private int getIntParam(Map<String, String> p, String k, int def) {
        try { return Integer.parseInt(p.getOrDefault(k, String.valueOf(def))); } catch (NumberFormatException e) { return def; }
    }
    private float getFloatParam(Map<String, String> p, String k, float def) {
        try { return Float.parseFloat(p.getOrDefault(k, String.valueOf(def))); } catch (NumberFormatException e) { return def; }
    }
    private long parseTimeMs(String t, long defSec) {
        if (t == null || t.isEmpty()) return defSec * 1000;
        t = t.toLowerCase();
        try {
            if (t.endsWith("ms")) return Long.parseLong(t.replace("ms", ""));
            if (t.endsWith("s")) return Long.parseLong(t.replace("s", "")) * 1000;
            if (t.endsWith("m")) return Long.parseLong(t.replace("m", "")) * 60000;
            if (t.endsWith("h")) return Long.parseLong(t.replace("h", "")) * 3600000;
            return Long.parseLong(t) * 1000;
        } catch (NumberFormatException e) { return defSec * 1000; }
    }

    private ServerPlayer getServerPlayer(String name) {
        if (Minecraft.getInstance().hasSingleplayerServer()) {
            return ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayerByName(name);
        }
        return null;
    }

    // spoof 子动作
    private String spoofRay(ServerPlayer target, Map<String, String> p) {
        int q = getIntParam(p, "quantity", 1);
        float dmg = getFloatParam(p, "injure", 5.0f);
        Level lvl = target.level();
        BlockPos pos = target.blockPosition();
        for (int i = 0; i < q; i++) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(lvl);
            if (bolt != null) {
                bolt.setPos(Vec3.atBottomCenterOf(pos));
                bolt.setCause(target);
                lvl.addFreshEntity(bolt);
            }
        }
        target.hurt(target.damageSources().lightningBolt(), dmg);
        return "Struck " + target.getName().getString() + " with " + q + " lightning bolts.";
    }

    private String spoofCreeper(ServerPlayer target, Map<String, String> p) {
        int q = Math.min(getIntParam(p, "quantity", 1), 64);
        boolean charged = "lightning".equalsIgnoreCase(p.get("morphology"));
        String timeStr = p.get("time");
        Level lvl = target.level();
        BlockPos pos = target.blockPosition();
        for (int i = 0; i < q; i++) {
            Creeper creeper = EntityType.CREEPER.create(lvl);
            if (creeper != null) {
                creeper.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                if (charged) {
                    try {
                        java.lang.reflect.Method method = Creeper.class.getMethod("setPowered", boolean.class);
                        method.invoke(creeper, true);
                    } catch (Exception ignored) {}
                }
                lvl.addFreshEntity(creeper);
                if ("moment".equalsIgnoreCase(timeStr)) {
                    creeper.ignite();
                } else if (timeStr != null && !timeStr.isEmpty()) {
                    long delay = parseTimeMs(timeStr, 0);
                    scheduler.schedule(creeper::ignite, delay, TimeUnit.MILLISECONDS);
                }
            }
        }
        return "Spawned " + q + (charged ? " charged" : "") + " creepers.";
    }

    private String spoofFlyup(ServerPlayer target, Map<String, String> p) {
        Vec3 dest;
        if (p.containsKey("coordinates")) {
            String[] parts = p.get("coordinates").split(",");
            dest = new Vec3(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
        } else {
            dest = target.position().add(0, 100, 0);
        }
        target.teleportTo(dest.x, dest.y, dest.z);
        if ("no".equalsIgnoreCase(p.get("injure"))) target.fallDistance = 0;
        return "Teleported " + target.getName().getString();
    }

    private String spoofEvasiveGround(ServerPlayer target, Map<String, String> p) {
        Vec3 dest;
        if (p.containsKey("coordinates")) {
            String[] parts = p.get("coordinates").split(",");
            dest = new Vec3(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
        } else {
            dest = target.position().add(0, -10, 0);
        }
        target.teleportTo(dest.x, dest.y, dest.z);
        if ("yes".equalsIgnoreCase(p.get("injure"))) target.hurt(target.damageSources().inWall(), 2.0f);
        return "Burrowed " + target.getName().getString();
    }

    private String spoofStop(ServerPlayer target, Map<String, String> p) {
        String timeStr = p.get("time");
        if (timeStr == null) return "Missing time parameter";
        long ms = parseTimeMs(timeStr, 0);
        Vec3 pos = target.position();
        float yr = target.getYRot(), xr = target.getXRot();
        scheduler.scheduleAtFixedRate(() -> {
            target.teleportTo(pos.x, pos.y, pos.z);
            target.setYRot(yr); target.setXRot(xr);
            target.setDeltaMovement(0,0,0);
        }, 0, 50, TimeUnit.MILLISECONDS);
        scheduler.schedule(() -> {}, ms, TimeUnit.MILLISECONDS);
        return "Froze " + target.getName().getString() + " for " + (ms/1000) + "s";
    }

    private String spoofQuickly(ServerPlayer target, Map<String, String> p) {
        String timeStr = p.get("time");
        if (timeStr == null) return "Missing time";
        float speed = getFloatParam(p, "speed", 2.0f);
        long ms = parseTimeMs(timeStr, 0);
        target.getAbilities().setWalkingSpeed(speed / 10f);
        target.onUpdateAbilities();
        scheduler.schedule(() -> {
            target.getAbilities().setWalkingSpeed(0.1f);
            target.onUpdateAbilities();
        }, ms, TimeUnit.MILLISECONDS);
        return "Speed " + speed + " applied for " + (ms/1000) + "s";
    }

    private String spoofTortoise(ServerPlayer target, Map<String, String> p) {
        String timeStr = p.get("time");
        if (timeStr == null) return "Missing time";
        long ms = parseTimeMs(timeStr, 0);
        target.getAbilities().setWalkingSpeed(0.02f);
        target.onUpdateAbilities();
        scheduler.schedule(() -> {
            target.getAbilities().setWalkingSpeed(0.1f);
            target.onUpdateAbilities();
        }, ms, TimeUnit.MILLISECONDS);
        return "Slowed for " + (ms/1000) + "s";
    }

    private String spoofBlackscreen(ServerPlayer target, Map<String, String> p) {
        String timeStr = p.get("time");
        if (timeStr == null) return "Missing time";
        long ms = parseTimeMs(timeStr, 0);
        ModNetwork.sendToPlayer(target, new BlackScreenPayload(true));
        scheduler.schedule(() -> ModNetwork.sendToPlayer(target, new BlackScreenPayload(false)), ms, TimeUnit.MILLISECONDS);
        return "Blackscreen applied for " + (ms/1000) + "s";
    }

    public List<String> getOutputBuffer() { return outputBuffer; }
    public void clearOutputBuffer() { outputBuffer.clear(); }
}
