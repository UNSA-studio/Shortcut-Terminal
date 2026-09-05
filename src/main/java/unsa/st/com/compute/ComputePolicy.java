package unsa.st.com.compute;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * STOS 算力策略表：处理器等级 → 字符处理速度、命令 → 所需等级。
 *
 * <p>等级 L1~L9 每级算力翻倍（L4 = 128 chars/s）。命令按 T1/T2/T3 分档，
 * 裸终端（未安装处理器）只能用终端程序自带的基础命令。
 * 附属命令 L1+ 可用。</p>
 */
public final class ComputePolicy {
    private ComputePolicy() {}

    /** 裸终端（无处理器）的等级常量。 */
    public static final int LEVEL_NONE = 0;
    /** 最高处理器等级。 */
    public static final int LEVEL_MAX = 9;

    /** 每级字符处理速度（chars/s），L1=16，翻倍到 L9=4096。 */
    public static int charsPerSecond(int level) {
        if (level <= 0) return 4; // 裸终端仍有极低算力渲染基础程序
        return 8 << level;       // L1=16, L2=32 ... L9=4096
    }

    // ==================== 命令分档 ====================

    /** T0：裸终端自带命令（终端程序，无需算力伪装系统）。 */
    public static final Set<String> TIER0 = Set.of(
            "help", "ls", "cd", "pwd", "cat", "echo", "clear", "mkdir", "touch", "rm", "whoami");

    /** T1：基础伪装系统（L1+）。 */
    public static final Set<String> TIER1 = Set.of(
            "cp", "mv", "head", "tail", "wc", "grep", "sort", "uniq",
            "date", "uname", "uptime", "du", "which", "chmod", "addons");

    /** T2：中阶系统命令（L4+，需要稳定内核渲染）。 */
    public static final Set<String> TIER2 = Set.of(
            "ps", "df", "free", "env", "hostname", "lscpu", "top", "w",
            "dmesg", "tps", "lsmod", "modinfo", "ping", "addons");

    /** T3：高阶命令（L7+，满血伪装系统）。 */
    public static final Set<String> TIER3 = Set.of(
            "curl", "wget", "sh", "macro", "pkg", "refresh", "sleep", "kill", "winget",
            "run", "user", "stop");

    /** 命令所需最低处理器等级；T0 命令返回 0。 */
    public static int requiredLevel(String command) {
        String cmd = command == null ? "" : command.toLowerCase(Locale.ROOT);
        if (TIER0.contains(cmd)) return 0;
        if (TIER1.contains(cmd)) return 1;
        if (TIER2.contains(cmd)) return 4;
        if (TIER3.contains(cmd)) return 7;
        // 未知命令（附属命令）按 L1 处理：装了处理器就能跑
        return 1;
    }

    // ==================== 门槛与延迟 ====================

    /** 算力门槛失败时该命令的冷却毫秒数。 */
    public static final long GATE_COOLDOWN_MS = 3000;

    /** 判断命令是否被算力门槛拦截。 */
    public static boolean isGated(String command, int installedLevel) {
        return installedLevel < requiredLevel(command);
    }

    /**
     * 计算输出延迟毫秒：字符数 / 算力。低等级跑大输出会明显变慢。
     * 上限 4 秒，避免 L1 看 lsmod 卡半天。
     */
    public static long outputDelayMs(String output, int installedLevel) {
        if (output == null || output.isEmpty()) return 0;
        int chars = output.length();
        long ms = (long) (chars * 1000.0 / charsPerSecond(installedLevel));
        return Math.min(ms, 4000);
    }

    /** STOS 横幅：版本 + 等级 + 算力。 */
    public static String stosBanner(int level) {
        if (level <= 0) {
            return "STOS 1.0 [bare terminal program]\n"
                 + "No processor installed - install a Processor (L1-L9) to boot the full system.\n"
                 + "Type 'help' for basic commands.";
        }
        return String.format(Locale.ROOT,
                "STOS 1.0 [L%d] compute=%d chars/s", level, charsPerSecond(level));
    }

    /** 骗过管理员眼睛的内核版本号（等级越高版本号越大）。 */
    public static String kernelVersion(int level) {
        return String.format(Locale.ROOT, "stos-kernel 4.%d.1-l%d", 9 + level, level);
    }

    // ==================== 低配强跑的 kernel panic（纯文本） ====================

    private static final String[] PANIC_LINES = {
            "kernel panic - not syncing: compute unit overheated",
            "kernel panic - attempted to kill init! compute=L2 cmd=sh",
            "kernel BUG at stos_chardev.c:4096! render pipeline overflow"
    };

    /** 低等级偶然触发 kernel panic 文本（概率 12%）。 */
    public static String maybePanic(int installedLevel, String command, java.util.Random random) {
        int req = requiredLevel(command);
        if (req >= 4 && installedLevel < req && random.nextInt(100) < 12) {
            return "*** " + PANIC_LINES[random.nextInt(PANIC_LINES.length)] + " ***\n"
                 + "[ STOS recovered. Consider upgrading your processor. ]";
        }
        return null;
    }
}