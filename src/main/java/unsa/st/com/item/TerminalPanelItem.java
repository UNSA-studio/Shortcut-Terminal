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
        if (!level.isClientSide) attachTID(stack, player);
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

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack panel = player.getItemInHand(hand);
        ItemStack other = player.getItemInHand(hand == net.minecraft.world.InteractionHand.MAIN_HAND
                ? net.minecraft.world.InteractionHand.OFF_HAND : net.minecraft.world.InteractionHand.MAIN_HAND);
        int ram = HardwareSpec.ramMbOfItem(other), ssd = HardwareSpec.ssdGbOfItem(other);
        int cpu = ProcessorCapability.levelOfProcessorItem(other);
        if (ram > 0 || ssd > 0 || cpu > 0) {
            if (!level.isClientSide) {
                ItemStack returned = ItemStack.EMPTY;
                if (ram > 0) {
                    int old = HardwareSpec.getRamMb(panel);
                    var item = unsa.st.com.registry.ModItems.RAM_MODULES.get(HardwareSpec.ramTierOfMb(old));
                    if (item != null) returned = new ItemStack(item.get());
                    HardwareSpec.installRam(panel, ram);
                } else if (ssd > 0) {
                    int old = HardwareSpec.getSsdGb(panel);
                    var item = unsa.st.com.registry.ModItems.SSD_MODULES.get(HardwareSpec.ssdTierOfGb(old));
                    if (item != null) returned = new ItemStack(item.get());
                    HardwareSpec.installSsd(panel, ssd);
                } else {
                    var item = unsa.st.com.registry.ModItems.PROCESSORS.get(ProcessorCapability.getInstalledLevel(panel));
                    if (item != null) returned = new ItemStack(item.get());
                    ProcessorCapability.install(panel, cpu);
                }
                other.shrink(1);
                if (!returned.isEmpty() && !player.getInventory().add(returned)) player.drop(returned, false);
                attachTID(panel, player);
                player.displayClientMessage(Component.literal("STOS: hardware installed."), true);
            }
        } else if (level.isClientSide) {
            unsa.st.com.client.ClientPayloadHandler.openTerminal(panel);
        } else attachTID(panel, player);
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(panel, level.isClientSide);
    }
}
