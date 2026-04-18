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
            case "stop": if (args.length > 0 && args[0].equalsIgnoreCase("macro")) { PlayerMacroManager.stopMacro(); return "Macro stopped."; } return "Usage: stop macro";
            case "pkg": return executePkg(args);
            case "macro": return executeMacro(args);
            case "stop": 
                if (args.length > 0 && args[0].equalsIgnoreCase("macro")) {
                    PlayerMacroManager.stopMacro();
                    return "Macro stopped.";
                }
                return "Usage: stop macro";
            default: return "Error: Unknown command. Type 'help' for available commands.";
        }
    }

    private String getHelp() { return "Available: ls, mkdir, touch, rm, cat, echo, cd, pwd, cp, mv, head, tail, wc, grep, sort, uniq, whoami, uname, df, free, ps, du, ping, curl, wget, clear, date, which, chmod, sh, refresh, pkg, macro, stop macro"; }

    // ... 所有原有的 execute 方法保持不变 (为节省篇幅，这里省略，实际应保留全部已有实现) ...
    // 由于篇幅限制，我将只列出 executeMacro 和原有的框架，你需要确保原有方法完整。
    // 你可以在本地复制完整的 CoreCommandExecutor，只需在末尾添加 executeMacro 方法。
    // 为了简洁，此处仅提供新增和修改的部分，完整文件见后续说明。

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

    // 保留原有其他方法...
    // 注意：原 CoreCommandExecutor 文件非常长，请确保将所有原有方法复制进来，不要遗漏。
    // 由于此处无法展示完整文件，建议你在本地编辑器中将原有所有方法粘贴到上述类体中。
    // 下面是一个简短的占位，提醒你合并。
    // ========== 原有方法请务必保留 ==========
    private String executeLs() { return ""; } // 替换为实际代码
    // ... 其他所有方法 ...
}
