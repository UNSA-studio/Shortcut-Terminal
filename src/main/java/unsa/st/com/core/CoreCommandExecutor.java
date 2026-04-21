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

    public CoreCommandExecutor(boolean isClient) {
        this.isClient = isClient;
    }

    public void setPlayer(ServerPlayer player) {
        this.playerUuid = player.getUUID();
        this.playerName = player.getName().getString();
    }

    public void setPlayer(String playerName, String uuid) {
        this.playerName = playerName;
        this.playerUuid = UUID.fromString(uuid);
    }

    public String execute(String command, String[] args) {
        // 1. 尝试内置命令
        String builtInResult = executeBuiltInCommand(command, args);
        if (builtInResult != null && !builtInResult.startsWith("Error: Unknown command.")) {
            return builtInResult;
        }

        // 2. 尝试 PATH 外部命令
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
            case "dummymodule": return "Please use '/ST run dummymodule' in chat. Terminal cannot create fake players directly.";
            case "stop": return executeStop(args);
            default: return "Error: Unknown command. Type 'help' for available commands.";
        }
    }

    private Path findExecutableInPath(String command) {
        Path pathFile = PkgManager.getPathFile(isClient);
        if (!Files.exists(pathFile)) return null;
        try {
            List<String> paths = Files.readAllLines(pathFile);
            for (String dirStr : paths) {
                Path dir = Paths.get(dirStr);
                Path candidate = dir.resolve(command);
                if (Files.isExecutable(candidate)) return candidate;
                Path candidateExe = dir.resolve(command + ".exe");
                if (Files.isExecutable(candidateExe)) return candidateExe;
            }
        } catch (IOException e) {
            unsa.st.com.ShortcutTerminal.LOGGER.error("PATH read error", e);
        }
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
        } catch (Exception e) {
            return "Error: Execution failed - " + e.getMessage();
        }
        return output.toString().trim();
    }

    private String executeStop(String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("macro")) {
            if (isClient) {
                PlayerMacroManager.stopMacro();
                return "Macro stopped.";
            } else return "stop macro can only be used in terminal panel.";
        }
        return "Usage: stop macro";
    }

    private String getHelp() {
        return "Available: ls, mkdir, touch, rm, cat, echo, cd, pwd, cp, mv, head, tail, wc, grep, sort, uniq, whoami, uname, df, free, ps, du, ping, curl, wget, clear, date, which, chmod, sh, refresh, pkg, macro, stop macro";
    }

    private String executeLs() {
        List<String> files = isClient ?
                ClientVirtualFileSystem.listDirectory(currentPath) :
                UserFileSystem.listDirectory(playerUuid, currentPath);
        if (files == null) return "Error: Directory not found.";
        return String.join("  ", files);
    }

    private String executeMkdir(String[] args) {
        if (args.length == 0) return "Usage: mkdir <directory>";
        boolean ok = isClient ?
                ClientVirtualFileSystem.createDirectory(currentPath, args[0]) :
                UserFileSystem.createDirectory(playerUuid, currentPath, args[0]);
        return ok ? "Directory created." : "Error: Failed to create directory.";
    }

    private String executeTouch(String[] args) {
        if (args.length == 0) return "Usage: touch <file>";
        boolean ok = isClient ?
                ClientVirtualFileSystem.createFile(currentPath, args[0]) :
                UserFileSystem.createFile(playerUuid, currentPath, args[0]);
        return ok ? "File created." : "Error: Failed to create file.";
    }

    private String executeRm(String[] args) {
        if (args.length == 0) return "Usage: rm [-r] <name>";
        boolean recursive = false;
        String target;
        if (args[0].equals("-r")) {
            if (args.length < 2) return "Usage: rm [-r] <name>";
            recursive = true;
            target = args[1];
        } else {
            target = args[0];
        }
        boolean ok = isClient ?
                ClientVirtualFileSystem.delete(currentPath, target, recursive) :
                UserFileSystem.delete(playerUuid, currentPath, target, recursive);
        return ok ? "Deleted." : "Error: Failed to delete.";
    }

    private String executeCat(String[] args) {
        if (args.length == 0) return "Usage: cat <file>";
        String content = isClient ?
                ClientVirtualFileSystem.readFile(currentPath, args[0]) :
                UserFileSystem.readFile(playerUuid, currentPath, args[0]);
        return content != null ? content : "Error: File not found.";
    }

    private String executeEcho(String[] args) {
        return String.join(" ", args);
    }

    private String executeCd(String[] args) {
        if (args.length == 0) return "Usage: cd <path>";
        String target = args[0];
        String newPath = UserFileSystem.normalizePath(currentPath, target);
        List<String> test = isClient ?
                ClientVirtualFileSystem.listDirectory(newPath) :
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

    private String executePwd() {
        return currentPath.isEmpty() ? "/" : currentPath;
    }

    private String executeCp(String[] args) {
        if (args.length < 2) return "Usage: cp <source> <destination>";
        String content = isClient ?
                ClientVirtualFileSystem.readFile(currentPath, args[0]) :
                UserFileSystem.readFile(playerUuid, currentPath, args[0]);
        if (content == null) return "Error: Source file not found.";
        if (isClient) {
            ClientVirtualFileSystem.writeFile(currentPath, args[1], content);
        } else {
            UserFileSystem.writeFile(playerUuid, currentPath, args[1], content);
        }
        return "Copied.";
    }

    private String executeMv(String[] args) {
        if (args.length < 2) return "Usage: mv <source> <destination>";
        String content = isClient ?
                ClientVirtualFileSystem.readFile(currentPath, args[0]) :
                UserFileSystem.readFile(playerUuid, currentPath, args[0]);
        if (content == null) return "Error: Source file not found.";
        if (isClient) {
            ClientVirtualFileSystem.writeFile(currentPath, args[1], content);
            ClientVirtualFileSystem.delete(currentPath, args[0], false);
        } else {
            UserFileSystem.writeFile(playerUuid, currentPath, args[1], content);
            UserFileSystem.delete(playerUuid, currentPath, args[0], false);
        }
        return "Moved.";
    }

    private String executeHead(String[] args) {
        if (args.length == 0) return "Usage: head [-n N] <file>";
        int lines = 10;
        String file;
        if (args[0].equals("-n")) {
            if (args.length < 3) return "Usage: head [-n N] <file>";
            try { lines = Integer.parseInt(args[1]); } catch (NumberFormatException e) { return "Error: Invalid number."; }
            file = args[2];
        } else {
            file = args[0];
        }
        String content = isClient ?
                ClientVirtualFileSystem.readFile(currentPath, file) :
                UserFileSystem.readFile(playerUuid, currentPath, file);
        if (content == null) return "Error: File not found.";
        String[] allLines = content.split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(lines, allLines.length); i++) {
            sb.append(allLines[i]).append("\n");
        }
        return sb.toString().trim();
    }

    private String executeTail(String[] args) {
        if (args.length == 0) return "Usage: tail [-n N] <file>";
        int lines = 10;
        String file;
        if (args[0].equals("-n")) {
            if (args.length < 3) return "Usage: tail [-n N] <file>";
            try { lines = Integer.parseInt(args[1]); } catch (NumberFormatException e) { return "Error: Invalid number."; }
            file = args[2];
        } else {
            file = args[0];
        }
        String content = isClient ?
                ClientVirtualFileSystem.readFile(currentPath, file) :
                UserFileSystem.readFile(playerUuid, currentPath, file);
        if (content == null) return "Error: File not found.";
        String[] allLines = content.split("\n");
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, allLines.length - lines);
        for (int i = start; i < allLines.length; i++) {
            sb.append(allLines[i]).append("\n");
        }
        return sb.toString().trim();
    }

    private String executeWc(String[] args) {
        if (args.length == 0) return "Usage: wc <file>";
        String content = isClient ?
                ClientVirtualFileSystem.readFile(currentPath, args[0]) :
                UserFileSystem.readFile(playerUuid, currentPath, args[0]);
        if (content == null) return "Error: File not found.";
        int lines = content.split("\n").length;
        int words = content.split("\\s+").length;
        int chars = content.length();
        return String.format("%d %d %d %s", lines, words, chars, args[0]);
    }

    private String executeGrep(String[] args) {
        if (args.length < 2) return "Usage: grep <pattern> <file>";
        String pattern = args[0];
        String file = args[1];
        String content = isClient ?
                ClientVirtualFileSystem.readFile(currentPath, file) :
                UserFileSystem.readFile(playerUuid, currentPath, file);
        if (content == null) return "Error: File not found.";
        StringBuilder sb = new StringBuilder();
        for (String line : content.split("\n")) {
            if (line.contains(pattern)) sb.append(line).append("\n");
        }
        return sb.toString().trim();
    }

    private String executeSort(String[] args) {
        if (args.length == 0) return "Usage: sort <file>";
        String content = isClient ?
                ClientVirtualFileSystem.readFile(currentPath, args[0]) :
                UserFileSystem.readFile(playerUuid, currentPath, args[0]);
        if (content == null) return "Error: File not found.";
        List<String> lines = new ArrayList<>(Arrays.asList(content.split("\n")));
        Collections.sort(lines);
        return String.join("\n", lines);
    }

    private String executeUniq(String[] args) {
        if (args.length == 0) return "Usage: uniq <file>";
        String content = isClient ?
                ClientVirtualFileSystem.readFile(currentPath, args[0]) :
                UserFileSystem.readFile(playerUuid, currentPath, args[0]);
        if (content == null) return "Error: File not found.";
        List<String> lines = Arrays.asList(content.split("\n"));
        StringBuilder sb = new StringBuilder();
        String prev = null;
        for (String line : lines) {
            if (!line.equals(prev)) {
                sb.append(line).append("\n");
                prev = line;
            }
        }
        return sb.toString().trim();
    }

    private String executeUname(String[] args) {
        return System.getProperty("os.name") + " " + System.getProperty("os.arch");
    }

    private String executeDf(String[] args) {
        Path root = Paths.get(".");
        try {
            FileStore store = Files.getFileStore(root);
            long total = store.getTotalSpace() / (1024*1024);
            long used = (store.getTotalSpace() - store.getUsableSpace()) / (1024*1024);
            long avail = store.getUsableSpace() / (1024*1024);
            return String.format("Filesystem     1M-blocks  Used Available Use%% Mounted on\n%s  %d  %d  %d  %d%% /",
                    store.name(), total, used, avail, total > 0 ? (used*100/total) : 0);
        } catch (IOException e) {
            return "Error: Unable to get disk usage.";
        }
    }

    private String executeFree(String[] args) {
        Runtime rt = Runtime.getRuntime();
        long total = rt.totalMemory() / (1024*1024);
        long free = rt.freeMemory() / (1024*1024);
        long used = total - free;
        return String.format("              total        used        free      shared  buff/cache   available\nMem:           %d          %d          %d           0           0           0",
                total, used, free);
    }

    private String executePs(String[] args) {
        return "PID TTY          TIME CMD\n  1 ?        00:00:00 java\n  2 ?        00:00:00 Minecraft";
    }

    private String executeDu(String[] args) {
        if (args.length == 0) return "Usage: du <file/directory>";
        Path p = isClient ? Paths.get("client_fs", currentPath, args[0]) : UserFileSystem.getUserPath(playerUuid).resolve(currentPath).resolve(args[0]);
        try {
            if (Files.isDirectory(p)) {
                final long[] size = {0};
                Files.walkFileTree(p, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        size[0] += attrs.size();
                        return FileVisitResult.CONTINUE;
                    }
                });
                return size[0] / 1024 + "K\t" + args[0];
            } else {
                return Files.size(p) / 1024 + "K\t" + args[0];
            }
        } catch (IOException e) {
            return "Error: Unable to get size.";
        }
    }

    private String executePing(String[] args) {
        if (args.length == 0) return "Usage: ping <host>";
        try {
            InetAddress address = InetAddress.getByName(args[0]);
            long start = System.currentTimeMillis();
            boolean reachable = address.isReachable(3000);
            long time = System.currentTimeMillis() - start;
            if (reachable) {
                return "Reply from " + address.getHostAddress() + ": time=" + time + "ms";
            } else {
                return "Request timed out.";
            }
        } catch (IOException e) {
            return "Error: Unknown host.";
        }
    }

    private String executeCurl(String[] args) {
        if (args.length == 0) return "Usage: curl <url>";
        return fetchUrl(args[0]);
    }

    private String executeWget(String[] args) {
        if (args.length < 2) return "Usage: wget <url> <output_file>";
        String content = fetchUrl(args[0]);
        if (content.startsWith("Error:")) return content;
        if (isClient) {
            ClientVirtualFileSystem.writeFile(currentPath, args[1], content);
        } else {
            UserFileSystem.writeFile(playerUuid, currentPath, args[1], content);
        }
        return "Downloaded to " + args[1];
    }

    private String fetchUrl(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                content.append(line).append("\n");
            }
            in.close();
            conn.disconnect();
            return content.toString().trim();
        } catch (Exception e) {
            return "Error: Failed to fetch URL.";
        }
    }

    private String executeWhich(String[] args) {
        if (args.length == 0) return "Usage: which <command>";
        Path found = findExecutableInPath(args[0]);
        if (found != null) return found.toString();
        String builtIn = executeBuiltInCommand(args[0], new String[0]);
        if (!builtIn.startsWith("Error: Unknown command.")) {
            return args[0] + ": shell built-in command";
        }
        return args[0] + " not found";
    }

    private String executeChmod(String[] args) {
        if (args.length < 2) return "Usage: chmod <mode> <file>";
        if (args[0].equals("+x")) {
            Path p = isClient ? Paths.get("client_fs", currentPath, args[1]) : UserFileSystem.getUserPath(playerUuid).resolve(currentPath).resolve(args[1]);
            if (Files.exists(p)) {
                p.toFile().setExecutable(true);
                return "Added execute permission.";
            }
            return "Error: File not found.";
        }
        return "Error: Only +x is supported.";
    }

    private String executeSh(String[] args) {
        if (args.length == 0) return "Usage: sh <script>";
        String content = isClient ?
                ClientVirtualFileSystem.readFile(currentPath, args[0]) :
                UserFileSystem.readFile(playerUuid, currentPath, args[0]);
        if (content == null) return "Error: Script not found.";
        StringBuilder output = new StringBuilder();
        for (String line : content.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split("\\s+");
            String cmd = parts[0];
            String[] cmdArgs = Arrays.copyOfRange(parts, 1, parts.length);
            output.append("> ").append(line).append("\n");
            output.append(execute(cmd, cmdArgs)).append("\n");
        }
        return output.toString().trim();
    }

    private String executeRefresh(String[] args) {
        if (args.length == 0) return "Usage: refresh <plugin|bf>";
        if (args[0].equalsIgnoreCase("plugin")) {
            BinaryPluginManager.refreshPlugins();
            return "Plugins refreshed. Available: " + BinaryPluginManager.getAvailablePlugins();
        } else if (args[0].equalsIgnoreCase("bf")) {
            return "BF refreshed. (Not implemented)";
        }
        return "Usage: refresh <plugin|bf>";
    }

    private String executePkg(String[] args) {
        if (args.length == 0) return PkgManager.getHelp();
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "update": return PkgManager.updateIndex();
            case "search": return args.length > 1 ? PkgManager.search(args[1]) : "Usage: pkg search <keyword>";
            case "install": return args.length > 1 ? PkgManager.install(args[1], isClient) : "Usage: pkg install <package>";
            case "remove": return args.length > 1 ? PkgManager.remove(args[1], isClient) : "Usage: pkg remove <package>";
            case "list": return PkgManager.listInstalled(isClient);
            case "info": return args.length > 1 ? PkgManager.info(args[1]) : "Usage: pkg info <package>";
            case "upgrade": return PkgManager.upgrade(isClient);
            case "clean": return PkgManager.clean(isClient);
            default: return "Unknown pkg command. Available: update, search, install, remove, list, info, upgrade, clean";
        }
    }

    private String executeMacro(String[] args) {
        if (!isClient) return "macro can only be used in terminal panel.";
        if (args.length == 0) return "Usage: macro start <operate> [interval_ms]";
        if (args[0].equalsIgnoreCase("start")) {
            if (args.length < 2) return "Usage: macro start <operate> [interval_ms]";
            String operate = args[1];
            long interval = 3000;
            if (args.length > 2) {
                try { interval = Long.parseLong(args[2]); } catch (NumberFormatException e) { return "Error: Invalid interval."; }
            }
            PlayerMacroManager.startMacro(operate, interval);
            return "Macro started.";
        }
        return "Usage: macro start <operate> [interval_ms]";
    }
}
