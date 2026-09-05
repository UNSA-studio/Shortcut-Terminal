package unsa.st.com.kernel;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.List;

/**
 * /proc 内核信息挂载点：把内核数据以文件形式暴露（cat /proc/xxx 可读）。
 * 文件内容在读取时从内核数据层动态生成——没有磁盘存储，
 * 但暴露的全是真实数据（真实 MSPT/线程/模组清单），与 Linux /proc 同构。
 * 挂载位置：服务端终端路径树的 /proc/。
 */
public final class ProcFS {
    private ProcFS() {}

    /** proc 文件清单。 */
    public static final String[] FILES = {
            "/proc/uptime", "/proc/loadavg", "/proc/meminfo",
            "/proc/version", "/proc/cpuinfo", "/proc/mods",
            "/proc/net/dev", "/proc/threads", "/proc/mspt"
    };

    /** 判断路径是否为 proc 文件（支持 /proc 及子路径查询）。 */
    public static boolean isProcPath(String fullPath) {
        if (fullPath == null) return false;
        String p = fullPath.startsWith("/") ? fullPath : "/" + fullPath;
        for (String f : FILES) if (f.equals(p)) return true;
        return p.equals("/proc");
    }

    /** 读取 proc 文件内容（读取时从内核数据层动态生成）。 */
    public static String read(String fullPath) {
        if (fullPath == null) return null;
        String p = fullPath.startsWith("/") ? fullPath : "/" + fullPath;
        switch (p) {
            case "/proc/uptime": {
                long up = TerminalKernel.uptimeSeconds();
                return up + " " + (up * 20) + "\n";
            }
            case "/proc/loadavg": {
                double mspt = TerminalKernel.averageMspt();
                double tps = TerminalKernel.tps();
                int players = serverPlayerCount();
                // loadavg 语义映射: mspt/50 → 负载 (50ms=满载1.0)
                double l1 = Math.min(mspt / 50.0, 99.0);
                return String.format("%.2f %.2f %.2f %d/%d tasks, tps=%.2f\n", l1, l1 * 0.95, l1 * 0.9, players, maxPlayers(), tps);
            }
            case "/proc/meminfo": {
                Runtime rt = Runtime.getRuntime();
                long total = rt.totalMemory() / 1024, free = rt.freeMemory() / 1024, max = rt.maxMemory() / 1024;
                return String.format(
                        "MemTotal:      %d kB\nMemFree:       %d kB\nMemUsed:       %d kB\nSwapTotal:     %d kB\nSwapFree:      %d kB\n",
                        max, free, total - free, 0, 0);
            }
            case "/proc/version": {
                return "Shortcut Terminal kernel version 1.0.1 (unsa.st.com) #1 SMP NeoForge 21.1.219\n";
            }
            case "/proc/cpuinfo": {
                int cores = Runtime.getRuntime().availableProcessors();
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < cores; i++) {
                    sb.append(String.format("processor\t: %d\nmodel name\t: %s %s\n\n",
                            i, System.getProperty("os.arch", "?"), "(JVM virtual)"));
                }
                return sb.toString();
            }
            case "/proc/mods": {
                StringBuilder sb = new StringBuilder();
                for (TerminalKernel.ModRow m : TerminalKernel.loadedMods()) {
                    sb.append(String.format("%-30s %-20s %s\n", m.id(), m.name(), m.version()));
                }
                return sb.toString();
            }
            case "/proc/net/dev": {
                StringBuilder sb = new StringBuilder("Inter-|   Receive                  |  Transmit\n");
                sb.append(" face |bytes    packets errs drop    |bytes    packets errs drop\n");
                for (ServerPlayer pl : onlinePlayers()) {
                    sb.append(String.format(" p%05d: ping %4d ms (player channel)\n",
                            Math.abs(pl.getUUID().hashCode() % 100000), pl.connection.latency()));
                }
                return sb.toString();
            }
            case "/proc/threads": {
                StringBuilder sb = new StringBuilder();
                for (TerminalKernel.ThreadRow t : TerminalKernel.threadTable()) {
                    sb.append(String.format("%6d %-32s %-10s cpu=%dms\n", t.id, t.name, t.state, t.cpuMs));
                }
                return sb.toString();
            }
            case "/proc/mspt": {
                return String.format("mspt_avg: %.2f\nmspt_max: %.2f\ntps: %.2f\ntarget: 50.00\nwindow: 100 ticks\n",
                        TerminalKernel.averageMspt(), TerminalKernel.maxMspt(), TerminalKernel.tps());
            }
            default: return null;
        }
    }

    private static int serverPlayerCount() {
        var s = TerminalKernel.server();
        return s != null ? s.getPlayerCount() : 0;
    }

    private static int maxPlayers() {
        var s = TerminalKernel.server();
        return s != null ? s.getMaxPlayers() : 0;
    }

    private static List<ServerPlayer> onlinePlayers() {
        var s = TerminalKernel.server();
        return s != null ? s.getPlayerList().getPlayers() : List.of();
    }
}