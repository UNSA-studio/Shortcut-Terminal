package unsa.st.com.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.CompoundTag;
import unsa.st.com.terminal.TerminalIdManager;

public class TerminalPanelItem extends Item {
    public TerminalPanelItem(Properties properties) {
        super(properties);
    }

    @Override
    public void onCraftedBy(ItemStack stack, Level level, Player player) {
        super.onCraftedBy(stack, level, player);
        attachTID(stack, player);
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

    public static String getTID(ItemStack stack) {
        CustomData customData = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        if (tag.contains("TerminalTID")) {
            return tag.getString("TerminalTID");
        }
        return null;
    }
}