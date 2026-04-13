package unsa.st.com.util;

import net.minecraft.server.level.ServerPlayer;
import unsa.st.com.filesystem.UserFileSystem;
import java.util.*;

public class CommandExecutor {
    private String currentPath = "";
    private boolean cdSuccessful = false;

    public String execute(ServerPlayer player, String command, String[] args, String sessionPath) {
        this.currentPath = sessionPath;
        this.cdSuccessful = false;
        UUID uuid = player.getUUID();
        boolean isOp = player.hasPermissions(2);

        switch (command.toLowerCase()) {
            case "help": return getHelp();
            case "ls": return executeLs(uuid, args);
            case "mkdir": return executeMkdir(uuid, args);
            case "touch": return executeTouch(uuid, args);
            case "rm": return executeRm(uuid, args);
            case "cat": return executeCat(uuid, args);
            case "echo": return executeEcho(args);
            case "cd": return executeCd(uuid, args);
            case "pwd": return executePwd(uuid);
            case "whoami": return player.getName().getString();
            case "clear": return "\n".repeat(20);
            case "user": return isOp ? executeUserCommand(args) : "Error: Permission denied";
            default: return "Error: Unknown command. Type 'help' for available commands.";
        }
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
               §fecho <text> > <file> §7- Write text to file
               §fcd <path> §7- Change directory
               §fpwd §7- Print working directory
               §fwhoami §7- Display your username
               §fclear §7- Clear the terminal screen
               §fuser <player> <action> §7- Admin player management
               §fexit §7- Exit terminal mode
               §a================================
               """;
    }

    private String executeLs(UUID uuid, String[] args) {
        List<String> files = UserFileSystem.listDirectory(uuid, currentPath);
        if (files == null) return "Error: Directory not found";
        if (files.isEmpty()) return "(empty)";
        return String.join("  ", files);
    }

    private String executeMkdir(UUID uuid, String[] args) {
        if (args.length == 0) return "Error: mkdir: missing operand";
        String name = args[0];
        if (name.contains("..") || name.contains("/") || name.contains("\\")) 
            return "Error: Invalid directory name";
        return UserFileSystem.createDirectory(uuid, currentPath, name) 
            ? "Directory created: " + name : "Error: Directory already exists or invalid path";
    }

    private String executeTouch(UUID uuid, String[] args) {
        if (args.length == 0) return "Error: touch: missing file operand";
        String name = args[0];
        if (name.contains("..") || name.contains("/") || name.contains("\\")) 
            return "Error: Invalid file name";
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
        if (targetPath.startsWith("..") || targetPath.contains("../")) {
            String newPath = UserFileSystem.normalizePath(currentPath, targetPath);
            if (UserFileSystem.isPathValid(uuid, newPath)) {
                this.currentPath = newPath;
                this.cdSuccessful = true;
                return "Changed directory to: " + (currentPath.isEmpty() ? "/" : currentPath);
            } else {
                return "§cYou do not have permission to access this user's folder";
            }
        }
        if (!targetPath.isEmpty() && !targetPath.equals(".") && !targetPath.equals("..")) {
            String newPath = UserFileSystem.normalizePath(currentPath, targetPath);
            if (!UserFileSystem.isPathValid(uuid, newPath)) {
                return "§cYou do not have permission to access this user's folder";
            }
        }
        if (UserFileSystem.directoryExists(uuid, currentPath, targetPath)) {
            String newPath = UserFileSystem.normalizePath(currentPath, targetPath);
            this.currentPath = newPath;
            this.cdSuccessful = true;
            return "Changed directory to: " + (currentPath.isEmpty() ? "/" : currentPath);
        }
        return "Error: Directory not found: " + targetPath;
    }

    private String executePwd(UUID uuid) {
        return "/" + uuid.toString() + (currentPath.isEmpty() ? "" : "/" + currentPath);
    }

    private String executeUserCommand(String[] args) {
        if (args.length < 2) return "Error: Usage: user <playername> <action> [params...]";
        return "User command executed. (Admin feature)";
    }

    public boolean wasCdSuccessful() { return cdSuccessful; }
    public String getCurrentPath() { return currentPath; }
}
