package unsa.st.com.client;

import net.minecraft.client.Minecraft;
import unsa.st.com.pkg.PackageManager;

import java.util.*;

public class ClientCommandExecutor {
    private String currentPath = "/";
    private final String playerName;
    private final String playerUuid;
    private List<String> commandHistory = new ArrayList<>();
    private boolean pendingChanges = false;
    
    public ClientCommandExecutor() {
        this(Minecraft.getInstance().player.getName().getString());
    }
    
    public ClientCommandExecutor(String playerName) {
        this.playerName = playerName;
        if (Minecraft.getInstance().player != null) {
            this.playerUuid = Minecraft.getInstance().player.getUUID().toString();
        } else {
            this.playerUuid = "local-player";
        }
    }
    
    public String execute(String command, String[] args) {
        switch (command.toLowerCase(Locale.ROOT)) {
            case "help": return getHelp();
            case "ls": return executeLs();
            case "mkdir": pendingChanges = true; return executeMkdir(args);
            case "touch": pendingChanges = true; return executeTouch(args);
            case "rm": pendingChanges = true; return executeRm(args);
            case "cat": return executeCat(args);
            case "echo": pendingChanges = true; return executeEcho(args);
            case "cd": return executeCd(args);
            case "pwd": return executePwd();
            case "whoami": return playerName;
            case "clear": return "";
            case "refresh": return executeRefresh(args);
            case "pkg": return executePkg(args);
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
               §frefresh bf §7- Refresh binary plugins
               §fpkg install <pkg> §7- Install a package
               §fpkg remove <pkg> §7- Remove a package
               §fpkg list §7- List installed packages
               §fpkg available §7- List available packages
               §fpkg path §7- Show current PATH
               §frun synchrony -server §7- Sync from server
               §frun synchrony -local §7- Sync to server
               §a================================
               """;
    }
    
    private String executeLs() {
        List<String> files = ClientVirtualFileSystem.listDirectory(playerUuid, currentPath);
        if (files.isEmpty()) return "(empty)";
        return String.join("  ", files);
    }
    
    private String executeMkdir(String[] args) {
        if (args.length == 0) return "Error: mkdir: missing operand";
        String name = args[0];
        boolean success = ClientVirtualFileSystem.createDirectory(playerUuid, currentPath, name);
        return success ? "Directory created: " + name : "Error: Directory already exists";
    }
    
    private String executeTouch(String[] args) {
        if (args.length == 0) return "Error: touch: missing file operand";
        String name = args[0];
        boolean success = ClientVirtualFileSystem.createFile(playerUuid, currentPath, name);
        return success ? "File created: " + name : "Error: File already exists";
    }
    
    private String executeRm(String[] args) {
        if (args.length == 0) return "Error: rm: missing operand";
        boolean success = ClientVirtualFileSystem.delete(playerUuid, currentPath, args[0]);
        return success ? "Removed: " + args[0] : "Error: File/directory not found or not empty";
    }
    
    private String executeCat(String[] args) {
        if (args.length == 0) return "Error: cat: missing file operand";
        String content = ClientVirtualFileSystem.readFile(playerUuid, currentPath, args[0]);
        return content != null ? content : "Error: File not found";
    }
    
    private String executeEcho(String[] args) {
        String full = String.join(" ", args);
        int gtIndex = full.indexOf('>');
        if (gtIndex == -1) {
            return full;
        }
        String text = full.substring(0, gtIndex).trim();
        String filename = full.substring(gtIndex + 1).trim();
        if (filename.isEmpty()) return "Error: missing file name after >";
        ClientVirtualFileSystem.writeFile(playerUuid, currentPath, filename, text);
        return "";
    }
    
    private String executeCd(String[] args) {
        String target = args.length == 0 ? "" : args[0];
        String newPath = ClientVirtualFileSystem.normalizePath(currentPath, target);
        if (ClientVirtualFileSystem.directoryExists(playerUuid, newPath)) {
            this.currentPath = newPath;
            return "";
        }
        return "bash: cd: " + target + ": No such file or directory";
    }
    
    private String executePwd() {
        return currentPath;
    }
    
    private String executeRefresh(String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("bf")) {
            return "Binary plugins refresh requested. (Will sync with server)";
        }
        return "Usage: refresh bf";
    }
    
    private String executePkg(String[] args) {
        if (args.length == 0) {
            return "Usage: pkg <install|remove|list|available|path>";
        }
        switch (args[0].toLowerCase()) {
            case "install":
                if (args.length < 2) return "Usage: pkg install <package>";
                boolean installed = PackageManager.installPackage(args[1]);
                return installed ? "Package installed: " + args[1] : "Failed to install: " + args[1];
            case "remove":
                if (args.length < 2) return "Usage: pkg remove <package>";
                boolean removed = PackageManager.uninstallPackage(args[1]);
                return removed ? "Package removed: " + args[1] : "Failed to remove: " + args[1];
            case "list":
                List<String> list = PackageManager.listInstalledPackages();
                return list.isEmpty() ? "No packages installed." : "Installed:\n" + String.join("\n", list);
            case "available":
                List<String> avail = PackageManager.listAvailablePackages();
                return avail.isEmpty() ? "No packages available." : "Available:\n" + String.join("\n", avail);
            case "path":
                List<String> path = PackageManager.getPathEntries();
                return "PATH:\n" + String.join("\n", path);
            default:
                return "Unknown pkg subcommand: " + args[0];
        }
    }
    
    public String getCurrentPath() { return currentPath; }
    public void setCurrentPath(String path) { this.currentPath = path; }
    public String getPlayerName() { return playerName; }
    public String getPlayerUuid() { return playerUuid; }
    public List<String> getCommandHistory() { return commandHistory; }
    public void setCommandHistory(List<String> history) { this.commandHistory = new ArrayList<>(history); }
    public void addCommandToHistory(String command) { this.commandHistory.add(command); }
    public boolean hasPendingChanges() { return pendingChanges; }
    public void clearPendingChanges() { pendingChanges = false; }
}
