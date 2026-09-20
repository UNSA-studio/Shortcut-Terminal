package unsa.st.com.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import unsa.st.com.compute.ComputePolicy;
import unsa.st.com.compute.HardwareSpec;
import unsa.st.com.compute.ProcessorCapability;
import unsa.st.com.terminal.TerminalIdManager;

import java.util.List;

public class TerminalPanelItem extends Item {
    public TerminalPanelItem(Properties properties) {
        super(properties);
    }

    @Override
    public void onCraftedBy(ItemStack stack, Level level, Player player) {
        super.onCraftedBy(stack, level, player);
        attachTID(stack, player);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        // Covers creative-mode pickups: any TID-less panel gets one as soon as it is ticked
        if (!level.isClientSide && entity instanceof Player player && getTID(stack) == null) {
            attachTID(stack, player);
        }
    }

    public static void attachTID(ItemStack stack, Player player) {
        CustomData customData = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        if (!tag.contains("TerminalTID")) {
            String tid = TerminalIdManager.generateTID();
            tag.putString("TerminalTID", tid);
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, CustomData.of(tag));
            TerminalIdManager.registerTerminal(tid, player.getName().getString(), player.getUUID());
        }
    }

    /** 读取自定义 int 组件；不存在返回 null。 */
    public static Integer getCustomInt(ItemStack stack, String key) {
        CustomData customData = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        return tag.contains(key) ? tag.getInt(key) : null;
    }

    /** 写入自定义 int 组件（保留其余键）。 */
    public static void setCustomInt(ItemStack stack, String key, int value) {
        CustomData customData = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        tag.putInt(key, value);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static String getTID(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        if (tag.contains("TerminalTID")) {
            return tag.getString("TerminalTID");
        }
        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int level = ProcessorCapability.getInstalledLevel(stack);
        if (level <= 0) {
            tooltip.add(Component.literal("No processor installed (bare terminal)"));
            tooltip.add(Component.literal("Right-click with a Processor L1-L9 to install"));
        } else {
            tooltip.add(Component.literal("Processor: L" + level
                    + " (" + ComputePolicy.charsPerSecond(level) + " chars/s, "
                    + ComputePolicy.threads(level) + " threads)"));
        }
        int ramMb = HardwareSpec.getRamMb(stack);
        tooltip.add(Component.literal(ramMb > 0 ? "RAM: " + HardwareSpec.formatRam(ramMb)
                + " (" + HardwareSpec.totalMemoryCapacityLines(ramMb) + " lines total, "
                + HardwareSpec.RAM_PRESSURE_PERCENT + "% pressure limit)"
                : "No RAM module - right-click with a RAM module to install"));
        int ssdGb = HardwareSpec.getSsdGb(stack);
        tooltip.add(Component.literal(ssdGb > 0 ? "SSD: " + HardwareSpec.formatSsd(ssdGb)
                + " (" + HardwareSpec.formatChars(HardwareSpec.storageQuotaChars(ssdGb)) + " storage)"
                : "No SSD module - right-click with an SSD module to install"));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    /** 手持处理器右键面板 → 安装/替换处理器（面板拿在手上或对着已放置的方块均不处理，仅手上交互）。 */
    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack panel = player.getItemInHand(hand);
        ItemStack other = player.getItemInHand(hand == net.minecraft.world.InteractionHand.MAIN_HAND
                ? net.minecraft.world.InteractionHand.OFF_HAND : net.minecraft.world.InteractionHand.MAIN_HAND);
        // ===== RAM / SSD 模块 =====
        int ramMb = HardwareSpec.ramMbOfItem(other);
        if (ramMb > 0 && !level.isClientSide) {
            int oldRam = HardwareSpec.getRamMb(panel);
            if (oldRam > 0) {
                var oi = unsa.st.com.registry.ModItems.RAM_MODULES.get(HardwareSpec.ramTierOfMb(oldRam));
                if (oi != null && !player.getInventory().add(new ItemStack(oi.get()))) player.drop(new ItemStack(oi.get()), false);
            }
            other.shrink(1);
            HardwareSpec.installRam(panel, ramMb);
            player.displayClientMessage(Component.literal("STOS: installed RAM " + HardwareSpec.formatRam(ramMb)
                    + " (memory capacity " + HardwareSpec.totalMemoryCapacityLines(ramMb) + " lines total)"), false);
            return net.minecraft.world.InteractionResultHolder.sidedSuccess(panel, false);
        }
        int ssdGb = HardwareSpec.ssdGbOfItem(other);
        if (ssdGb > 0 && !level.isClientSide) {
            int oldSsd = HardwareSpec.getSsdGb(panel);
            if (oldSsd > 0) {
                var oi = unsa.st.com.registry.ModItems.SSD_MODULES.get(HardwareSpec.ssdTierOfGb(oldSsd));
                if (oi != null && !player.getInventory().add(new ItemStack(oi.get()))) player.drop(new ItemStack(oi.get()), false);
            }
            other.shrink(1);
            HardwareSpec.installSsd(panel, ssdGb);
            player.displayClientMessage(Component.literal("STOS: installed SSD " + HardwareSpec.formatSsd(ssdGb)
                    + " (" + HardwareSpec.formatChars(HardwareSpec.storageQuotaChars(ssdGb)) + " storage)"), false);
            return net.minecraft.world.InteractionResultHolder.sidedSuccess(panel, false);
        }

        // ===== 处理器 =====
        int newLevel = ProcessorCapability.levelOfProcessorItem(other);
        if (newLevel >= 1 && !level.isClientSide) {
            int old = ProcessorCapability.getInstalledLevel(panel);
            // 替换时把旧处理器还给玩家（若旧等级 > 0）
            if (old >= 1) {
                var oldItem = unsa.st.com.registry.ModItems.PROCESSORS.get(old);
                if (oldItem != null && !player.getInventory().add(new ItemStack(oldItem.get()))) {
                    player.drop(new ItemStack(oldItem.get()), false);
                }
            }
            ProcessorCapability.install(panel, newLevel);
            other.shrink(1);
            player.displayClientMessage(Component.literal(
                    "STOS: installed Processor L" + newLevel + " (" + ComputePolicy.charsPerSecond(newLevel) + " chars/s)"
                            + (old >= 1 ? " (replaced L" + old + ")" : "")), false);
            return net.minecraft.world.InteractionResultHolder.sidedSuccess(panel, false);
        }
        return net.minecraft.world.InteractionResultHolder.pass(panel);
    }
}