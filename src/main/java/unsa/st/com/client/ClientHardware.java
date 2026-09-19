package unsa.st.com.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import unsa.st.com.compute.HardwareSpec;
import unsa.st.com.item.TerminalPanelItem;

import java.util.function.ToIntFunction;

/**
 * 客户端硬件查询：从当前玩家的手持/背包中找终端面板，读取 RAM / SSD 规格。
 * 仅在物理客户端调用（单人游戏即本机）。
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

    /** 当前生效的终端历史缓冲行数。 */
    public static int scrollbackLimit() {
        return HardwareSpec.scrollbackLimit(clientRamMb());
    }

    /** 当前生效的存储配额字符数。 */
    public static long storageQuotaChars() {
        return HardwareSpec.storageQuotaChars(clientSsdGb());
    }

    /** 客户端 df 报告。 */
    public static String dfReport(String playerName) {
        long quota = storageQuotaChars();
        long used = 0;
        try { used = ClientVirtualFileSystem.totalChars(playerName); } catch (Throwable ignored) {}
        return HardwareSpec.dfReport(quota, used, clientRamMb());
    }
}