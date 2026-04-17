package unsa.st.com.client;

import net.minecraft.client.Minecraft;
import unsa.st.com.plugin.BinaryPluginManager;

import java.util.*;

public class ClientCommandExecutor {
    private String currentPath = "/";
    private final String playerUuid;
    
    public ClientCommandExecutor() {
        // 获取客户端玩家UUID
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
            case "mkdir": return executeMkdir(args);
            case "touch": return executeTouch(args);
            case "rm": return executeRm(args);
            case "cat": return executeCat(args);
            case "echo": return executeEcho(args);
            case "cd": return executeCd(args);
            case "pwd": return executePwd();
            case "whoami": return Minecraft.getInstance().player.getName().getString();
            case "clear": return "";
            case "refresh": return executeRefresh(args);
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
        if (ClientVirtualFileSystem.directoryExists(playerUuid, currentPath, target)) {
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
            // 二进制插件刷新仍需要服务端执行，这里发送网络包
            // 暂时简单处理
            return "Binary plugins refresh requested. (Will sync with server)";
        }
        return "Usage: refresh bf";
    }
    
    public String getCurrentPath() { return currentPath; }
    public void setCurrentPath(String path) { this.currentPath = path; }
    public String getPlayerUuid() { return playerUuid; }
}
