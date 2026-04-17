package unsa.st.com.util;

import net.minecraft.server.level.ServerPlayer;
import unsa.st.com.filesystem.UserFileSystem;
import unsa.st.com.plugin.BinaryPluginManager;
import unsa.st.com.terminal.TerminalManager;
import unsa.st.com.terminal.TerminalSession;

import java.util.*;

public class CommandExecutor {
    private String currentPath = "";
    private boolean cdSuccessful = false;

    public String execute(ServerPlayer player, String command, String[] args, String sessionPath) {
        this.currentPath = sessionPath;
        this.cdSuccessful = false;
        UUID uuid = player.getUUID();
        boolean isOp = player.hasPermissions(2);

        switch (command.toLowerCase(Locale.ROOT)) {
            case "help": return getHelp();
            case "ls": return executeLs(uuid);
            case "mkdir": return executeMkdir(uuid, args);
            case "touch": return executeTouch(uuid, args);
            case "rm": return executeRm(uuid, args);
            case "cat": return executeCat(uuid, args);
            case "echo": return executeEcho(args);
            case "cd": return executeCd(uuid, args);
            case "pwd": return executePwd(uuid);
            case "whoami": return player.getName().getString();
            case "clear": return "";
            case "user": return isOp ? executeUserCommand(args) : "Error: Permission denied";
            case "refresh": return executeRefresh(args);
            default: return "Error: Unknown command. Type 'help' for available commands.";
        }
    }

    public static String executeFromGUI(ServerPlayer player, String input) {
        if (input.isBlank()) return "";
        String[] parts = input.trim().split("\\s+");
        String command = parts[0];
        String[] args = new String[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);
        
        CommandExecutor executor = new CommandExecutor();
        TerminalSession session = TerminalManager.getSession(player);
        if (session == null) {
            TerminalManager.enterTerminalMode(player);
            session = TerminalManager.getSession(player);
        }
        String sessionPath = session != null ? session.getCurrentPath() : "";
        String result = executor.execute(player, command, args, sessionPath);
        
        // 如果执行了 cd 并且成功，更新会话路径
        if (command.equalsIgnoreCase("cd") && executor.wasCdSuccessful()) {
            session.setCurrentPath(executor.getCurrentPath());
        }
        
        return result;
    }

    private String getHelp() {
        return """
               §a=== Shortcut Terminal Commands ===
               §fhelp §7- Show this help message
               §fls §7- List files in current directory
               §fmkdir <name> §7- Create a directory
               §ftouch <name> §7- Create a file
               §frm <name> §7- Remove a file or empty directory
               §fcat <name> §7- Display file contents
               §fcd <path> §7- Change directory
               §fpwd §7- Print working directory
               §fwhoami §7- Display your username
               §fclear §7- Clear the terminal screen
               §fuser <player> <action> §7- Admin player management
               §frefresh bf §7- Refresh binary plugins
               §a================================
               """;
    }

    private String executeLs(UUID uuid) {
        List<String> files = UserFileSystem.listDirectory(uuid, currentPath);
        if (files == null) return "Error: Directory not found";
        if (files.isEmpty()) return "(empty)";
        return String.join("  ", files);
    }

    private String executeMkdir(UUID uuid, String[] args) {
        if (args.length == 0) return "Error: mkdir: missing operand";
        String name = args[0];
        return UserFileSystem.createDirectory(uuid, currentPath, name) 
            ? "Directory created: " + name : "Error: Directory already exists or invalid path";
    }

    private String executeTouch(UUID uuid, String[] args) {
        if (args.length == 0) return "Error: touch: missing file operand";
        String name = args[0];
        return UserFileSystem.createFile(uuid, currentPath, name) 
            ? "File created: " + name : "Error: File already exists or invalid path";
    }

    private String executeRm(UUID uuid, String[] args) {
        if (args.length == 0) return "Error: rm: missing operand";
        return UserFileSystem.delete(uuid, currentPath, args[0]) 
            ? "Removed: " + args[0] : "Error: File/directory not found or not empty";
    }

    private String executeCat(UUID uuid, String[] args) {
        if (args.length == 0) return "Error: cat: missing file operand";
        String content = UserFileSystem.readFile(uuid, currentPath, args[0]);
        return content != null ? content : "Error: File not found";
    }

    private String executeEcho(String[] args) {
        return String.join(" ", args);
    }

    private String executeCd(UUID uuid, String[] args) {
        String targetPath = args.length == 0 ? "" : args[0];
        String newPath = UserFileSystem.normalizePath(currentPath, targetPath);
        if (!UserFileSystem.isPathValid(uuid, newPath)) {
            return "§cYou do not have permission to access this user's folder";
        }
        if (targetPath.isEmpty() || targetPath.equals(".") || UserFileSystem.directoryExists(uuid, currentPath, targetPath)) {
            this.currentPath = newPath;
            this.cdSuccessful = true;
            return "";
        }
        return "bash: cd: " + targetPath + ": No such file or directory";
    }

    private String executePwd(UUID uuid) {
        return "/" + uuid.toString() + (currentPath.isEmpty() ? "" : "/" + currentPath);
    }

    private String executeUserCommand(String[] args) {
        if (args.length < 2) return "Error: Usage: user <playername> <action> [params...]";
        return "User command executed. (Admin feature)";
    }

    private String executeRefresh(String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("bf")) {
            BinaryPluginManager.refreshPlugins();
            return "Binary plugins refreshed. Found " + BinaryPluginManager.getPluginCount() + " plugins.";
        }
        return "Usage: refresh bf";
    }

    public boolean wasCdSuccessful() { return cdSuccessful; }
    public String getCurrentPath() { return currentPath; }
}
