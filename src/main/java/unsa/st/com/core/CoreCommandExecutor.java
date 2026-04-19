package unsa.st.com.core;

import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import unsa.st.com.client.ClientVirtualFileSystem;
import unsa.st.com.filesystem.UserFileSystem;
import unsa.st.com.pkg.PkgManager;
import unsa.st.com.plugin.BinaryPluginManager;
import unsa.st.com.dummy.PlayerMacroManager;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class CoreCommandExecutor {
    private final boolean isClient;
    private String currentPath = "/";
    private boolean cdSuccessful = false;
    private UUID playerUuid;
    private String playerName;

    public CoreCommandExecutor(boolean isClient) { this.isClient = isClient; }

    public void setPlayer(ServerPlayer player) {
        this.playerUuid = player.getUUID();
        this.playerName = player.getName().getString();
    }

    public void setPlayer(String playerName, String uuid) {
        this.playerName = playerName;
        this.playerUuid = UUID.fromString(uuid);
    }

    public String execute(String command, String[] args) {
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
            case "cp": return executeCp(args);
            case "mv": return executeMv(args);
            case "head": return executeHead(args);
            case "tail": return executeTail(args);
            case "wc": return executeWc(args);
            case "grep": return executeGrep(args);
            case "sort": return executeSort(args);
            case "uniq": return executeUniq(args);
            case "whoami": return playerName;
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
            case "stop":
                if (args.length > 0 && args[0].equalsIgnoreCase("macro")) {
                    if (isClient) { PlayerMacroManager.stopMacro(); return "Macro stopped."; }
                    else return "stop macro can only be used in terminal panel.";
                }
                return "Usage: stop macro";
            default: return "Error: Unknown command. Type 'help' for available commands.";
        }
    }

    private String getHelp() { return "Available: ls, mkdir, touch, rm, cat, echo, cd, pwd, cp, mv, head, tail, wc, grep, sort, uniq, whoami, uname, df, free, ps, du, ping, curl, wget, clear, date, which, chmod, sh, refresh, pkg, macro, stop macro"; }

    private String executeLs() {
        List<String> files = isClient ? ClientVirtualFileSystem.listDirectory(playerUuid.toString(), currentPath) : UserFileSystem.listDirectory(playerUuid, currentPath);
        return files == null ? "Error" : files.isEmpty() ? "(empty)" : String.join("  ", files);
    }
    private String executeMkdir(String[] args) {
        if (args.length == 0) return "Error: missing operand";
        boolean ok = isClient ? ClientVirtualFileSystem.createDirectory(playerUuid.toString(), currentPath, args[0]) : UserFileSystem.createDirectory(playerUuid, currentPath, args[0]);
        return ok ? "" : "Error";
    }
    private String executeTouch(String[] args) {
        if (args.length == 0) return "Error: missing operand";
        boolean ok = isClient ? ClientVirtualFileSystem.createFile(playerUuid.toString(), currentPath, args[0]) : UserFileSystem.createFile(playerUuid, currentPath, args[0]);
        if (ok && args[0].endsWith(".sh")) {
            if (isClient) ClientVirtualFileSystem.setExecutable(playerUuid.toString(), currentPath, args[0], true);
            else UserFileSystem.setExecutable(playerUuid, currentPath, args[0], true);
        }
        return ok ? "" : "Error";
    }
    private String executeRm(String[] args) {
        if (args.length == 0) return "Error: missing operand";
        boolean rec = args.length > 1 && args[0].equals("-r");
        String tgt = rec ? args[1] : args[0];
        boolean ok = isClient ? ClientVirtualFileSystem.delete(playerUuid.toString(), currentPath, tgt, rec) : UserFileSystem.delete(playerUuid, currentPath, tgt, rec);
        return ok ? "" : "Error";
    }
    private String executeCat(String[] args) {
        if (args.length == 0) return "Error: missing operand";
        String content = isClient ? ClientVirtualFileSystem.readFile(playerUuid.toString(), currentPath, args[0]) : UserFileSystem.readFile(playerUuid, currentPath, args[0]);
        return content != null ? content : "Error: File not found";
    }
    private String executeEcho(String[] args) {
        String full = String.join(" ", args);
        int gt = full.indexOf('>');
        if (gt == -1) return full;
        String text = full.substring(0, gt).trim(), file = full.substring(gt + 1).trim();
        if (file.isEmpty()) return "Error: missing file name";
        if (isClient) {
            ClientVirtualFileSystem.writeFile(playerUuid.toString(), currentPath, file, text);
            if (file.endsWith(".sh")) ClientVirtualFileSystem.setExecutable(playerUuid.toString(), currentPath, file, true);
        } else {
            UserFileSystem.writeFile(playerUuid, currentPath, file, text);
            if (file.endsWith(".sh")) UserFileSystem.setExecutable(playerUuid, currentPath, file, true);
        }
        return "";
    }
    private String executeCd(String[] args) {
        String target = args.length == 0 ? "" : args[0];
        String newPath = isClient ? ClientVirtualFileSystem.normalizePath(currentPath, target) : UserFileSystem.normalizePath(currentPath, target);
        boolean exists = isClient ? ClientVirtualFileSystem.directoryExists(playerUuid.toString(), newPath) : UserFileSystem.directoryExists(playerUuid, currentPath, target);
        if (exists) { currentPath = newPath; cdSuccessful = true; return ""; }
        return "bash: cd: " + target + ": No such file or directory";
    }
    private String executePwd() {
        return isClient ? currentPath : "/" + playerUuid.toString() + (currentPath.equals("/") ? "" : currentPath);
    }
    private String executeCp(String[] args) {
        if (args.length < 2) return "Usage: cp <src> <dst>";
        boolean rec = args.length > 2 && args[0].equals("-r");
        String src = rec ? args[1] : args[0], dst = rec ? args[2] : args[1];
        boolean ok = isClient ? ClientVirtualFileSystem.copy(playerUuid.toString(), currentPath, src, dst, rec) : UserFileSystem.copy(playerUuid, currentPath, src, dst, rec);
        return ok ? "" : "Error: Copy failed";
    }
    private String executeMv(String[] args) {
        if (args.length < 2) return "Usage: mv <src> <dst>";
        boolean ok = isClient ? ClientVirtualFileSystem.move(playerUuid.toString(), currentPath, args[0], args[1]) : UserFileSystem.move(playerUuid, currentPath, args[0], args[1]);
        return ok ? "" : "Error: Move failed";
    }
    private String executeHead(String[] args) {
        if (args.length < 1) return "Usage: head <file> [n]";
        int n = 10; if (args.length > 1) try { n = Integer.parseInt(args[1]); } catch(Exception e){}
        String c = isClient ? ClientVirtualFileSystem.readFile(playerUuid.toString(), currentPath, args[0]) : UserFileSystem.readFile(playerUuid, currentPath, args[0]);
        if (c == null) return "Error";
        String[] lines = c.split("\n");
        return String.join("\n", Arrays.copyOfRange(lines, 0, Math.min(n, lines.length)));
    }
    private String executeTail(String[] args) {
        if (args.length < 1) return "Usage: tail <file> [n]";
        int n = 10; if (args.length > 1) try { n = Integer.parseInt(args[1]); } catch(Exception e){}
        String c = isClient ? ClientVirtualFileSystem.readFile(playerUuid.toString(), currentPath, args[0]) : UserFileSystem.readFile(playerUuid, currentPath, args[0]);
        if (c == null) return "Error";
        String[] lines = c.split("\n");
        int s = Math.max(0, lines.length - n);
        return String.join("\n", Arrays.copyOfRange(lines, s, lines.length));
    }
    private String executeWc(String[] args) {
        if (args.length < 1) return "Usage: wc <file>";
        String c = isClient ? ClientVirtualFileSystem.readFile(playerUuid.toString(), currentPath, args[0]) : UserFileSystem.readFile(playerUuid, currentPath, args[0]);
        if (c == null) return "Error";
        int l = c.split("\n").length, w = c.split("\\s+").length, ch = c.length();
        return String.format("%d %d %d %s", l, w, ch, args[0]);
    }
    private String executeGrep(String[] args) {
        if (args.length < 2) return "Usage: grep <pattern> <file>";
        String c = isClient ? ClientVirtualFileSystem.readFile(playerUuid.toString(), currentPath, args[1]) : UserFileSystem.readFile(playerUuid, currentPath, args[1]);
        if (c == null) return "Error";
        StringBuilder sb = new StringBuilder();
        for (String line : c.split("\n")) if (line.contains(args[0])) sb.append(line).append("\n");
        return sb.toString().trim();
    }
    private String executeSort(String[] args) {
        if (args.length < 1) return "Usage: sort <file>";
        String c = isClient ? ClientVirtualFileSystem.readFile(playerUuid.toString(), currentPath, args[0]) : UserFileSystem.readFile(playerUuid, currentPath, args[0]);
        if (c == null) return "Error";
        List<String> lines = new ArrayList<>(Arrays.asList(c.split("\n")));
        Collections.sort(lines);
        return String.join("\n", lines);
    }
    private String executeUniq(String[] args) {
        if (args.length < 1) return "Usage: uniq <file>";
        String c = isClient ? ClientVirtualFileSystem.readFile(playerUuid.toString(), currentPath, args[0]) : UserFileSystem.readFile(playerUuid, currentPath, args[0]);
        if (c == null) return "Error";
        List<String> out = new ArrayList<>();
        String prev = null;
        for (String line : c.split("\n")) { if (!line.equals(prev)) out.add(line); prev = line; }
        return String.join("\n", out);
    }
    private String executeUname(String[] args) {
        return args.length > 0 && args[0].equals("-a") ? System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch") : System.getProperty("os.name");
    }
    private String executeDf(String[] args) {
        Path root = isClient ? Minecraft.getInstance().gameDirectory.toPath() : Paths.get(".").toAbsolutePath().getRoot();
        try {
            FileStore store = Files.getFileStore(root);
            long total = store.getTotalSpace(), free = store.getUsableSpace();
            if (args.length > 0 && args[0].equals("-h")) return String.format("%s %s %s %d%%", humanReadable(total), humanReadable(total-free), humanReadable(free), (total-free)*100/total);
            else return String.format("%s %d %d %d%%", "root", total/1024, free/1024, (total-free)*100/total);
        } catch (IOException e) { return "Error"; }
    }
    private String executeFree(String[] args) {
        Runtime r = Runtime.getRuntime();
        long total = r.totalMemory(), free = r.freeMemory(), used = total - free;
        if (args.length > 0 && args[0].equals("-h")) return String.format("Mem: %s %s %s", humanReadable(total), humanReadable(used), humanReadable(free));
        else return String.format("Mem: %d %d %d", total/1024, used/1024, free/1024);
    }
    private String executePs(String[] args) { return "PID TTY TIME CMD\n1 ? 00:00:00 java\n2 ? 00:00:00 minecraft"; }
    private String executeDu(String[] args) { return "du: not fully implemented"; }
    private String executePing(String[] args) {
        if (args.length < 1) return "Usage: ping <host>";
        try {
            InetAddress addr = InetAddress.getByName(args[0]);
            long start = System.currentTimeMillis();
            boolean reach = addr.isReachable(3000);
            long time = System.currentTimeMillis() - start;
            return reach ? String.format("64 bytes from %s: time=%d ms", addr.getHostAddress(), time) : "timeout";
        } catch (IOException e) { return "unknown host"; }
    }
    private String executeCurl(String[] args) {
        if (args.length < 1) return "Usage: curl <url>";
        try {
            URL url = new URL(args[0]);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "ShortcutTerminal");
            BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder(); String l;
            while ((l = r.readLine()) != null) sb.append(l).append("\n");
            r.close();
            return sb.toString();
        } catch (IOException e) { return "Error: " + e.getMessage(); }
    }
    private String executeWget(String[] args) {
        if (args.length < 1) return "Usage: wget <url>";
        String url = args[0], name = url.substring(url.lastIndexOf('/') + 1);
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestProperty("User-Agent", "ShortcutTerminal");
            InputStream in = conn.getInputStream();
            if (isClient) ClientVirtualFileSystem.writeFileFromStream(playerUuid.toString(), currentPath, name, in);
            else UserFileSystem.writeFileFromStream(playerUuid, currentPath, name, in);
            return "Downloaded: " + name;
        } catch (IOException e) { return "Error: " + e.getMessage(); }
    }
    private String executeWhich(String[] args) {
        return args.length < 1 ? "Usage: which <cmd>" : "/bin/" + args[0];
    }
    private String executeChmod(String[] args) {
        if (args.length < 2 || !args[0].equals("+x")) return "Usage: chmod +x <file>";
        boolean ok = isClient ? ClientVirtualFileSystem.setExecutable(playerUuid.toString(), currentPath, args[1], true) : UserFileSystem.setExecutable(playerUuid, currentPath, args[1], true);
        return ok ? "" : "Error";
    }
    private String executeSh(String[] args) {
        if (args.length < 1) return "Usage: sh <script>";
        String script = isClient ? ClientVirtualFileSystem.readFile(playerUuid.toString(), currentPath, args[0]) : UserFileSystem.readFile(playerUuid, currentPath, args[0]);
        if (script == null) return "Error: not found";
        StringBuilder out = new StringBuilder();
        for (String line : script.split("\n")) {
            line = line.trim(); if (line.isEmpty() || line.startsWith("#")) continue;
            String[] p = line.split("\\s+");
            out.append(execute(p[0], Arrays.copyOfRange(p, 1, p.length))).append("\n");
        }
        return out.toString().trim();
    }
    private String executeRefresh(String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("bf")) {
            BinaryPluginManager.refreshPlugins();
            return "Plugins refreshed. Found " + BinaryPluginManager.getPluginCount();
        }
        return "Usage: refresh bf";
    }
    private String executePkg(String[] args) {
        if (args.length == 0) return "Usage: pkg <update|install|remove|list|search|info|path>";
        switch (args[0].toLowerCase()) {
            case "update": return PkgManager.updateIndex();
            case "install": return args.length < 2 ? "Usage: pkg install <pkg>" : PkgManager.install(args[1], isClient);
            case "remove": return args.length < 2 ? "Usage: pkg remove <pkg>" : PkgManager.remove(args[1], isClient);
            case "list": List<String> l = PkgManager.listInstalled(isClient); return l.isEmpty() ? "None" : String.join("\n", l);
            case "search": return args.length < 2 ? "Usage: pkg search <kw>" : String.join("\n", PkgManager.search(args[1]));
            case "info": return args.length < 2 ? "Usage: pkg info <pkg>" : PkgManager.showInfo(args[1]);
            case "path": List<String> p = PkgManager.getPathEntries(isClient); return p.isEmpty() ? "PATH empty" : String.join("\n", p);
            default: return "Unknown pkg subcommand";
        }
    }
    private String executeMacro(String[] args) {
        if (!isClient) return "Macro can only be used in terminal panel.";
        String operate = "";
        long interval = 3000;
        for (String arg : args) {
            if (arg.startsWith("operate:")) operate = arg.substring(8);
            else if (arg.startsWith("interval:")) {
                String t = arg.substring(9);
                if (t.endsWith("s")) interval = Long.parseLong(t.substring(0, t.length()-1)) * 1000;
                else if (t.endsWith("ms")) interval = Long.parseLong(t.substring(0, t.length()-2));
                else interval = Long.parseLong(t) * 1000;
            }
        }
        if (operate.isEmpty()) return "Usage: macro operate:<keys> [interval:<time>]";
        PlayerMacroManager.startMacro(operate, interval);
        return "Macro started.";
    }
    private static String humanReadable(long b) {
        if (b < 1024) return b + " B";
        int e = (int) (Math.log(b) / Math.log(1024));
        return String.format("%.1f %cB", b / Math.pow(1024, e), "KMGTPE".charAt(e-1));
    }
    public boolean wasCdSuccessful() { return cdSuccessful; }
    public String getCurrentPath() { return currentPath; }
    public void setCurrentPath(String path) { this.currentPath = path; }
}
