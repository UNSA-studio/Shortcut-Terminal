package unsa.st.com.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import unsa.st.com.registry.ModItems;

public class PlayerJoinHandler {
    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 检查玩家是否首次加入（可以根据持久化数据判断，这里简化为如果没有指南书就给）
            boolean hasBook = player.getInventory().items.stream()
                    .anyMatch(stack -> stack.getItem() == ModItems.GUIDE_BOOK.get());
            if (!hasBook) {
                ItemStack book = new ItemStack(ModItems.GUIDE_BOOK.get());
                if (!player.getInventory().add(book)) {
                    player.drop(book, false);
                }
            }
        }
    }
}
