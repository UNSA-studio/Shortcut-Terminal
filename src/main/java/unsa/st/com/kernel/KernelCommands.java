package unsa.st.com.kernel;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * 内核命令集：从内核数据层读取的 Linux 风格命令实现。
 * 全部输出真实数据：MSPT/TPS、dmesg、线程表、模组清单、玩家会话详情。
 */
public final class KernelCommands {
    private KernelCommands() {}

    /** dmesg：内核环形日志。 */
    public static String dmesg(String[] args) {
        List<String> log = TerminalKernel.dmesgSnapshot();
        if (log.isEmpty()) return "dmesg: kernel log is empty (server may still be starting)";
        if (args.length > 0 && (args[0].equals("--follow") || args[0].equals("-f"))) {
            return String.join("\n", log) + "\n(--follow not supported; re-run dmesg to refresh)";
        }
        // 默认显示最近 40 行，--all 显示全部
        boolean all = args.length > 0 && (args[0].equals("--all") || args[0].equals("-a"));
        if (!all && log.size() > 40) log = log.subList(log.size() - 40, log.size());
        return String.join("\n", log);
    }

    /** tps：MSPT/TPS 性能面板。 */
    public static String tps() {
        double avg = TerminalKernel.averageMspt();
        double max = TerminalKernel.maxMspt();
        double tps = TerminalKernel.tps();
        String health = tps >= 19.5 ? "healthy" : tps >= 15.0 ? "degraded" : "lagging";
        return String.format(
                "TPS:      %.2f (target 20.00) [%s]\nMSPT avg: %.2f ms (target 50.00)\nMSPT max: %.2f ms (window 100)\nServers:  %s",
                tps, health, avg, max,
                TerminalKernel.server() != null ? "online" : "not running");
    }

    /** lsmod：已加载模组清单（FML 真实数据）。 */
    public static String lsmod() {
        List<TerminalKernel.ModRow> mods = TerminalKernel.loadedMods();
        if (mods.isEmpty()) return "No mods loaded (or ModList unavailable).";
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-30s %-28s %s\n", "MODID", "NAME", "VERSION"));
        sb.append("-".repeat(70)).append('\n');
        for (TerminalKernel.ModRow m : mods) {
            sb.append(String.format("%-30s %-28s %s\n", m.id(), clip(m.name(), 28), m.version()));
        }
        sb.append('\n').append(mods.size()).append(" mod(s) loaded");
        return sb.toString();
    }

    /** modinfo：单个模组详情。 */
    public static String modinfo(String[] args) {
        if (args.length == 0) return "Usage: modinfo <modid>";
        String id = args[0].toLowerCase();
        if (!TerminalKernel.isModLoaded(id)) return "modinfo: mod not loaded: " + id;
        for (TerminalKernel.ModRow m : TerminalKernel.loadedMods()) {
            if (m.id().equals(id)) {
                return String.format("modid:     %s\nname:      %s\nversion:   %s\nloaded:    yes\nsource:    FML ModList", m.id(), m.name(), m.version());
            }
        }
        return "modinfo: mod not loaded: " + id;
    }

    /** ps -e：内核进程表（线程 + 玩家进程化）。 */
    public static String psTop() {
        List<ProcessTable.Row> rows = ProcessTable.snapshot();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-10s %6s %8s %-12s %s\n", "USER", "PID", "CPU(s)", "STATE", "COMMAND"));
        sb.append("-".repeat(78)).append('\n');
        for (ProcessTable.Row r : rows) {
            sb.append(String.format("%-10s %6d %8.2f %-12s %s\n", r.user, r.pid, r.cpu, r.state, r.command));
        }
        return sb.toString();
    }

    /** w：详细用户会话（who 的增强版）。 */
    public static String w() {
        var server = TerminalKernel.server();
        if (server == null) return "No server running.";
        var players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) return "No users logged in.";
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-16s %-8s %6s %-22s %s\n", "USER", "PING", "MODE", "POSITION", "IDLE-TICKS"));
        sb.append("-".repeat(70)).append('\n');
        for (ServerPlayer p : players) {
            String mode = p.gameMode.getGameModeForPlayer().getName();
            sb.append(String.format("%-16s %5dms %-8s %-22s %d\n",
                    p.getGameProfile().getName(),
                    p.connection.latency(),
                    mode,
                    (int) p.getX() + "," + (int) p.getY() + "," + (int) p.getZ(),
                    p.getTickCount()));
        }
        return sb.toString();
    }

    /** free：内存详情（单位标准化）。 */
    public static String freeK() {
        Runtime rt = Runtime.getRuntime();
        long total = rt.totalMemory() / 1024, free = rt.freeMemory() / 1024, max = rt.maxMemory() / 1024;
        long used = total - free;
        return String.format(
                "              total        used        free\nMem:      %10d %10d %10d\nSwap:           0           0           0\n(max heap: %d kB)",
                total, used, free, max);
    }

    private static String clip(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}