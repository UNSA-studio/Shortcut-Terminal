package unsa.st.com.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import unsa.st.com.compute.ComputePolicy;
import unsa.st.com.compute.HardwareSpec;
import unsa.st.com.compute.ProcessorCapability;
import unsa.st.com.item.TerminalPanelItem;

import java.util.function.ToIntFunction;

/**
 * 客户端硬件查询：从当前玩家的手持/背包中找终端面板，读取 CPU / RAM / SSD 规格。
 * 仅在物理客户端调用（单人游戏即本机）。
 *
 * <p>窗口规则：CPU 线程数 = 硬性窗口上限；RAM = 每窗口内存容量，任一窗口占用超 80% 即拦截新窗口。</p>
 */
public final class ClientHardware {
    private ClientHardware() {}

    private static int panelStat(ToIntFunction<ItemStack> fn, int def) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return def;
        for (ItemStack held : mc.player.getHandSlots()) {
            if (held.getItem() instanceof TerminalPanelItem) return fn.applyAsInt(held);
        }
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack s = inv.items.get(i);
            if (s.getItem() instanceof TerminalPanelItem) return fn.applyAsInt(s);
        }
        return def;
    }

    /** 当前玩家面板的 RAM（MB）；无面板/未装返回 0。 */
    public static int clientRamMb() {
        return panelStat(HardwareSpec::getRamMb, 0);
    }

    /** 当前玩家面板的 SSD（GB）；无面板/未装返回 0。 */
    public static int clientSsdGb() {
        return panelStat(HardwareSpec::getSsdGb, 0);
    }

    /** 当前玩家面板的处理器等级（0 = 裸机）。 */
    public static int clientProcessorLevel() {
        return panelStat(ProcessorCapability::getInstalledLevel, 0);
    }

    /** 当前生效的终端历史缓冲行数（= 每窗口内存容量）。 */
    public static int scrollbackLimit() {
        return HardwareSpec.scrollbackLimit(clientRamMb());
    }

    /** 当前生效的存储配额字符数。 */
    public static long storageQuotaChars() {
        return HardwareSpec.storageQuotaChars(clientSsdGb());
    }

    // ==================== 窗口规则 ====================

    /** CPU 硬上限：处理器线程数就是允许的窗口数（不管每个窗口用了多少内存）。 */
    public static int cpuWindowLimit() {
        return ComputePolicy.threads(clientProcessorLevel());
    }

    /** RAM 总内存容量（行）：所有窗口输出行数之和的上限。 */
    public static int totalMemoryCapacityLines() {
        return HardwareSpec.totalMemoryCapacityLines(clientRamMb());
    }

    /** 当前打开的窗口数（无界面时按 1 计）。 */
    public static int currentWindowCount() {
        try {
            return unsa.st.com.gui.TerminalScreen.windowCount();
        } catch (Throwable t) {
            return 1;
        }
    }

    /** 全部窗口的输出行数总和（无界面时为 0）。 */
    public static int totalUsedLines() {
        try {
            return unsa.st.com.gui.TerminalScreen.totalUsedLines();
        } catch (Throwable t) {
            return 0;
        }
    }

    /** 窗口常驻内存记账（KB）。 */
    public static int windowMemoryKb() {
        return HardwareSpec.windowMemoryKb(currentWindowCount());
    }

    /** 全部窗口总占用是否已用满 80% RAM 总容量。 */
    public static boolean ramUnderPressure() {
        return HardwareSpec.ramUnderPressure(totalUsedLines(), totalMemoryCapacityLines());
    }

    /** 客户端 df 报告。 */
    public static String dfReport(String playerName) {
        long quota = storageQuotaChars();
        long used = 0;
        try { used = ClientVirtualFileSystem.totalChars(playerName); } catch (Throwable ignored) {}
        return HardwareSpec.dfReport(quota, used, clientRamMb(), currentWindowCount(), totalUsedLines());
    }
}
