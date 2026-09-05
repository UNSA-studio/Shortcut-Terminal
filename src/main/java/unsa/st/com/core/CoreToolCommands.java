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

    /** date：服务器世界内日期与实际时间 */
    public static String date() {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        StringBuilder sb = new StringBuilder(new java.util.Date().toString());
        if (s != null) {
            var ow = s.overworld();
            if (ow != null) {
                long dayTime = ow.getDayTime();
                long day = dayTime / 24000L + 1;
                long t = dayTime % 24000L;
                long mcH = (t / 1000 + 6) % 24;
                long mcM = (t % 1000) * 60 / 1000;
                sb.append(String.format("\nWorld day %d, %02d:%02d (MC time)", day, mcH, mcM));
                sb.append(ow.isRaining() ? "\nWeather: raining" : "\nWeather: clear");
            }
        }
        return sb.toString();
    }

    /** hostname：详细系统信息 */
    public static String unameAll() {
        return String.format("%s %s %s %s %s",
                System.getProperty("os.name", "?"),
                System.getProperty("os.arch", "?"),
                System.getProperty("os.version", "?"),
                "MC", System.getProperty("java.version", "?"));
    }

    /** init：系统启动时间与版本横幅 */
    public static String initInfo() {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        long upMs = ManagementFactory.getRuntimeMXBean().getUptime();
        long sec = upMs / 1000;
        return String.format("JVM uptime: %dd %dh %dm %ds\nServer: %s",
                sec / 86400, (sec % 86400) / 3600, (sec % 3600) / 60, sec % 60,
                s != null ? s.getServerModName() : "unknown");
    }

    /** kill：终止（占位：显示如何正确停止服务器） */
    public static String killInfo(String[] args) {
        if (args.length == 0) return "Usage: kill <pid>  (server-side, use /stop to shut down the server)";
        return "kill: only the dedicated server console may terminate the JVM. Use /stop instead.";
    }

    /** sleep：批处理脚本中的等待命令 */
    public static String sleep(String[] args) {
        if (args.length == 0) return "Usage: sleep <seconds>";
        try {
            long ms = (long) (Double.parseDouble(args[0]) * 1000);
            Thread.sleep(Math.min(ms, 10000)); // max 10s in scripts to avoid runaway scripts
            return "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "sleep interrupted";
        } catch (NumberFormatException e) {
            return "sleep: invalid number: " + args[0];
        }
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

    /** 内核层 top 已由 KernelCommands.psTop 替代：这里委托 kernel 实现（供 GUI 客户端复用）。 */
    public static String top() {
        return unsa.st.com.kernel.KernelCommands.psTop();
    }
}