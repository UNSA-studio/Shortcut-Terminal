package unsa.st.com.compute;

import net.minecraft.world.item.ItemStack;
import unsa.st.com.item.TerminalPanelItem;

import java.util.Locale;
import java.util.Random;

/**
 * 处理器组件读写：等级存在面板物品的 CUSTOM_DATA NBT 中（与 TID 同通道）。
 * 约定键 "ProcessorLevel"，不存在 = 裸终端（0）。
 */
public final class ProcessorCapability {
    private ProcessorCapability() {}

    /** 读取面板已安装的处理器等级；未安装返回 0。 */
    public static int getInstalledLevel(ItemStack panel) {
        Integer v = TerminalPanelItem.getCustomInt(panel, "ProcessorLevel");
        return v == null ? 0 : Math.max(0, Math.min(9, v));
    }

    /** 安装/替换处理器，返回安装前面板描述（用于消息）。 */
    public static void install(ItemStack panel, int level) {
        TerminalPanelItem.setCustomInt(panel, "ProcessorLevel", level);
    }

    /** 卸下处理器（清零，不返还物品——替换时由交互逻辑处理）。 */
    public static void uninstall(ItemStack panel) {
        TerminalPanelItem.setCustomInt(panel, "ProcessorLevel", 0);
    }

    /** 物品 ID → 等级（processor_l3 → 3）。非处理器物品返回 -1。 */
    public static int levelOfProcessorItem(ItemStack stack) {
        String id = stack.getItem().toString();
        // Item.toString() 默认为注册名短形式（如 item.shortcutterminal.processor_l3 由
        // DeferredItem 提供）；做宽松解析
        String s = id.toLowerCase(Locale.ROOT);
        if (s.endsWith("_l1")) return 1;
        if (s.endsWith("_l2")) return 2;
        if (s.endsWith("_l3")) return 3;
        if (s.endsWith("_l4")) return 4;
        if (s.endsWith("_l5")) return 5;
        if (s.endsWith("_l6")) return 6;
        if (s.endsWith("_l7")) return 7;
        if (s.endsWith("_l8")) return 8;
        if (s.endsWith("_l9")) return 9;
        return -1;
    }

    /** 给定随机源与目标等级，产出对应等级处理器物品堆（供光刻机批量出活）。 */
    public static java.util.List<ItemStack> produceBatch(int targetLevel, Random random) {
        java.util.List<ItemStack> out = new java.util.ArrayList<>();
        int[] batch = YieldTable.rollBatch(targetLevel, random);
        int[] counts = new int[10];
        for (int l : batch) counts[l]++;
        for (int l = 1; l <= 9; l++) {
            if (counts[l] > 0 && ModItemsLookup.processor(l) != null) {
                out.add(new ItemStack(ModItemsLookup.processor(l), counts[l]));
            }
        }
        return out;
    }

    /** 延迟查找处理器物品（避免 registry-freeze 期静态引用）。 */
    private static final class ModItemsLookup {
        static net.minecraft.world.item.Item processor(int level) {
            var item = unsa.st.com.registry.ModItems.PROCESSORS.get(level);
            return item == null ? null : item.get();
        }
    }
}