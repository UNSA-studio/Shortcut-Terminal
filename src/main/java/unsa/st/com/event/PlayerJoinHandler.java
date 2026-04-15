package unsa.st.com.event;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.minecraft.world.item.component.CustomData;

public class PlayerJoinHandler {
    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            String ourBookId = "st:st_guide";

            // 检查玩家背包是否已有我们的手册
            boolean hasBook = player.getInventory().items.stream().anyMatch(stack -> {
                if (stack.getItem() == Items.WRITTEN_BOOK) {
                    CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
                    return customData != null && 
                           ourBookId.equals(customData.copyTag().getString("patchouli:book"));
                }
                return false;
            });

            if (!hasBook) {
                ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
                
                // 正确创建 CustomData 并设置 patchouli:book 标识
                CustomData customData = CustomData.EMPTY.update(
                    tag -> tag.putString("patchouli:book", ourBookId)
                );
                book.set(DataComponents.CUSTOM_DATA, customData);

                // 将书放入玩家背包或掉落
                if (!player.getInventory().add(book)) {
                    player.drop(book, false);
                }
            }
        }
    }
}
