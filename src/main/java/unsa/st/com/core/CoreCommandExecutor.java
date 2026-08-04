package unsa.st.com.core;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
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
            default: return null;
        }
    }

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
        return "External execution not available.";
    }

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
            return "Changed directory to: " + (currentPath.isEmpty() ? "/" : currentPath);
        }
        String newPath = UserFileSystem.normalizePath(currentPath, args[0]);
        List<String> test = isClient ?
                ClientVirtualFileSystem.listDirectory(playerName, newPath) :
                UserFileSystem.listDirectory(playerUuid, newPath);
        if (test != null) {
            currentPath = newPath;
            cdSuccessful = true;
            return "Changed directory to: " + (currentPath.isEmpty() ? "/" : currentPath);
        }
        cdSuccessful = false;
        return "Error: Directory not found.";
    }

    public boolean wasCdSuccessful() { return cdSuccessful; }
    public String getCurrentPath() { return currentPath; }
    public void setCurrentPath(String path) { this.currentPath = path; }

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
        List<String> lineList = new ArrayList<>(Arrays.asList(content.split("\n")));
        Collections.sort(lineList);
        return String.join("\n", lineList);
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

    private String executeDf(String[] args) {
        File root = new File(".");
        long total = root.getTotalSpace();
        long free = root.getFreeSpace();
        long used = total - free;
        return String.format("Filesystem      Size     Used    Avail   Use%%\n/                %s  %s  %s  %d%%",
                formatSize(total), formatSize(used), formatSize(free),
                total > 0 ? (used * 100 / total) : 0);
    }

    private String executeFree(String[] args) {
        Runtime rt = Runtime.getRuntime();
        long total = rt.totalMemory();
        long freeMem = rt.freeMemory();
        long used = total - freeMem;
        long max = rt.maxMemory();
        return String.format("              total       used       free\nMem:           %s  %s  %s\nMax:           %s",
                formatSize(total), formatSize(used), formatSize(freeMem), formatSize(max));
    }

    private String executePs(String[] args) {
        MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
        if (srv == null) return "No server running.";
        var players = srv.getPlayerList().getPlayers();
        if (players.isEmpty()) return "No players online.";
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-20s %-36s\n", "PLAYER", "UUID"));
        sb.append("-".repeat(56)).append("\n");
        for (var p : players) {
            sb.append(String.format("%-20s %-36s\n", p.getName().getString(), p.getUUID().toString()));
        }
        sb.append("\nTotal: ").append(players.size()).append(" player(s)");
        return sb.toString();
    }

    private String executeDu(String[] args) {
        Path userRoot = UserFileSystem.getUserPath(playerUuid);
        if (!Files.exists(userRoot)) return "0\t/";
        try {
            final long[] totalSize = {0};
            Files.walkFileTree(userRoot, new SimpleFileVisitor<Path>() {
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    totalSize[0] += attrs.size();
                    return FileVisitResult.CONTINUE;
                }
            });
            return formatSize(totalSize[0]) + "\t/";
        } catch (IOException e) { return "Error: Cannot calculate disk usage."; }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1048576) return String.format("%.1fK", bytes / 1024.0);
        if (bytes < 1073741824) return String.format("%.1fM", bytes / 1048576.0);
        return String.format("%.1fG", bytes / 1073741824.0);
    }

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

    private String executeRun(String[] args) {
        if (args.length == 0) return "Usage: run <module> [args...]";
        String module = args[0].toLowerCase(Locale.ROOT);
        String[] moduleArgs = Arrays.copyOfRange(args, 1, args.length);
        switch (module) {
            case "spoof": return executeSpoof(moduleArgs);
            case "screenshot": return executeScreenshot(moduleArgs);
            case "id": return executeId(moduleArgs);
            default: return "Unknown run module: " + module;
        }
    }

    private String executeScreenshot(String[] args) {
        if (args.length == 0) return "Usage: run screenshot <player> [-aov 1-4]";
        String targetName = args[0];
        int aov = 1;
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
        return "Usage: run id tid ram   (list all terminals)";
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

    private Map<String, String> parseParams(String[] args) {
        Map<String, String> map = new HashMap<>();
        for (String a : args) { int d = a.indexOf('-'); if (d>0) map.put(a.substring(0,d).toLowerCase(), a.substring(d+1)); }
        return map;
    }

    private int getIntParam(Map<String, String> p, String k, int def) {
        try { return Integer.parseInt(p.getOrDefault(k, String.valueOf(def))); } catch (NumberFormatException e) { return def; }
    }

    private float getFloatParam(Map<String, String> p, String k, float def) {
        try { return Float.parseFloat(p.getOrDefault(k, String.valueOf(def))); } catch (NumberFormatException e) { return def; }
    }

    private long parseTimeMs(String t, long defSec) {
        if(t==null||t.isEmpty()) return defSec*1000;
        t=t.toLowerCase();
        try {
            if(t.endsWith("ms")) return Long.parseLong(t.replace("ms",""));
            if(t.endsWith("s")) return Long.parseLong(t.replace("s",""))*1000;
            if(t.endsWith("m")) return Long.parseLong(t.replace("m",""))*60000;
            if(t.endsWith("h")) return Long.parseLong(t.replace("h",""))*3600000;
            return Long.parseLong(t)*1000;
        } catch (NumberFormatException e) { return defSec*1000; }
    }

    private ServerPlayer getServerPlayer(String name) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            return server.getPlayerList().getPlayerByName(name);
        }
        return null;
    }

    private String spoofRay(ServerPlayer t, Map<String, String> p) {
        String fi = p.get("fi");
        if (fi != null) {
            String[] parts = fi.split("-");
            if (parts.length == 2) {
                int count = Integer.parseInt(parts[0]);
                long interval = parseTimeMs(parts[1], 0);
                float dmg = getFloatParam(p, "injure", 5);
                for (int i = 0; i < count; i++) {
                    scheduler.schedule(() -> {
                        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(t.level());
                        if (bolt != null) {
                            bolt.setPos(Vec3.atBottomCenterOf(t.blockPosition()));
                            bolt.setCause(t);
                            t.level().addFreshEntity(bolt);
                            t.hurt(t.damageSources().lightningBolt(), dmg);
                        }
                    }, i * interval, TimeUnit.MILLISECONDS);
                }
                return "Scheduled " + count + " lightning strikes.";
            }
            return "Invalid fi format.";
        }
        int q = getIntParam(p, "quantity", 1);
        float dmg = getFloatParam(p, "injure", 5);
        for (int i = 0; i < q; i++) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(t.level());
            if (bolt != null) { bolt.setPos(Vec3.atBottomCenterOf(t.blockPosition())); bolt.setCause(t); t.level().addFreshEntity(bolt); }
        }
        t.hurt(t.damageSources().lightningBolt(), dmg);
        return "Ray done.";
    }

    private String spoofCreeper(ServerPlayer t, Map<String, String> p) {
        int q = Math.min(getIntParam(p, "quantity", 1), 64);
        boolean charged = "lightning".equalsIgnoreCase(p.get("morphology"));
        String ts = p.get("time");
        Level l = t.level(); BlockPos pos = t.blockPosition();
        for (int i = 0; i < q; i++) {
            Creeper c = EntityType.CREEPER.create(l);
            if (c != null) {
                c.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                if (charged) { try { Creeper.class.getMethod("setPowered", boolean.class).invoke(c, true); } catch (Exception ignored) {} }
                l.addFreshEntity(c);
                if ("moment".equalsIgnoreCase(ts)) c.ignite();
                else if (ts != null && !ts.isEmpty()) scheduler.schedule(c::ignite, parseTimeMs(ts, 0), TimeUnit.MILLISECONDS);
            }
        }
        return "Creeper done.";
    }

    private String spoofFlyup(ServerPlayer t, Map<String, String> p) {
        double height = 100;
        for (String key : p.keySet()) {
            if (key.matches("\\d+\\.?\\d*")) {
                try { height = Double.parseDouble(key); break; } catch (NumberFormatException ignored) {}
            }
        }
        if (p.containsKey("height")) try { height = Double.parseDouble(p.get("height")); } catch (NumberFormatException ignored) {}
        Vec3 dest = t.position().add(0, height, 0);
        if (p.containsKey("coordinates")) {
            String[] parts = p.get("coordinates").split(",");
            dest = new Vec3(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
        }
        t.teleportTo(dest.x, dest.y, dest.z);
        if ("no".equalsIgnoreCase(p.get("injure"))) t.fallDistance = 0;
        return "Flyup done.";
    }

    private String spoofEvasiveGround(ServerPlayer t, Map<String, String> p) {
        double depth = 10;
        for (String key : p.keySet()) {
            if (key.matches("\\d+\\.?\\d*")) {
                try { depth = Double.parseDouble(key); break; } catch (NumberFormatException ignored) {}
            }
        }
        if (p.containsKey("depth")) try { depth = Double.parseDouble(p.get("depth")); } catch (NumberFormatException ignored) {}
        Vec3 dest = t.position().add(0, -depth, 0);
        if (p.containsKey("coordinates")) {
            String[] parts = p.get("coordinates").split(",");
            dest = new Vec3(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
        }
        t.teleportTo(dest.x, dest.y, dest.z);
        if ("yes".equalsIgnoreCase(p.get("injure"))) t.hurt(t.damageSources().inWall(), 2);
        return "EvasiveGround done.";
    }

    private String spoofStop(ServerPlayer t, Map<String, String> p) {
        String ts = p.get("time"); if (ts == null) return "Missing time";
        long ms = parseTimeMs(ts, 0); Vec3 pos = t.position(); float yr = t.getYRot(), xr = t.getXRot();
        java.util.concurrent.ScheduledFuture<?> stopFuture = scheduler.scheduleAtFixedRate(() -> { t.teleportTo(pos.x, pos.y, pos.z); t.setYRot(yr); t.setXRot(xr); t.setDeltaMovement(0,0,0); }, 0, 50, TimeUnit.MILLISECONDS);
        scheduler.schedule(() -> stopFuture.cancel(true), ms, TimeUnit.MILLISECONDS);
        return "Stop done.";
    }

    private String spoofQuickly(ServerPlayer t, Map<String, String> p) {
        String ts = p.get("time"); if (ts == null) return "Missing time";
        float speed = getFloatParam(p, "speed", 2); long ms = parseTimeMs(ts, 0);
        t.getAbilities().setWalkingSpeed(speed / 10f); t.onUpdateAbilities();
        scheduler.schedule(() -> { t.getAbilities().setWalkingSpeed(0.1f); t.onUpdateAbilities(); }, ms, TimeUnit.MILLISECONDS);
        return "Quickly done.";
    }

    private String spoofTortoise(ServerPlayer t, Map<String, String> p) {
        String ts = p.get("time"); if (ts == null) return "Missing time";
        long ms = parseTimeMs(ts, 0);
        t.getAbilities().setWalkingSpeed(0.02f); t.onUpdateAbilities();
        scheduler.schedule(() -> { t.getAbilities().setWalkingSpeed(0.1f); t.onUpdateAbilities(); }, ms, TimeUnit.MILLISECONDS);
        return "Tortoise done.";
    }

    private String spoofBlackscreen(ServerPlayer t, Map<String, String> p) {
        String ts = p.get("time"); if (ts == null) return "Missing time";
        long ms = parseTimeMs(ts, 0);
        ModNetwork.sendToPlayer(t, new BlackScreenPayload(true));
        scheduler.schedule(() -> ModNetwork.sendToPlayer(t, new BlackScreenPayload(false)), ms, TimeUnit.MILLISECONDS);
        return "Blackscreen done.";
    }

    private String executeUser(String[] args) {
        if (args.length < 2) return "Usage: User <player> <operation> [options...]";
        String targetName = args[0];
        String operation = args[1];
        String[] opArgs = args.length > 2 ? Arrays.copyOfRange(args, 2, args.length) : new String[0];

        ServerPlayer target = getServerPlayer(targetName);
        UUID targetUuid = target != null ? target.getUUID() : lookupOfflineUUID(targetName);
        if (targetUuid == null) return "Player not found: " + targetName;

        switch (operation) {
            case "switchingmode":
                if (opArgs.length == 0) return "Usage: User <player> switchingmode <mode>";
                return switchGameMode(target, opArgs[0]);
            case "transport-Online":
                if (opArgs.length == 0) return "Usage: User <player> transport-Online <x> <y> <z>";
                if (target != null) return teleportOnline(target, opArgs);
                return "Player is offline: " + targetName;
            case "transport-Offline":
                if (opArgs.length == 0) return "Usage: User <player> transport-Offline <x> <y> <z>";
                return OfflineTeleportManager.scheduleTeleport(targetUuid, targetName, opArgs);
            case "ban":
                if (target != null) {
                    target.connection.disconnect(Component.literal("You have been banned."));
                    return "Player " + targetName + " has been banned.";
                }
                return "Player is offline: " + targetName;
            case "op":
                MinecraftServer svr = ServerLifecycleHooks.getCurrentServer();
                if (svr != null && target != null) {
                    svr.getPlayerList().op(target.getGameProfile());
                    return "Player " + targetName + " is now an operator.";
                }
                return "Server not available or player is offline.";
            default:
                return "Unknown operation: " + operation + ". Available: switchingmode, transport-Online, transport-Offline, ban, op";
        }
    }

    private String switchGameMode(ServerPlayer target, String mode) {
        if (target == null) return "Player is offline.";
        GameType gameType = switch (mode.toLowerCase()) {
            case "creative", "1" -> GameType.CREATIVE;
            case "survival", "2" -> GameType.SURVIVAL;
            case "spectator", "3" -> GameType.SPECTATOR;
            default -> null;
        };
        if (gameType == null) return "Invalid mode: " + mode;
        target.setGameMode(gameType);
        return "Set " + target.getName().getString() + " to " + gameType.getName();
    }

    private String teleportOnline(ServerPlayer target, String[] coords) {
        try {
            double x = Double.parseDouble(coords[0]);
            double y = Double.parseDouble(coords[1]);
            double z = Double.parseDouble(coords[2]);
            target.teleportTo(x, y, z);
            return "Teleported " + target.getName().getString() + " to " + x + " " + y + " " + z;
        } catch (NumberFormatException e) {
            return "Invalid coordinates.";
        }
    }

    private UUID lookupOfflineUUID(String name) {
        MinecraftServer svr = ServerLifecycleHooks.getCurrentServer();
        if (svr == null) return null;
        var profile = svr.getProfileCache().get(name);
        if (profile.isPresent()) return profile.get().getId();
        return null;
    }
}