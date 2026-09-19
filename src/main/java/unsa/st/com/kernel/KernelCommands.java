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
            sb.append(String.format("%-16s %5dms %-8s %-22s\n",
                    p.getGameProfile().getName(),
                    p.connection.latency(),
                    mode,
                    (int) p.getX() + "," + (int) p.getY() + "," + (int) p.getZ()));
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

    /** gcstat：GC 与类加载统计。 */
    public static String gcstat() {
        StringBuilder sb = new StringBuilder();
        sb.append("gc               (name / collections / time_ms)\n");
        sb.append("-------------------------------------------------\n");
        long tc = 0, tt = 0;
        for (TerminalKernel.GcRow g : TerminalKernel.gcStats()) {
            sb.append(String.format("%-32s %8d %10d\n", g.name(), g.count(), g.timeMs()));
            tc += g.count(); tt += g.timeMs();
        }
        sb.append("-------------------------------------------------\n");
        sb.append(String.format("TOTAL: %d collections, %d ms\n", tc, tt));
        sb.append(String.format("Classes: loaded=%d, total=%d, unloaded=%d\n",
                TerminalKernel.loadedClasses(), TerminalKernel.totalLoadedClasses(), TerminalKernel.unloadedClasses()));
        Runtime rt = Runtime.getRuntime();
        sb.append(String.format("Heap: %dK used / %dK committed / %dK max\n",
                (rt.totalMemory() - rt.freeMemory()) / 1024, rt.totalMemory() / 1024, rt.maxMemory() / 1024));
        return sb.toString();
    }

    /** vmstat：系统综合统计（进程/内存/交换/GC/CPU）。 */
    public static String vmstat() {
        Runtime rt = Runtime.getRuntime();
        long pTotal = TerminalKernel.physicalTotalMemory();
        long pFree = TerminalKernel.physicalFreeMemory();
        long swTotal = TerminalKernel.swapTotal();
        long swFree = TerminalKernel.swapFree();
        long gcCount = 0, gcTime = 0;
        for (TerminalKernel.GcRow g : TerminalKernel.gcStats()) { gcCount += g.count(); gcTime += g.timeMs(); }
        double sys = TerminalKernel.systemCpuLoad();
        double proc = TerminalKernel.processCpuLoad();
        String pMem = pTotal > 0
                ? String.format("%d/%d MB", (pTotal - pFree) / 1048576, pTotal / 1048576)
                : String.format("JVM %d/%d MB", (rt.totalMemory() - rt.freeMemory()) / 1048576, rt.maxMemory() / 1048576);
        String sw = swTotal > 0
                ? String.format("%d/%d MB", (swTotal - swFree) / 1048576, swTotal / 1048576)
                : "n/a";
        StringBuilder sb = new StringBuilder();
        sb.append("procs -----------memory---------- ---swap-- --gc-- -----cpu-----\n");
        sb.append(String.format("%5s %13s %9s %7d %6d %6s %8s\n",
                "r=1", pMem, sw, gcCount, gcTime,
                sys >= 0 ? String.format("%.0f%%", sys * 100) : "n/a",
                proc >= 0 ? String.format("%.0f%%", proc * 100) : "n/a"));
        sb.append(String.format("threads running=%d, started=%d\n",
                TerminalKernel.threadTable().size(), TerminalKernel.totalThreadsStarted()));
        return sb.toString();
    }

    /** netstat：活动网络连接（玩家连接映射）。 */
    public static String netstat() {
        return "Active connections:\n" + ProcFS.read("/proc/net/tcp");
    }

    /** mpstat：线程 CPU 使用排行（top 10）。 */
    public static String mpstat() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%8s %-32s %-12s %10s\n", "TID", "THREAD", "STATE", "CPU(ms)"));
        sb.append("-".repeat(66)).append('\n');
        List<TerminalKernel.ThreadRow> rows = TerminalKernel.threadTable();
        int n = 0;
        for (TerminalKernel.ThreadRow t : rows) {
            if (n++ >= 10) break;
            sb.append(String.format("%8d %-32s %-12s %10d\n", t.id, clip(t.name, 32), t.state, t.cpuMs));
        }
        sb.append("-".repeat(66)).append('\n');
        sb.append("(").append(rows.size()).append(" threads total)");
        return sb.toString();
    }

    /** kinfo：内核综合信息一屏。 */
    public static String kinfo() {
        long up = TerminalKernel.uptimeSeconds();
        Runtime rt = Runtime.getRuntime();
        double sys = TerminalKernel.systemCpuLoad();
        double proc = TerminalKernel.processCpuLoad();
        double sysLoad = TerminalKernel.systemLoadAverage();
        long gcCount = 0, gcTime = 0;
        for (TerminalKernel.GcRow g : TerminalKernel.gcStats()) { gcCount += g.count(); gcTime += g.timeMs(); }
        StringBuilder sb = new StringBuilder("STOS Kernel Information\n");
        sb.append("============================\n");
        sb.append(String.format("kernel    : 1.1.0-stos (NeoForge 21.1.219)\n"));
        sb.append(String.format("vm        : %s %s\n", TerminalKernel.vmName(), TerminalKernel.vmVersion()));
        sb.append(String.format("uptime    : %dd %dh %dm %ds\n", up / 86400, (up % 86400) / 3600, (up % 3600) / 60, up % 60));
        sb.append(String.format("mspt/tps  : %.2f ms / %.2f\n", TerminalKernel.averageMspt(), TerminalKernel.tps()));
        sb.append(String.format("cpu       : sys=%s proc=%s loadavg=%s\n",
                sys >= 0 ? String.format("%.0f%%", sys * 100) : "n/a",
                proc >= 0 ? String.format("%.0f%%", proc * 100) : "n/a",
                sysLoad >= 0 ? String.format("%.2f", sysLoad) : "n/a"));
        long pTotal = TerminalKernel.physicalTotalMemory();
        if (pTotal > 0) {
            sb.append(String.format("mem(host) : %d/%d MB\n",
                    (pTotal - TerminalKernel.physicalFreeMemory()) / 1048576, pTotal / 1048576));
        }
        sb.append(String.format("mem(heap) : %d/%d MB\n",
                (rt.totalMemory() - rt.freeMemory()) / 1048576, rt.maxMemory() / 1048576));
        sb.append(String.format("gc        : %d collections, %d ms\n", gcCount, gcTime));
        sb.append(String.format("threads   : active=%d started=%d\n", TerminalKernel.threadTable().size(), TerminalKernel.totalThreadsStarted()));
        sb.append(String.format("classes   : loaded=%d\n", TerminalKernel.loadedClasses()));
        sb.append(String.format("mods      : %d loaded\n", TerminalKernel.modCount()));
        sb.append(String.format("players   : %d online\n", serverPlayerCount()));
        sb.append("============================\n");
        sb.append("subsystems: core procfs klog sched netd gc world");
        return sb.toString();
    }

    /** kmods：内核子系统表（实际实现的功能模块）。 */
    public static String kmods() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-14s %-8s %s\n", "MODULE", "STATUS", "DESCRIPTION"));
        sb.append("-".repeat(72)).append('\n');
        sb.append(String.format("%-14s %-8s %s\n", "stos_core",  "live", "Kernel core: MSPT sampler, ring log, system stats"));
        sb.append(String.format("%-14s %-8s %s\n", "stos_procfs","live", "/proc virtual filesystem (19 files)"));
        sb.append(String.format("%-14s %-8s %s\n", "stos_sched", "live", "Server tick hooks (MSPT sampling)"));
        sb.append(String.format("%-14s %-8s %s\n", "stos_klog",  "live", "dmesg ring buffer (200 entries)"));
        sb.append(String.format("%-14s %-8s %s\n", "stos_netd",  "live", "Player connection monitor"));
        sb.append(String.format("%-14s %-8s %s\n", "stos_gc",    "live", "GC & class loading statistics"));
        sb.append(String.format("%-14s %-8s %s\n", "stos_world", "live", "World/entity/chunk statistics"));
        sb.append("-".repeat(72)).append('\n');
        sb.append("7 modules loaded");
        return sb.toString();
    }

    private static int serverPlayerCount() {
        var s = TerminalKernel.server();
        return s != null ? s.getPlayerCount() : 0;
    }

    private static String clip(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}