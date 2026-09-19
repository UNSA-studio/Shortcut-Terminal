package unsa.st.com.compute;

import net.minecraft.world.item.ItemStack;
import unsa.st.com.item.TerminalPanelItem;

import java.util.Locale;

/**
 * 硬件规格：面板上的 RAM / SSD 模块读写与效果映射（无客户端依赖，双端安全）。
 *
 * <p>RAM → 终端历史缓冲行数（系统能"装"多少输出）；SSD → 虚拟存储配额字符数（能"存"多少数据）。
 * 与处理器（算力）共同构成终端的三维硬件体系。</p>
 */
public final class HardwareSpec {
    private HardwareSpec() {}

    /** 无模块时的默认值。 */
    public static final int DEFAULT_SCROLLBACK = 200;
    public static final long DEFAULT_QUOTA_CHARS = 4096;

    // ==================== NBT 读写（与处理器同通道） ====================

    public static void installRam(ItemStack panel, int ramMb) {
        TerminalPanelItem.setCustomInt(panel, "RamMb", ramMb);
    }

    public static int getRamMb(ItemStack panel) {
        Integer v = TerminalPanelItem.getCustomInt(panel, "RamMb");
        return v == null ? 0 : v;
    }

    public static void installSsd(ItemStack panel, int ssdGb) {
        TerminalPanelItem.setCustomInt(panel, "SsdGb", ssdGb);
    }

    public static int getSsdGb(ItemStack panel) {
        Integer v = TerminalPanelItem.getCustomInt(panel, "SsdGb");
        return v == null ? 0 : v;
    }

    // ==================== 物品识别（按注册名匹配档位） ====================

    /** RAM 物品 → 容量 MB；非 RAM 返回 -1。 */
    public static int ramMbOfItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return -1;
        String n = stack.getItem().toString().toLowerCase(Locale.ROOT);
        if (n.endsWith("ram_1gb")) return 1024;
        if (n.endsWith("ram_4gb")) return 4096;
        if (n.endsWith("ram_16gb")) return 16384;
        if (n.endsWith("ram_64gb")) return 65536;
        if (n.endsWith("ram_256gb")) return 262144;
        return -1;
    }

    /** SSD 物品 → 容量 GB；非 SSD 返回 -1。 */
    public static int ssdGbOfItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return -1;
        String n = stack.getItem().toString().toLowerCase(Locale.ROOT);
        if (n.endsWith("ssd_64gb")) return 64;
        if (n.endsWith("ssd_256gb")) return 256;
        if (n.endsWith("ssd_1tb")) return 1024;
        if (n.endsWith("ssd_4tb")) return 4096;
        if (n.endsWith("ssd_16tb")) return 16384;
        return -1;
    }

    /** RAM 容量 MB → 档位（1..5；未知 0）。 */
    public static int ramTierOfMb(int mb) {
        if (mb >= 262144) return 5;
        if (mb >= 65536) return 4;
        if (mb >= 16384) return 3;
        if (mb >= 4096) return 2;
        if (mb >= 1024) return 1;
        return 0;
    }

    /** SSD 容量 GB → 档位（1..5；未知 0）。 */
    public static int ssdTierOfGb(int gb) {
        if (gb >= 16384) return 5;
        if (gb >= 4096) return 4;
        if (gb >= 1024) return 3;
        if (gb >= 256) return 2;
        if (gb >= 64) return 1;
        return 0;
    }

    // ==================== 效果映射 ====================

    /** RAM 容量 → 终端历史缓冲行数（翻倍档位）。 */
    public static int scrollbackLimit(int ramMb) {
        if (ramMb >= 262144) return 131072;
        if (ramMb >= 65536) return 32768;
        if (ramMb >= 16384) return 8192;
        if (ramMb >= 4096) return 2048;
        if (ramMb >= 1024) return 512;
        return DEFAULT_SCROLLBACK;
    }

    /** SSD 容量 → 存储配额字符数（终端文本场景按 1 GB ≈ 1024 字符缩放）。 */
    public static long storageQuotaChars(int ssdGb) {
        if (ssdGb <= 0) return DEFAULT_QUOTA_CHARS;
        return ssdGb * 1024L;
    }

    // ==================== 显示格式化 ====================

    public static String formatRam(int mb) {
        if (mb <= 0) return "none";
        if (mb < 1024) return mb + " MB";
        int gb = mb / 1024;
        if (gb < 1024) return gb + " GB";
        return (gb / 1024) + " TB";
    }

    public static String formatSsd(int gb) {
        if (gb <= 0) return "none";
        if (gb < 1024) return gb + " GB";
        return (gb / 1024) + " TB";
    }

    public static String formatChars(long chars) {
        if (chars < 1024) return chars + " B";
        if (chars < 1048576) return String.format(Locale.ROOT, "%.1f KB", chars / 1024.0);
        return String.format(Locale.ROOT, "%.1f MB", chars / 1048576.0);
    }

    /** df 报告（由调用方提供数据，避免客户端依赖）。 */
    public static String dfReport(long quotaChars, long usedChars, int ramMb) {
        long avail = Math.max(0, quotaChars - usedChars);
        int pct = quotaChars > 0 ? (int) (usedChars * 100 / quotaChars) : 0;
        StringBuilder sb = new StringBuilder();
        sb.append("Filesystem      Size    Used    Avail   Use% Mounted on\n");
        sb.append(String.format(Locale.ROOT, "/dev/ssd0       %-7s %-7s %-7s %3d%% /",
                formatChars(quotaChars), formatChars(usedChars), formatChars(avail), pct));
        sb.append(String.format(Locale.ROOT, "\n/dev/ram0       %-7s (scrollback %d lines)",
                formatRam(ramMb), scrollbackLimit(ramMb)));
        return sb.toString();
    }
}