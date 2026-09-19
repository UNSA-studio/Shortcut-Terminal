package unsa.st.com.kernel;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * /proc 内核信息挂载点：把内核数据以文件形式暴露（cat /proc/xxx 可读）。
 * 文件内容在读取时从内核数据层动态生成——没有磁盘存储，
 * 但暴露的全是真实数据（MSPT/线程/模组/GC/内存/世界状态），与 Linux /proc 同构。
 * 挂载位置：终端路径树的 /proc/。
 */
public final class ProcFS {
    private ProcFS() {}

    /** proc 文件清单。 */
    public static final String[] FILES = {
            "/proc/uptime", "/proc/loadavg", "/proc/meminfo", "/proc/version",
            "/proc/cpuinfo", "/proc/mods", "/proc/net/dev", "/proc/threads", "/proc/mspt",
            "/proc/stat", "/proc/vmstat", "/proc/gc", "/proc/cmdline",
            "/proc/mounts", "/proc/filesystems", "/proc/net/tcp",
            "/proc/game/world", "/proc/game/entities", "/proc/game/chunks"
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
                // 负载语义映射: mspt/50 → 满载 1.0；系统负载可用时补充显示
                double l = Math.min(mspt / 50.0, 99.0);
                double sys = TerminalKernel.systemLoadAverage();
                String sysNote = sys >= 0 ? String.format(" sysload=%.2f", sys) : "";
                return String.format("%.2f %.2f %.2f %d/%d tasks, tps=%.2f%s\n",
                        l, l * 0.95, l * 0.9, players, maxPlayers(), tps, sysNote);
            }
            case "/proc/meminfo": {
                long pTotal = TerminalKernel.physicalTotalMemory();
                long pFree = TerminalKernel.physicalFreeMemory();
                long swTotal = TerminalKernel.swapTotal();
                long swFree = TerminalKernel.swapFree();
                Runtime rt = Runtime.getRuntime();
                long hTotal = rt.totalMemory() / 1024, hFree = rt.freeMemory() / 1024, hMax = rt.maxMemory() / 1024;
                StringBuilder sb = new StringBuilder();
                if (pTotal > 0) {
                    sb.append(String.format("MemTotal:       %d kB\n", pTotal / 1024));
                    sb.append(String.format("MemFree:        %d kB\n", pFree / 1024));
                    sb.append(String.format("MemAvailable:   %d kB\n", pFree / 1024));
                } else {
                    sb.append(String.format("MemTotal:       %d kB (JVM max; host stats unavailable)\n", hMax));
                    sb.append(String.format("MemFree:        %d kB\n", hFree));
                }
                sb.append(String.format("SwapTotal:      %d kB\n", Math.max(swTotal, 0) / 1024));
                sb.append(String.format("SwapFree:       %d kB\n", Math.max(swFree, 0) / 1024));
                sb.append(String.format("HeapTotal:      %d kB\n", hTotal));
                sb.append(String.format("HeapUsed:       %d kB\n", hTotal - hFree));
                sb.append(String.format("HeapMax:        %d kB\n", hMax));
                long nonHeap = TerminalKernel.nonHeapUsed();
                sb.append(String.format("NonHeapUsed:    %d kB\n", nonHeap > 0 ? nonHeap / 1024 : 0));
                return sb.toString();
            }
            case "/proc/version": {
                return "Shortcut Terminal kernel 1.1.0-stos (unsa.st.com) #1 SMP NeoForge 21.1.219 "
                        + TerminalKernel.vmName() + " " + TerminalKernel.vmVersion() + "\n";
            }
            case "/proc/cpuinfo": {
                int cores = Runtime.getRuntime().availableProcessors();
                double sys = TerminalKernel.systemCpuLoad();
                double proc = TerminalKernel.processCpuLoad();
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < cores; i++) {
                    sb.append(String.format("processor\t: %d\nmodel name\t: %s (JVM virtual)\n\n",
                            i, System.getProperty("os.arch", "?")));
                }
                sb.append(String.format("cpu_cores\t: %d\n", cores));
                sb.append(String.format("sys_cpu_load\t: %s\n", sys >= 0 ? String.format("%.1f%%", sys * 100) : "n/a"));
                sb.append(String.format("proc_cpu_load\t: %s\n", proc >= 0 ? String.format("%.1f%%", proc * 100) : "n/a"));
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
                var srv = TerminalKernel.server();
                StringBuilder sb = new StringBuilder("Inter-|   Receive                  |  Transmit\n");
                sb.append(" face |bytes    packets errs drop    |bytes    packets errs drop\n");
                sb.append(String.format(" srv: listening on port %d\n", srv != null ? srv.getPort() : 0));
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
            // ============ 扩展文件（第二批） ============
            case "/proc/stat": {
                long totalTicks = TerminalKernel.uptimeSeconds() * 20;
                double tps = TerminalKernel.tps();
                long busy = (long) (totalTicks * Math.min(tps / 20.0, 1.0));
                long idle = Math.max(totalTicks - busy, 0);
                var srv = TerminalKernel.server();
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("cpu  %d 0 %d %d 0 0 0 0 0 0\n", busy, busy / 3, idle));
                sb.append(String.format("ctxt %d\n", srv != null ? srv.getTickCount() : 0));
                sb.append(String.format("btime %d\n", java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime() / 1000));
                sb.append(String.format("processes %d\n", TerminalKernel.totalThreadsStarted()));
                sb.append("procs_running 1\nprocs_blocked 0\n");
                return sb.toString();
            }
            case "/proc/vmstat": {
                Runtime rt = Runtime.getRuntime();
                long heapUsedKb = (rt.totalMemory() - rt.freeMemory()) / 1024;
                long gcCount = 0, gcTime = 0;
                for (TerminalKernel.GcRow g : TerminalKernel.gcStats()) { gcCount += g.count(); gcTime += g.timeMs(); }
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("nr_free_pages %d\n", rt.freeMemory() / 4096));
                sb.append(String.format("nr_active_anon %d\n", heapUsedKb));
                sb.append(String.format("pgscan_kswapd %d\n", gcCount));
                sb.append(String.format("pgsteal_kswapd %d\n", gcTime));
                sb.append("oom_kill 0\n");
                sb.append(String.format("nonheap_kb %d\n", Math.max(TerminalKernel.nonHeapUsed(), 0) / 1024));
                sb.append(String.format("objects_pending_finalization %d\n", Math.max(TerminalKernel.pendingFinalization(), 0)));
                return sb.toString();
            }
            case "/proc/gc": {
                StringBuilder sb = new StringBuilder();
                sb.append("gc_name                          count      time_ms\n");
                sb.append("---------------------------------------------------\n");
                long tCount = 0, tTime = 0;
                for (TerminalKernel.GcRow g : TerminalKernel.gcStats()) {
                    sb.append(String.format("%-32s %8d %10d\n", g.name(), g.count(), g.timeMs()));
                    tCount += g.count(); tTime += g.timeMs();
                }
                sb.append("---------------------------------------------------\n");
                sb.append(String.format("total                            %8d %10d\n", tCount, tTime));
                sb.append(String.format("classes_loaded                   %8d\n", TerminalKernel.loadedClasses()));
                sb.append(String.format("classes_total                    %8d\n", TerminalKernel.totalLoadedClasses()));
                sb.append(String.format("classes_unloaded                 %8d\n", TerminalKernel.unloadedClasses()));
                Runtime rt = Runtime.getRuntime();
                sb.append(String.format("heap_used_kb                     %8d\n", (rt.totalMemory() - rt.freeMemory()) / 1024));
                sb.append(String.format("heap_committed_kb                %8d\n", rt.totalMemory() / 1024));
                sb.append(String.format("heap_max_kb                      %8d\n", rt.maxMemory() / 1024));
                return sb.toString();
            }
            case "/proc/cmdline": {
                StringBuilder sb = new StringBuilder();
                List<String> args = TerminalKernel.vmArguments();
                sb.append(args.isEmpty() ? "(no jvm arguments)" : String.join(" ", args)).append('\n');
                sb.append(String.format("vm.name: %s\nvm.version: %s\n", TerminalKernel.vmName(), TerminalKernel.vmVersion()));
                return sb.toString();
            }
            case "/proc/mounts": {
                String gameDir = "unknown";
                try { gameDir = unsa.st.com.pkg.PkgManager.getGameDir(true).toAbsolutePath().toString(); } catch (Throwable ignored) {}
                return "stosfs / stosfs rw,relatime 0 0\n"
                     + "proc /proc proc rw 0 0\n"
                     + "sysfs /sys sysfs ro 0 0\n"
                     + "tmpfs /tmp tmpfs rw 0 0\n"
                     + "hostfs " + gameDir + " stosfs rw 0 0\n";
            }
            case "/proc/filesystems": {
                return "nodev\tproc\nnodev\tsysfs\nnodev\ttmpfs\nnodev\tstosfs\n\text4\n";
            }
            case "/proc/net/tcp": {
                var srv = TerminalKernel.server();
                int port = srv != null ? srv.getPort() : 0;
                StringBuilder sb = new StringBuilder();
                sb.append("  sl  local_address        remote_address       st          user\n");
                sb.append("   0  0.0.0.0:").append(String.format("%04X", port)).append("    0.0.0.0:0000         LISTEN      srv\n");
                int i = 1;
                for (ServerPlayer pl : onlinePlayers()) {
                    sb.append(String.format("  %2d  0.0.0.0:%-5s  %-20s ESTABLISHED %s (ping %dms)\n",
                            i++, String.format("%04X", port),
                            anonymizeIp(pl.getIpAddress()) + ":xxxxx",
                            pl.getGameProfile().getName(), pl.connection.latency()));
                }
                return sb.toString();
            }
            case "/proc/game/world": {
                var srv = TerminalKernel.server();
                if (srv == null) return "no server running\n";
                var ow = srv.overworld();
                if (ow == null) return "no overworld\n";
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("level:      %s\n", dimName(ow)));
                sb.append(String.format("day_time:   %d\n", ow.getDayTime()));
                String weather = ow.isThundering() ? "thunder" : (ow.isRaining() ? "rain" : "clear");
                sb.append(String.format("weather:    %s\n", weather));
                sb.append(String.format("difficulty: %s\n", ow.getDifficulty().getKey()));
                sb.append(String.format("seed:       %d\n", ow.getSeed()));
                sb.append(String.format("players:    %d\n", serverPlayerCount()));
                return sb.toString();
            }
            case "/proc/game/entities": {
                if (allLevels().isEmpty()) return "no server running\n";
                StringBuilder sb = new StringBuilder("dimension                        entities  players\n");
                sb.append("--------------------------------------------------\n");
                for (ServerLevel lvl : allLevels()) {
                    long entities = countEntities(lvl);
                    int players = lvl.getPlayers(p -> true).size();
                    sb.append(String.format("%-32s %8s %8d\n", dimName(lvl),
                            entities >= 0 ? String.valueOf(entities) : "n/a", players));
                }
                return sb.toString();
            }
            case "/proc/game/chunks": {
                if (allLevels().isEmpty()) return "no server running\n";
                StringBuilder sb = new StringBuilder("dimension                        loaded_chunks\n");
                sb.append("--------------------------------------------------\n");
                for (ServerLevel lvl : allLevels()) {
                    long chunks = -1;
                    try { chunks = lvl.getChunkSource().getLoadedChunksCount(); } catch (Throwable ignored) {}
                    sb.append(String.format("%-32s %8s\n", dimName(lvl), chunks >= 0 ? String.valueOf(chunks) : "n/a"));
                }
                return sb.toString();
            }
            default: return null;
        }
    }

    // ==================== 辅助 ====================

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

    private static List<ServerLevel> allLevels() {
        List<ServerLevel> out = new ArrayList<>();
        var s = TerminalKernel.server();
        if (s != null) {
            for (ServerLevel lvl : s.getAllLevels()) out.add(lvl);
        }
        return out;
    }

    /** IP 脱敏（只保留前两段）。 */
    private static String anonymizeIp(String ip) {
        if (ip == null) return "unknown";
        String addr = ip.split(":")[0];
        String[] parts = addr.split("\\.");
        if (parts.length == 4) return parts[0] + "." + parts[1] + ".x.x";
        if (addr.length() > 16) return addr.substring(0, 16) + "...";
        return addr;
    }

    private static String dimName(ServerLevel lvl) {
        try { return lvl.dimension().location().toString(); } catch (Throwable t) { return "unknown"; }
    }

    private static long countEntities(ServerLevel lvl) {
        long n = 0;
        try { for (var ignored : lvl.getAllEntities()) n++; } catch (Throwable t) { return -1; }
        return n;
    }
}