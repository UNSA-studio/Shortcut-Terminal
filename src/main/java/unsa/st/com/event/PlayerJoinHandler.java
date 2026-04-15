package unsa.st.com.event;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.fml.ModList;
import net.minecraft.world.item.component.CustomData;

public class PlayerJoinHandler {
    private static final String PATCHOULI_MOD_ID = "patchouli";
    private static final String OUR_BOOK_ID = "shortcutterminal:st_guide";

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        // 如果没有安装 Patchouli，直接跳过，不发放书籍
        if (!ModList.get().isLoaded(PATCHOULI_MOD_ID)) {
            return;
        }

        if (event.getEntity() instanceof ServerPlayer player) {
            // 检查玩家背包是否已有我们的手册
            boolean hasBook = player.getInventory().items.stream().anyMatch(stack -> {
                if (stack.getItem() == Items.WRITTEN_BOOK) {
                    CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
                    return customData != null && 
                           OUR_BOOK_ID.equals(customData.copyTag().getString("patchouli:book"));
                }
                return false;
            });

            if (!hasBook) {
                ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
                
                // 正确创建 CustomData 并设置 patchouli:book 标识
                CustomData customData = CustomData.EMPTY.update(
                    tag -> tag.putString("patchouli:book", OUR_BOOK_ID)
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
