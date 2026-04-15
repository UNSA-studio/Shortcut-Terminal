package unsa.st.com.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public class PlayerJoinHandler {
    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 检查玩家背包是否已有 Patchouli 指南书
            boolean hasBook = player.getInventory().items.stream()
                    .anyMatch(stack -> stack.getItem() == Items.WRITTEN_BOOK &&
                            stack.hasTag() &&
                            "st:st_guide".equals(stack.getTag().getString("patchouli:book")));
            if (!hasBook) {
                ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
                CompoundTag tag = book.getOrCreateTag();
                tag.putString("patchouli:book", "st:st_guide");
                if (!player.getInventory().add(book)) {
                    player.drop(book, false);
                }
            }
        }
    }
}
