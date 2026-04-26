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
import unsa.st.com.client.ClientVirtualFileSystem;
import unsa.st.com.filesystem.UserFileSystem;
import unsa.st.com.pkg.PkgManager;
import unsa.st.com.plugin.BinaryPluginManager;
import unsa.st.com.dummy.PlayerMacroManager;
import unsa.st.com.ShortcutTerminal;
import unsa.st.com.util.OfflineTeleportManager;
import unsa.st.com.music.MusicPlaybackManager;
import unsa.st.com.network.ModNetwork;
import unsa.st.com.network.BlackScreenPayload;
import unsa.st.com.network.ScreenshotPayload;

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

    private String executeLs() {
        List<String> files = isClient ?
                ClientVirtualFileSystem.listDirectory(playerName, currentPath) :
                UserFileSystem.listDirectory(playerUuid, currentPath);
        if (files == null) return "Error: Directory not found.";
        return String.join("  ", files);
    }

    private String executeMkdir(String[] args) {
        if (args.length == 0) return "Usage: mkdir <directory>";
        if (!isValidUserPath(currentPath)) return "Error: Access denied.";
        boolean ok = isClient ?
                ClientVirtualFileSystem.createDirectory(playerName, currentPath, args[0]) :
                UserFileSystem.createDirectory(playerUuid, currentPath, args[0]);
        return ok ? "Directory created." : "Error: Failed to create directory.";
    }

    private String executeTouch(String[] args) {
        if (args.length == 0) return "Usage: touch <file>";
        if (!isValidUserPath(currentPath)) return "Error: Access denied.";
        boolean ok = isClient ?
                ClientVirtualFileSystem.createFile(playerName, currentPath, args[0]) :
                UserFileSystem.createFile(playerUuid, currentPath, args[0]);
        return ok ? "File created." : "Error: Failed to create file.";
    }

    private String executeRm(String[] args) {
        if (args.length == 0) return "Usage: rm [-r] <name>";
        boolean recursive = args[0].equals("-r");
        String target = recursive ? (args.length > 1 ? args[1] : "") : args[0];
        if (target.isEmpty()) return "Invalid target.";
        if (!isValidUserPath(currentPath)) return "Error: Access denied.";
        boolean ok = isClient ?
                ClientVirtualFileSystem.delete(playerName, currentPath, target, recursive) :
                UserFileSystem.delete(playerUuid, currentPath, target, recursive);
        return ok ? "Deleted." : "Error: Failed to delete.";
    }

    private String executeCat(String[] args) {
        if (args.length == 0) return "Usage: cat <file>";
        if (!isValidUserPath(currentPath)) return "Error: Access denied.";
        String content = readFileSafe(args[0]);
        return content != null ? content : "Error: File not found.";
    }

    private String executeEcho(String[] args) { return String.join(" ", args); }

    private String executeCd(String[] args) {
        if (args.length == 0 || args[0].trim().isEmpty() || args[0].equals(".") || args[0].equals("./")) {
            currentPath = "";
            cdSuccessful = true;
            return "Changed directory to: " + getCwdDisplay();
        }
        String newPath = UserFileSystem.normalizePath(currentPath, args[0]);
        List<String> test = isClient ?
                ClientVirtualFileSystem.listDirectory(playerName, newPath) :
                UserFileSystem.listDirectory(playerUuid, newPath);
        if (test != null) {
            currentPath = newPath;
            cdSuccessful = true;
            return "Changed directory to: " + getCwdDisplay();
        }
        cdSuccessful = false;
        return "Error: Directory not found.";
    }

    public boolean wasCdSuccessful() { return cdSuccessful; }
    public String getCurrentPath() { return currentPath; }
    public void setCurrentPath(String path) { this.currentPath = path; }

    private String getCwdDisplay() { return currentPath.isEmpty() ? "~" : currentPath; }
    private String executePwd() { return currentPath.isEmpty() ? "/" : currentPath; }

    private String executeCp(String[] args) {
        if (args.length < 2) return "Usage: cp <source> <destination>";
        String content = readFileSafe(args[0]);
        if (content == null) return "Error: Source file not found.";
        writeFileSafe(args[1], content);
        return "Copied.";
    }

    private String executeMv(String[] args) {
        if (args.length < 2) return "Usage: mv <source> <destination>";
        String content = readFileSafe(args[0]);
        if (content == null) return "Error: Source file not found.";
        writeFileSafe(args[1], content);
        if (isClient) {
            ClientVirtualFileSystem.delete(playerName, currentPath, args[0], false);
        } else {
            UserFileSystem.delete(playerUuid, currentPath, args[0], false);
        }
        return "Moved.";
    }

    private String executeHead(String[] args) {
        if (args.length == 0) return "Usage: head [-n N] <file>";
        int lines = 10; String file;
        if (args[0].equals("-n")) {
            if (args.length < 3) return "Usage: head [-n N] <file>";
            try { lines = Integer.parseInt(args[1]); } catch (NumberFormatException e) { return "Invalid number."; }
            file = args[2];
        } else file = args[0];
        String content = readFileSafe(file);
        if (content == null) return "Error: File not found.";
        String[] allLines = content.split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(lines, allLines.length); i++) sb.append(allLines[i]).append("\n");
        return sb.toString().trim();
    }

    private String executeTail(String[] args) {
        if (args.length == 0) return "Usage: tail [-n N] <file>";
        int lines = 10; String file;
        if (args[0].equals("-n")) {
            if (args.length < 3) return "Usage: tail [-n N] <file>";
            try { lines = Integer.parseInt(args[1]); } catch (NumberFormatException e) { return "Invalid number."; }
            file = args[2];
        } else file = args[0];
        String content = readFileSafe(file);
        if (content == null) return "Error: File not found.";
        String[] allLines = content.split("\n");
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, allLines.length - lines);
        for (int i = start; i < allLines.length; i++) sb.append(allLines[i]).append("\n");
        return sb.toString().trim();
    }

    private String executeWc(String[] args) {
        if (args.length == 0) return "Usage: wc <file>";
        String content = readFileSafe(args[0]);
        if (content == null) return "Error: File not found.";
        int lines = content.split("\n").length;
        int words = content.split("\\s+").length;
        return String.format("%d %d %d %s", lines, words, content.length(), args[0]);
    }

    private String executeGrep(String[] args) {
        if (args.length < 2) return "Usage: grep <pattern> <file>";
        String content = readFileSafe(args[1]);
        if (content == null) return "Error: File not found.";
        StringBuilder sb = new StringBuilder();
        for (String line : content.split("\n")) if (line.contains(args[0])) sb.append(line).append("\n");
        return sb.toString().trim();
    }

    private String executeSort(String[] args) {
        if (args.length == 0) return "Usage: sort <file>";
        String content = readFileSafe(args[0]);
        if (content == null) return "Error: File not found.";
        List<String> lines = new ArrayList<>(Arrays.asList(content.split("\n")));
        Collections.sort(lines);
        return String.join("\n", lines);
    }

    private String executeUniq(String[] args) {
        if (args.length == 0) return "Usage: uniq <file>";
        String content = readFileSafe(args[0]);
        if (content == null) return "Error: File not found.";
        StringBuilder sb = new StringBuilder();
        String prev = null;
        for (String line : content.split("\n")) {
            if (!line.equals(prev)) { sb.append(line).append("\n"); prev = line; }
        }
        return sb.toString().trim();
    }

    private String executeUname(String[] args) { return System.getProperty("os.name") + " " + System.getProperty("os.arch"); }
    private String executeDf(String[] args) { return "Filesystem data not available."; }
    private String executeFree(String[] args) { return "Memory data not available."; }
    private String executePs(String[] args) { return "Process list not available."; }
    private String executeDu(String[] args) { return "Disk usage not available."; }

    private String executePing(String[] args) {
        if (args.length == 0) return "Usage: ping <host>";
        try { return InetAddress.getByName(args[0]).isReachable(3000) ? "Host reachable" : "Host unreachable";
        } catch (IOException e) { return "Error: Unknown host."; }
    }

    private String executeCurl(String[] args) {
        if (args.length == 0) return "Usage: curl <url>";
        return fetchUrl(args[0]);
    }

    private String executeWget(String[] args) {
        if (args.length < 2) return "Usage: wget <url> <output_file>";
        String content = fetchUrl(args[0]);
        if (content.startsWith("Error:")) return content;
        writeFileSafe(args[1], content);
        return "Downloaded to " + args[1];
    }

    private String fetchUrl(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000); conn.setReadTimeout(5000);
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder content = new StringBuilder(); String line;
            while ((line = in.readLine()) != null) content.append(line).append("\n");
            in.close(); conn.disconnect();
            return content.toString().trim();
        } catch (Exception e) { return "Error: Failed to fetch URL."; }
    }

    private String executeWhich(String[] args) {
        if (args.length == 0) return "Usage: which <command>";
        Path found = findExecutableInPath(args[0]);
        if (found != null) return found.toString();
        return args[0] + " not found";
    }

    private String executeChmod(String[] args) {
        if (args.length < 2) return "Usage: chmod <mode> <file>";
        if (args[0].equals("+x")) {
            if (isClient) { ClientVirtualFileSystem.setExecutable(playerName, currentPath, args[1], true); return "Added execute permission."; }
            else { UserFileSystem.setExecutable(playerUuid, currentPath, args[1], true); return "Added execute permission."; }
        }
        return "Error: Only +x is supported.";
    }

    private String executeSh(String[] args) {
        if (args.length == 0) return "Usage: sh <script>";
        String script = readFileSafe(args[0]);
        if (script == null) return "Error: Script not found.";
        StringBuilder output = new StringBuilder();
        for (String line : script.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split("\\s+");
            output.append("> ").append(line).append("\n").append(execute(parts[0], Arrays.copyOfRange(parts, 1, parts.length))).append("\n");
        }
        return output.toString().trim();
    }

    private String executeRefresh(String[] args) {
        if (args.length == 0) return "Usage: refresh <plugin|bf>";
        if (args[0].equalsIgnoreCase("plugin")) { BinaryPluginManager.refreshPlugins(); return "Plugins refreshed."; }
        return "Usage: refresh <plugin|bf>";
    }

    private String executePkg(String[] args) {
        if (args.length == 0) return "Usage: pkg <update|search|install|remove|list|show>";
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "update": return PkgManager.updateIndex();
            case "install": return args.length > 1 ? PkgManager.install(args[1], isClient) : "Usage: pkg install <package>";
            case "remove": return args.length > 1 ? PkgManager.remove(args[1], isClient) : "Usage: pkg remove <package>";
            case "list": return String.join("\n", PkgManager.listInstalled(isClient));
            case "search": return args.length > 1 ? String.join("\n", PkgManager.search(args[1])) : "Usage: pkg search <keyword>";
            case "show": return args.length > 1 ? PkgManager.showInfo(args[1]) : "Usage: pkg show <package>";
            default: return "Unknown pkg command.";
        }
    }

    private String executeMacro(String[] args) {
        if (!isClient) return "macro can only be used in terminal panel.";
        if (args.length < 2) return "Usage: macro start <operate> [interval_ms]";
        if (args[0].equalsIgnoreCase("start")) {
            try { PlayerMacroManager.startMacro(args[1], Long.parseLong(args.length > 2 ? args[2] : "3000")); return "Macro started.";
            } catch (NumberFormatException e) { return "Invalid interval."; }
        }
        return "Usage: macro start <operate> [interval_ms]";
    }

    private String executeStop(String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("macro")) {
            if (isClient) { PlayerMacroManager.stopMacro(); return "Macro stopped."; }
            else return "stop macro can only be used in terminal panel.";
        }
        return "Usage: stop macro";
    }

    // ==================== RUN / SPOOF (完整展开) ====================
    private String executeRun(String[] args) {
        if (args.length == 0) return "Usage: run <module> [args...]";
        String module = args[0].toLowerCase(Locale.ROOT);
        String[] moduleArgs = Arrays.copyOfRange(args, 1, args.length);
        if ("spoof".equals(module)) return executeSpoof(moduleArgs);
        return "Unknown run module: " + module;
    }

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

    private Map<String, String> parseParams(String[] args) { Map<String, String> map = new HashMap<>(); for (String a : args) { int d = a.indexOf('-'); if (d>0) map.put(a.substring(0,d).toLowerCase(), a.substring(d+1)); } return map; }
    private int getIntParam(Map<String, String> p, String k, int def) { try { return Integer.parseInt(p.getOrDefault(k, String.valueOf(def))); } catch (NumberFormatException e) { return def; } }
    private float getFloatParam(Map<String, String> p, String k, float def) { try { return Float.parseFloat(p.getOrDefault(k, String.valueOf(def))); } catch (NumberFormatException e) { return def; } }
    private long parseTimeMs(String t, long defSec) { if(t==null||t.isEmpty()) return defSec*1000; t=t.toLowerCase(); try { if(t.endsWith("ms")) return Long.parseLong(t.replace("ms","")); if(t.endsWith("s")) return Long.parseLong(t.replace("s",""))*1000; if(t.endsWith("m")) return Long.parseLong(t.replace("m",""))*60000; if(t.endsWith("h")) return Long.parseLong(t.replace("h",""))*3600000; return Long.parseLong(t)*1000; } catch (NumberFormatException e) { return defSec*1000; } }
    private ServerPlayer getServerPlayer(String name) { if (Minecraft.getInstance().hasSingleplayerServer()) return Minecraft.getInstance().getSingleplayerServer().getPlayerList().getPlayerByName(name); return null; }

    // 修复：使用反射设置Creeper充能，避免访问私有字段 DATA_IS_POWERED
    private String spoofCreeper(ServerPlayer t, Map<String, String> p) {
        int q = Math.min(getIntParam(p,"quantity",1), 64);
        boolean charged = "lightning".equalsIgnoreCase(p.get("morphology"));
        String ts = p.get("time");
        Level l = t.level();
        BlockPos pos = t.blockPosition();
        for (int i=0; i<q; i++) {
            Creeper c = EntityType.CREEPER.create(l);
            if (c != null) {
                c.setPos(pos.getX()+0.5, pos.getY(), pos.getZ()+0.5);
                if (charged) {
                    try {
                        java.lang.reflect.Method method = Creeper.class.getMethod("setPowered", boolean.class);
                        method.invoke(c, true);
                    } catch (Exception ignored) {}
                }
                l.addFreshEntity(c);
                if ("moment".equalsIgnoreCase(ts)) c.ignite();
                else if (ts != null && !ts.isEmpty()) scheduler.schedule(c::ignite, parseTimeMs(ts,0), TimeUnit.MILLISECONDS);
            }
        }
        return "Creeper done.";
    }

    private String spoofRay(ServerPlayer t, Map<String, String> p) { int q = getIntParam(p,"quantity",1); float dmg = getFloatParam(p,"injure",5); for (int i=0;i<q;i++) { LightningBolt bolt=EntityType.LIGHTNING_BOLT.create(t.level()); if(bolt!=null){ bolt.setPos(Vec3.atBottomCenterOf(t.blockPosition())); bolt.setCause(t); t.level().addFreshEntity(bolt); } } t.hurt(t.damageSources().lightningBolt(),dmg); return "Ray done."; }
    private String spoofFlyup(ServerPlayer t, Map<String, String> p) { String m=p.getOrDefault("manner",""); Vec3 dest; if(p.containsKey("coordinates")) { String[] parts=p.get("coordinates").split(","); dest=new Vec3(Double.parseDouble(parts[0]),Double.parseDouble(parts[1]),Double.parseDouble(parts[2])); } else dest=t.position().add(0,100,0); t.teleportTo(dest.x,dest.y,dest.z); if("no".equalsIgnoreCase(p.get("injure"))) t.fallDistance=0; return "Flyup done."; }
    private String spoofEvasiveGround(ServerPlayer t, Map<String, String> p) { Vec3 dest; if(p.containsKey("coordinates")) { String[] parts=p.get("coordinates").split(","); dest=new Vec3(Double.parseDouble(parts[0]),Double.parseDouble(parts[1]),Double.parseDouble(parts[2])); } else dest=t.position().add(0,-10,0); t.teleportTo(dest.x,dest.y,dest.z); if("yes".equalsIgnoreCase(p.get("injure"))) t.hurt(t.damageSources().inWall(),2); return "EvasiveGround done."; }
    private String spoofStop(ServerPlayer t, Map<String, String> p) { String ts=p.get("time"); if(ts==null) return "Missing time"; long ms=parseTimeMs(ts,0); Vec3 pos=t.position(); float yr=t.getYRot(),xr=t.getXRot(); scheduler.scheduleAtFixedRate(()->{ t.teleportTo(pos.x,pos.y,pos.z); t.setYRot(yr); t.setXRot(xr); t.setDeltaMovement(0,0,0); },0,50,TimeUnit.MILLISECONDS); scheduler.schedule(()->{},ms,TimeUnit.MILLISECONDS); return "Stop done."; }
    private String spoofQuickly(ServerPlayer t, Map<String, String> p) { String ts=p.get("time"); if(ts==null) return "Missing time"; float speed=getFloatParam(p,"speed",2); long ms=parseTimeMs(ts,0); t.getAbilities().setWalkingSpeed(speed/10f); t.onUpdateAbilities(); scheduler.schedule(()->{ t.getAbilities().setWalkingSpeed(0.1f); t.onUpdateAbilities(); },ms,TimeUnit.MILLISECONDS); return "Quickly done."; }
    private String spoofTortoise(ServerPlayer t, Map<String, String> p) { String ts=p.get("time"); if(ts==null) return "Missing time"; long ms=parseTimeMs(ts,0); t.getAbilities().setWalkingSpeed(0.02f); t.onUpdateAbilities(); scheduler.schedule(()->{ t.getAbilities().setWalkingSpeed(0.1f); t.onUpdateAbilities(); },ms,TimeUnit.MILLISECONDS); return "Tortoise done."; }
    private String spoofBlackscreen(ServerPlayer t, Map<String, String> p) { String ts=p.get("time"); if(ts==null) return "Missing time"; long ms=parseTimeMs(ts,0); ModNetwork.sendToPlayer(t,new BlackScreenPayload(true)); scheduler.schedule(()->ModNetwork.sendToPlayer(t,new BlackScreenPayload(false)),ms,TimeUnit.MILLISECONDS); return "Blackscreen done."; }

    // ==================== User 管理命令 (使用正确API) ====================
    private String executeUser(String[] args) {
        if (args.length < 2) return "Usage: User <player> <operation> [options...]";
        String targetName = args[0];
        String operation = args[1];
        String[] opArgs = args.length > 2 ? Arrays.copyOfRange(args, 2, args.length) : new String[0];

        ServerPlayer target = getServerPlayer(targetName);
        UUID targetUuid = target != null ? target.getUUID() : lookupOfflineUUID(targetName);

        if (targetUuid == null) return "Player not found: " + targetName;

        switch (operation.toLowerCase(Locale.ROOT)) {
            case "switchingmode": {
                if (opArgs.length == 0) return "Usage: User <player> switchingmode <mode>";
                GameType type = GameType.byName(opArgs[0]);
                if (type == null) { try { int id = Integer.parseInt(opArgs[0]); type = GameType.byId(id); } catch (NumberFormatException e) { return "Invalid mode number."; } }
                if (type == null) return "Invalid game mode.";
                if (target != null) { target.setGameMode(type); return targetName + " mode updated."; }
                return "Player is offline, cannot switch mode.";
            }
            case "transport-online": {
                if (opArgs.length < 3) return "Usage: User <player> transport-online <x> <y> <z>";
                if (target == null) return "The user is not online.";
                try {
                    double x = Double.parseDouble(opArgs[0]), y = Double.parseDouble(opArgs[1]), z = Double.parseDouble(opArgs[2]);
                    if (target.isPassenger()) target.stopRiding();
                    target.teleportTo(x, y, z);
                    return targetName + " teleported.";
                } catch (NumberFormatException e) { return "Invalid coordinates."; }
            }
            case "transport-offline": {
                if (opArgs.length < 3) return "Usage: User <player> transport-offline <x> <y> <z>";
                if (target != null) return "The user is on the server and the property is not available.";
                try {
                    double x = Double.parseDouble(opArgs[0]), y = Double.parseDouble(opArgs[1]), z = Double.parseDouble(opArgs[2]);
                    // 修复：直接使用你原文件中的静态方法，不再调用错误的 addTeleport
                    OfflineTeleportManager.saveCoordTeleport(targetUuid, x, y, z);
                    return "Offline teleport set for " + targetName;
                } catch (Exception e) { return "Invalid coordinates."; }
            }
            case "ban": {
                if (target != null) target.connection.disconnect(Component.literal("You are banned from this server."));
                banPlayer(targetUuid);
                return targetName + " has been banned.";
            }
            case "op": {
                if (target != null) {
                    Minecraft.getInstance().getSingleplayerServer().getPlayerList().op(target.getGameProfile());
                    return targetName + " is now operator.";
                }
                return "Player must be online to op.";
            }
            default: return "Unknown operation: " + operation;
        }
    }

    private UUID lookupOfflineUUID(String name) {
        if (Minecraft.getInstance().getSingleplayerServer() != null) {
            var playerList = Minecraft.getInstance().getSingleplayerServer().getPlayerList();
            if (playerList.getPlayerByName(name) != null)
                return playerList.getPlayerByName(name).getUUID();
        }
        return null;
    }

    private void banPlayer(UUID uuid) {
        try {
            Path banFile = getGameDir().resolve("banned-players.json");
            Files.writeString(banFile, uuid.toString() + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {}
    }

    private Path getGameDir() {
        return Minecraft.getInstance().getSingleplayerServer().getServerDirectory();
    }
    private String executeMp(String[] args) {
        if (args.length == 0) return "Usage: run mp <path> [loop-<n>] [songlist [run]]";
        String path = args[0];
        int loop = 0;
        boolean songlistMode = false;
        boolean runSonglist = false;
        boolean generateSonglist = false;
        for (int i = 1; i < args.length; i++) {
            String a = args[i].toLowerCase(Locale.ROOT);
            if (a.startsWith("loop-")) {
                try { loop = Integer.parseInt(a.substring(5)); } catch (NumberFormatException e) { return "Invalid loop number."; }
            } else if (a.equals("songlist")) {
                songlistMode = true;
            } else if (a.equals("run")) {
                runSonglist = true;
            }
        }
        java.nio.file.Path resolved = unsa.st.com.filesystem.UserFileSystem.getUserPath(playerUuid).resolve(path.startsWith("/") ? path.substring(1) : path);
        if (songlistMode && !runSonglist && java.nio.file.Files.isDirectory(resolved)) {
            return unsa.st.com.music.MusicPlaybackManager.generateSonglist(playerUuid, path);
        }
        return unsa.st.com.music.MusicPlaybackManager.startPlayback(playerUuid, path, loop, songlistMode && runSonglist);
    }
}
    private String executeScreenshot(String[] args) {
        if (args.length == 0) return "Usage: run screenshot <player> [-aov 1-4]";
        
        String targetName = args[0];
        int aov = 1; // 默认第一人称
        
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("-aov") && i + 1 < args.length) {
                try { aov = Integer.parseInt(args[i + 1]); } catch (NumberFormatException e) { return "Invalid angle of view."; }
                break;
            }
        }
        
        ServerPlayer target = getServerPlayer(targetName);
        if (target == null) return "Player not found: " + targetName;
        
        ModNetwork.sendToPlayer(target, new ScreenshotPayload(aov));
        return "Screenshot request sent to " + targetName;
    }

    private String executeId(String[] args) {
        if (args.length < 2) return "Usage: run id <tid|ram> [options]";
        
        String subCommand = args[0].toLowerCase(Locale.ROOT);
        if ("tid".equals(subCommand) && args.length > 1 && "ram".equals(args[1].toLowerCase())) {
            return TerminalIdManager.listAllTerminals();
        }
        return "Usage: run id tid ram   (list all terminals sorted by RAM usage)";
    }
