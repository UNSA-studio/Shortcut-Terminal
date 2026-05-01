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
        } catch (IOException e) {
            ShortcutTerminal.LOGGER.error("PATH read error", e);
        }
        return null;
    }

    private String executeExternalProgram(Path programPath, String[] args) {
        return "External execution not fully implemented yet.";
    }

    private String getHelp() {
        return "Available: ls, mkdir, touch, rm, cat, echo, cd, pwd, clear, pkg, run spoof, run mp";
    }

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
        if (args.length == 0 || args[0].trim().isEmpty() || args[0].equals(".") || args[0].equals("./")) {
            currentPath = "";
            return "Changed directory to: ~";
        }
        String newPath = ClientVirtualFileSystem.normalizePath(currentPath, args[0]);
        if (ClientVirtualFileSystem.listDirectory(playerName, newPath) != null) {
            currentPath = newPath;
            return "Changed directory to: " + (currentPath.isEmpty() ? "~" : currentPath);
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
            case "mp": return executeMp(moduleArgs);
            default: return "Unknown run module: " + module;
        }
    }

    // ========== SPOOF ==========
private String executeSpoof(String[] args) {        if (args.length == 0) return "Usage: run spoof <action> [player] [parameters...]";        String action = args[0].toLowerCase(Locale.ROOT);        String[] params = targetPlayer.equals(playerName) && args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : (args.length > 2 ? Arrays.copyOfRange(args, 2, args.length) : new String[0]);        ServerPlayer target = getServerPlayer(targetPlayer);        if (target == null) return "Player not found: " + targetPlayer;        Map<String, String> paramMap = parseParams(params);        switch (action) {            case "ray": return spoofRay(target, paramMap);            case "creeper": return spoofCreeper(target, paramMap);            case "flyup": return spoofFlyup(target, paramMap);            case "evasiveground": return spoofEvasiveGround(target, paramMap);            case "stop": return spoofStop(target, paramMap);            case "quickly": return spoofQuickly(target, paramMap);            case "tortoise": return spoofTortoise(target, paramMap);            case "blackscreen": return spoofBlackscreen(target, paramMap);            default: return "Unknown spoof action: " + action;        }    }    private Map<String, String> parseParams(String[] args) {        Map<String, String> map = new HashMap<>();        for (String a : args) { int d = a.indexOf('-'); if (d>0) map.put(a.substring(0,d).toLowerCase(), a.substring(d+1)); }        return map;    }    private int getIntParam(Map<String, String> p, String k, int def) { try { return Integer.parseInt(p.getOrDefault(k, String.valueOf(def))); } catch (NumberFormatException e) { return def; } }    private float getFloatParam(Map<String, String> p, String k, float def) { try { return Float.parseFloat(p.getOrDefault(k, String.valueOf(def))); } catch (NumberFormatException e) { return def; } }    private long parseTimeMs(String t, long defSec) { if(t==null||t.isEmpty()) return defSec*1000; t=t.toLowerCase(); try { if(t.endsWith("ms")) return Long.parseLong(t.replace("ms","")); if(t.endsWith("s")) return Long.parseLong(t.replace("s",""))*1000; if(t.endsWith("m")) return Long.parseLong(t.replace("m",""))*60000; if(t.endsWith("h")) return Long.parseLong(t.replace("h",""))*3600000; return Long.parseLong(t)*1000; } catch (NumberFormatException e) { return defSec*1000; } }    private String spoofRay(ServerPlayer t, Map<String, String> p) {        String fi = p.get("fi");        if (fi != null) {            String[] parts = fi.split("-");            if (parts.length == 2) {                int count = Integer.parseInt(parts[0]);                long interval = parseTimeMs(parts[1], 0);                float dmg = getFloatParam(p, "injure", 5);                for (int i = 0; i < count; i++) {                    scheduler.schedule(() -> {                        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(t.level());                        if (bolt != null) {                            bolt.setPos(Vec3.atBottomCenterOf(t.blockPosition()));                            bolt.setCause(t);                            t.level().addFreshEntity(bolt);                            t.hurt(t.damageSources().lightningBolt(), dmg);                        }                    }, i * interval, TimeUnit.MILLISECONDS);                }                return "Scheduled " + count + " lightning strikes.";            }            return "Invalid fi format.";        }        int q = getIntParam(p, "quantity", 1);        float dmg = getFloatParam(p, "injure", 5);        for (int i = 0; i < q; i++) {            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(t.level());            if (bolt != null) { bolt.setPos(Vec3.atBottomCenterOf(t.blockPosition())); bolt.setCause(t); t.level().addFreshEntity(bolt); }        }        t.hurt(t.damageSources().lightningBolt(), dmg);        return "Ray done.";    }    private String spoofCreeper(ServerPlayer t, Map<String, String> p) {        int q = Math.min(getIntParam(p, "quantity", 1), 64);        boolean charged = "lightning".equalsIgnoreCase(p.get("morphology"));        String ts = p.get("time");        Level l = t.level(); BlockPos pos = t.blockPosition();        for (int i = 0; i < q; i++) {            Creeper c = EntityType.CREEPER.create(l);            if (c != null) {                c.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);                if (charged) { try { Creeper.class.getMethod("setPowered", boolean.class).invoke(c, true); } catch (Exception ignored) {} }                l.addFreshEntity(c);                if ("moment".equalsIgnoreCase(ts)) c.ignite();            }        }        return "Creeper done.";    }    private String spoofFlyup(ServerPlayer t, Map<String, String> p) {        Vec3 dest;        if (p.containsKey("coordinates")) { String[] parts = p.get("coordinates").split(","); dest = new Vec3(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2])); }        else dest = t.position().add(0, 100, 0);        t.teleportTo(dest.x, dest.y, dest.z);        if ("no".equalsIgnoreCase(p.get("injure"))) t.fallDistance = 0;        return "Flyup done.";    }    private String spoofEvasiveGround(ServerPlayer t, Map<String, String> p) {        Vec3 dest;        if (p.containsKey("coordinates")) { String[] parts = p.get("coordinates").split(","); dest = new Vec3(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2])); }        else dest = t.position().add(0, -10, 0);        t.teleportTo(dest.x, dest.y, dest.z);        if ("yes".equalsIgnoreCase(p.get("injure"))) t.hurt(t.damageSources().inWall(), 2);        return "EvasiveGround done.";    }    private String spoofStop(ServerPlayer t, Map<String, String> p) {        String ts = p.get("time"); if (ts == null) return "Missing time";        long ms = parseTimeMs(ts, 0); Vec3 pos = t.position(); float yr = t.getYRot(), xr = t.getXRot();        scheduler.scheduleAtFixedRate(() -> { t.teleportTo(pos.x, pos.y, pos.z); t.setYRot(yr); t.setXRot(xr); t.setDeltaMovement(0,0,0); }, 0, 50, TimeUnit.MILLISECONDS);        scheduler.schedule(() -> {}, ms, TimeUnit.MILLISECONDS);        return "Stop done.";    }    private String spoofQuickly(ServerPlayer t, Map<String, String> p) {        String ts = p.get("time"); if (ts == null) return "Missing time";        float speed = getFloatParam(p, "speed", 2); long ms = parseTimeMs(ts, 0);        t.getAbilities().setWalkingSpeed(speed / 10f); t.onUpdateAbilities();        scheduler.schedule(() -> { t.getAbilities().setWalkingSpeed(0.1f); t.onUpdateAbilities(); }, ms, TimeUnit.MILLISECONDS);        return "Quickly done.";    }    private String spoofTortoise(ServerPlayer t, Map<String, String> p) {        String ts = p.get("time"); if (ts == null) return "Missing time";        long ms = parseTimeMs(ts, 0);        t.getAbilities().setWalkingSpeed(0.02f); t.onUpdateAbilities();        scheduler.schedule(() -> { t.getAbilities().setWalkingSpeed(0.1f); t.onUpdateAbilities(); }, ms, TimeUnit.MILLISECONDS);        return "Tortoise done.";    }    private String spoofBlackscreen(ServerPlayer t, Map<String, String> p) {        String ts = p.get("time"); if (ts == null) return "Missing time";        long ms = parseTimeMs(ts, 0);        ModNetwork.sendToPlayer(t, new BlackScreenPayload(true));        scheduler.schedule(() -> ModNetwork.sendToPlayer(t, new BlackScreenPayload(false)), ms, TimeUnit.MILLISECONDS);        return "Blackscreen done.";    }
    public List<String> getOutputBuffer() { return outputBuffer; }
    public void clearOutputBuffer() { outputBuffer.clear(); }
}