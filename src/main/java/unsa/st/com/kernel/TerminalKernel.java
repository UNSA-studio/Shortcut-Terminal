package unsa.st.com.kernel;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Shortcut Terminal 模拟内核——统一数据层。
 *
 * <p>所有终端命令共享这一份"内核视图"：MSPT 采样（真实 tick 计时环形缓冲）、
 * dmesg 环形日志（订阅 NeoForge 服务器生命周期/错误事件）、
 * 真实线程表（ThreadMXBean）、已加载模组清单（FML ModList）。</p>
 *
 * <p>设计原则：只聚合**真实**数据（服务器状态/JVM/FML），绝不伪造不可观测的信息。
 * 无法观测的数据输出占位说明（如宿主机 CPU 负载），保持诚实口径。</p>
 */
public final class TerminalKernel {
    private TerminalKernel() {}

    // ==================== MSPT 环形采样 ====================
    private static final int MSPT_WINDOW = 100;
    private static final double[] msptWindow = new double[MSPT_WINDOW];
    private static int msptCursor = 0;
    private static long lastTickEnd = -1;
    private static long lastTickStart = -1;

    // ==================== dmesg 环形日志 ====================
    private static final int DMESG_CAPACITY = 200;
    private static final Deque<String> dmesg = new ArrayDeque<>(DMESG_CAPACITY);

    /** 内核启动时间戳（服务器 Started 事件时置零）。 */
    private static long bootTime = System.currentTimeMillis();

    private static final ThreadMXBean THREAD_BEAN = ManagementFactory.getThreadMXBean();

    // ==================== tick 采样（由 KernelEventHandler 驱动） ====================

    /** Pre-tick：记录本 tick 开始时间。 */
    public static void onTickStart() {
        lastTickStart = System.nanoTime();
    }

    /** Post-tick：结算本 tick 耗时，写入环形缓冲。 */
    public static void onTickEnd() {
        if (lastTickStart > 0) {
            double ms = (System.nanoTime() - lastTickStart) / 1_000_000.0;
            msptWindow[msptCursor] = ms;
            msptCursor = (msptCursor + 1) % MSPT_WINDOW;
        }
    }

    /** 服务器就绪：重置内核状态。 */
    public static void onServerStart() {
        bootTime = System.currentTimeMillis();
        dmesg.clear();
        klog("kernel: Shortcut Terminal kernel online");
        klog("kernel: MSPT sampler initialized (window=" + MSPT_WINDOW + ")");
    }

    // ==================== klog（内核日志） ====================

    /** 向 dmesg 环形缓冲写一条内核消息。 */
    public static synchronized void klog(String message) {
        String ts = String.format("[%7.2f]", (System.currentTimeMillis() - bootTime) / 1000.0);
        if (dmesg.size() >= DMESG_CAPACITY) dmesg.pollFirst();
        dmesg.addLast(ts + " " + message);
    }

    /** dmesg 快照（旧→新）。 */
    public static synchronized List<String> dmesgSnapshot() {
        return new ArrayList<>(dmesg);
    }

    // ==================== MSPT / TPS ====================

    /** 当前 MSPT（最近窗口均值）。 */
    public static double averageMspt() {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        if (s == null) return 0;
        // 官方数据优先：服务器自带 100-tick 平均
        return s.getAverageTickTimeNanos() / 1_000_000.0;
    }

    /** 窗口内最大 MSPT。 */
    public static double maxMspt() {
        double max = 0;
        for (double v : msptWindow) if (v > max) max = v;
        return max;
    }

    /** TPS = min(20, 1000/MSPT)。 */
    public static double tps() {
        double mspt = averageMspt();
        return mspt <= 0 ? 20.0 : Math.min(20.0, 1000.0 / mspt);
    }

    /** 内核 uptime 秒。 */
    public static long uptimeSeconds() {
        return (System.currentTimeMillis() - bootTime) / 1000;
    }

    // ==================== 线程表（真实 JVM 数据） ====================

    public static final class ThreadRow {
        public final long id;
        public final String name;
        public final String state;
        public final long cpuMs;
        ThreadRow(long id, String name, String state, long cpuMs) {
            this.id = id; this.name = name; this.state = state; this.cpuMs = cpuMs;
        }
    }

    /** 真实线程表快照。 */
    public static List<ThreadRow> threadTable() {
        List<ThreadRow> rows = new ArrayList<>();
        long[] ids = THREAD_BEAN.getAllThreadIds();
        for (long id : ids) {
            var info = THREAD_BEAN.getThreadInfo(id);
            if (info == null) continue;
            long cpuMs = THREAD_BEAN.getThreadCpuTime(id) / 1_000_000L;
            if (cpuMs < 0) cpuMs = -1;
            rows.add(new ThreadRow(id, info.getThreadName(), info.getThreadState().name(), cpuMs));
        }
        rows.sort((a, b) -> Long.compare(b.cpuMs, a.cpuMs));
        return rows;
    }

    /** 已启动线程总数（含已终止未回收）。 */
    public static int totalThreadsStarted() {
        return (int) THREAD_BEAN.getTotalStartedThreadCount();
    }

    // ==================== 模组清单（FML 真实数据） ====================

    public record ModRow(String id, String name, String version) {}

    /** 已加载模组清单（FML ModList）。 */
    public static List<ModRow> loadedMods() {
        List<ModRow> rows = new ArrayList<>();
        try {
            for (var info : net.neoforged.fml.ModList.get().getMods()) {
                rows.add(new ModRow(info.getModId(), info.getDisplayName(), info.getVersion().toString()));
            }
        } catch (Throwable t) {
            klog("kernel: ModList unavailable: " + t.getClass().getSimpleName());
        }
        rows.sort((a, b) -> a.id().compareTo(b.id()));
        return rows;
    }

    public static int modCount() {
        try { return net.neoforged.fml.ModList.get().size(); } catch (Throwable t) { return -1; }
    }

    public static boolean isModLoaded(String id) {
        try { return net.neoforged.fml.ModList.get().isLoaded(id); } catch (Throwable t) { return false; }
    }

    // ==================== 服务器快捷访问 ====================

    /** 当前服务器实例（可能为 null，调用方须处理）。 */
    public static MinecraftServer server() {
        return ServerLifecycleHooks.getCurrentServer();
    }
}