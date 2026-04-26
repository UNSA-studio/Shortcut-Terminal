package unsa.st.com.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.nbt.CompoundTag;
import unsa.st.com.terminal.TerminalIdManager;

public class TerminalPanelItem extends Item {
    public TerminalPanelItem(Properties properties) {
        super(properties);
    }

    @Override
    public void onCraftedBy(ItemStack stack, Level level, Player player) {
        super.onCraftedBy(stack, level, player);
        // 合成时附加 TID
        attachTID(stack, player);
    }

    public static void attachTID(ItemStack stack, Player player) {
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.contains("TerminalTID")) {
            String tid = TerminalIdManager.generateTID();
            tag.putString("TerminalTID", tid);
            TerminalIdManager.registerTerminal(tid, player.getName().getString(), player.getUUID());
        }
    }

    public static String getTID(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("TerminalTID")) {
            return tag.getString("TerminalTID");
        }
        return null;
    }
}
