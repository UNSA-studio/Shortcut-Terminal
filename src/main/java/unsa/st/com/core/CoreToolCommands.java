package unsa.st.com.core;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import unsa.st.com.api.ShortcutTerminalAPI;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.List;
import java.util.Map;

/**
 * 终端内置工具命令集（Linux 风格补充）。
 * 方法由 CoreCommandExecutor.executeBuiltInCommand 调度。
 * 数据口径：uptime/who/hostname 基于服务端真实状态；
 * lscpu/top 为 JVM 层面近似值（模组沙箱无法读取宿主机 CPU 实际负载）。
 * 全部 MinecraftServer 方法名已对照 Mojang 1.21.1 官方映射验证。
 */
public final class CoreToolCommands {
    private CoreToolCommands() {}

    /** uptime：服务器运行时长 + 在线玩家数 */
    public static String uptime() {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        long ticks = s != null ? s.getTickCount() : 0;
        long sec = ticks / 20;
        long h = sec / 3600, m = (sec % 3600) / 60, secRem = sec % 60;
        int online = s != null ? s.getPlayerCount() : 0;
        return String.format(" up %02d:%02d:%02d,  %d user(s) online", h, m, secRem, online);
    }

    /** who：当前登录的玩家（真实在线列表） */
    public static String who() {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        if (s == null) return "No server running.";
        List<ServerPlayer> players = s.getPlayerList().getPlayers();
        if (players.isEmpty()) return "No users logged in.";
        StringBuilder sb = new StringBuilder("USER             LINE\n");
        for (ServerPlayer p : players) {
            sb.append(String.format("%-16s %-8s\n", p.getName().getString(), "mc-" + p.getUUID().toString().substring(0, 8)));
        }
        return sb.toString().trim();
    }

    /** env：JVM 关键环境变量 */
    public static String env() {
        StringBuilder sb = new StringBuilder();
        String[] keys = {"os.name", "os.arch", "os.version", "java.version", "java.vendor", "java.home", "user.dir", "user.name", "file.encoding"};
        for (String k : keys) {
            String v = System.getProperty(k);
            if (v != null) sb.append(k).append('=').append(v).append('\n');
        }
        return sb.toString().trim();
    }

    /** hostname：服务器地址与端口 */
    public static String hostname() {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        if (s == null) return "(no server)";
        return String.format("%s:%d", s.getMotd(), s.getPort());
    }

    /** lscpu：CPU 核心数与架构（JVM 视角） */
    public static String lscpu() {
        Runtime rt = Runtime.getRuntime();
        return String.format("Architecture:        %s\nCPU(s):              %d\nThread(s) per core:  JVM-managed\nModel name:          %s",
                System.getProperty("os.arch", "unknown"),
                rt.availableProcessors(),
                System.getProperty("os.name", "unknown"));
    }

    /** top：JVM 内存与线程概览（非宿主机进程表） */
    public static String top() {
        Runtime rt = Runtime.getRuntime();
        long total = rt.totalMemory(), free = rt.freeMemory(), max = rt.maxMemory();
        long used = total - free;
        ThreadMXBean tm = ManagementFactory.getThreadMXBean();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Tasks: %d thread(s) registered\n", tm.getThreadCount()));
        sb.append(String.format("%%Cpu(s): JVM heap %.1f%% of %s\n", total > 0 ? used * 100.0 / max : 0, fmt(max)));
        sb.append(String.format("MiB Mem : %s total, %s free, %s used\n", fmt(total), fmt(free), fmt(used)));
        sb.append("  (JVM-level view; host process table unavailable in mod sandbox)");
        return sb.toString();
    }

    private static String fmt(long b) {
        if (b < 1024) return b + "B";
        if (b < 1048576) return String.format("%.1fK", b / 1024.0);
        if (b < 1073741824) return String.format("%.1fM", b / 1048576.0);
        return String.format("%.1fG", b / 1073741824.0);
    }

    /** addons：列出所有附属注册的命令与模块（附属开发门户入口） */
    public static String addons() {
        Map<String, String> cmds = ShortcutTerminalAPI.commandInfoSnapshot();
        Map<String, String> mods = ShortcutTerminalAPI.moduleInfoSnapshot();
        if (cmds.isEmpty() && mods.isEmpty()) {
            return "No addon commands or modules registered.\n"
                 + "Addon developers: call ShortcutTerminalAPI.registerCommand(...) in FMLCommonSetupEvent.";
        }
        StringBuilder sb = new StringBuilder();
        if (!cmds.isEmpty()) {
            sb.append("Addon commands:\n");
            cmds.forEach((k, v) -> sb.append("  ").append(k).append(v.isEmpty() ? "" : " - " + v).append('\n'));
        }
        if (!mods.isEmpty()) {
            sb.append("Addon modules (use 'run <module>'):\n");
            mods.forEach((k, v) -> sb.append("  ").append(k).append(v.isEmpty() ? "" : " - " + v).append('\n'));
        }
        return sb.toString().trim();
    }
}