package unsa.st.com.core;

import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import unsa.st.com.client.ClientVirtualFileSystem;
import unsa.st.com.filesystem.UserFileSystem;
import unsa.st.com.pkg.PkgManager;
import unsa.st.com.plugin.BinaryPluginManager;

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
               §fpkg update §7- Update package index
               §fpkg install <pkg> §7- Install a package
               §fpkg remove <pkg> §7- Remove a package
               §fpkg list §7- List installed packages
               §fpkg search <kw> §7- Search packages
               §fpkg info <pkg> §7- Show package info
               §fpkg path §7- Show current PATH
               §a================================
               """;
    }

    private String executeLs() {
        List<String> files;
        if (isClient) {
            files = ClientVirtualFileSystem.listDirectory(playerUuid.toString(), currentPath);
        } else {
            files = UserFileSystem.listDirectory(playerUuid, currentPath);
        }
        if (files == null) return "Error: Directory not found";
        if (files.isEmpty()) return "(empty)";
        return String.join("  ", files);
    }

    private String executeMkdir(String[] args) {
        if (args.length == 0) return "Error: mkdir: missing operand";
        String name = args[0];
        boolean success;
        if (isClient) {
            success = ClientVirtualFileSystem.createDirectory(playerUuid.toString(), currentPath, name);
        } else {
            success = UserFileSystem.createDirectory(playerUuid, currentPath, name);
        }
        return success ? "Directory created: " + name : "Error: Directory already exists or invalid path";
    }

    private String executeTouch(String[] args) {
        if (args.length == 0) return "Error: touch: missing file operand";
        String name = args[0];
        boolean success;
        if (isClient) {
            success = ClientVirtualFileSystem.createFile(playerUuid.toString(), currentPath, name);
        } else {
            success = UserFileSystem.createFile(playerUuid, currentPath, name);
        }
        return success ? "File created: " + name : "Error: File already exists or invalid path";
    }

    private String executeRm(String[] args) {
        if (args.length == 0) return "Error: rm: missing operand";
        boolean success;
        if (isClient) {
            success = ClientVirtualFileSystem.delete(playerUuid.toString(), currentPath, args[0]);
        } else {
            success = UserFileSystem.delete(playerUuid, currentPath, args[0]);
        }
        return success ? "Removed: " + args[0] : "Error: File/directory not found or not empty";
    }

    private String executeCat(String[] args) {
        if (args.length == 0) return "Error: cat: missing file operand";
        String content;
        if (isClient) {
            content = ClientVirtualFileSystem.readFile(playerUuid.toString(), currentPath, args[0]);
        } else {
            content = UserFileSystem.readFile(playerUuid, currentPath, args[0]);
        }
        return content != null ? content : "Error: File not found";
    }

    private String executeEcho(String[] args) {
        String full = String.join(" ", args);
        int gtIndex = full.indexOf('>');
        if (gtIndex == -1) return full;
        String text = full.substring(0, gtIndex).trim();
        String filename = full.substring(gtIndex + 1).trim();
        if (filename.isEmpty()) return "Error: missing file name after >";
        if (isClient) {
            ClientVirtualFileSystem.writeFile(playerUuid.toString(), currentPath, filename, text);
        } else {
            UserFileSystem.writeFile(playerUuid, currentPath, filename, text);
        }
        return "";
    }

    private String executeCd(String[] args) {
        String target = args.length == 0 ? "" : args[0];
        String newPath;
        if (isClient) {
            newPath = ClientVirtualFileSystem.normalizePath(currentPath, target);
            if (ClientVirtualFileSystem.directoryExists(playerUuid.toString(), newPath)) {
                this.currentPath = newPath;
                this.cdSuccessful = true;
                return "";
            }
        } else {
            newPath = UserFileSystem.normalizePath(currentPath, target);
            if (UserFileSystem.directoryExists(playerUuid, currentPath, target)) {
                this.currentPath = newPath;
                this.cdSuccessful = true;
                return "";
            }
        }
        return "bash: cd: " + target + ": No such file or directory";
    }

    private String executePwd() {
        if (isClient) {
            return currentPath;
        } else {
            return "/" + playerUuid.toString() + (currentPath.equals("/") ? "" : currentPath);
        }
    }

    private String executeRefresh(String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("bf")) {
            BinaryPluginManager.refreshPlugins();
            return "Binary plugins refreshed. Found " + BinaryPluginManager.getPluginCount() + " plugins.";
        }
        return "Usage: refresh bf";
    }

    private String executePkg(String[] args) {
        if (args.length == 0) return "Usage: pkg <update|install|remove|list|search|info|path>";
        switch (args[0].toLowerCase()) {
            case "update": return PkgManager.updateIndex();
            case "install":
                if (args.length < 2) return "Usage: pkg install <package>";
                return PkgManager.install(args[1], isClient);
            case "remove":
                if (args.length < 2) return "Usage: pkg remove <package>";
                return PkgManager.remove(args[1], isClient);
            case "list": return PkgManager.listInstalled(isClient);
            case "search":
                if (args.length < 2) return "Usage: pkg search <keyword>";
                return PkgManager.search(args[1]);
            case "info":
                if (args.length < 2) return "Usage: pkg info <package>";
                return PkgManager.showInfo(args[1]);
            case "path": return PkgManager.getPathEntries(isClient);
            default: return "Unknown pkg subcommand: " + args[0];
        }
    }

    public boolean wasCdSuccessful() { return cdSuccessful; }
    public String getCurrentPath() { return currentPath; }
    public void setCurrentPath(String path) { this.currentPath = path; }
}
