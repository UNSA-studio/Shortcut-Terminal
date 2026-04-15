package unsa.st.com.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import vazkii.patchouli.api.PatchouliAPI;

public class PlayerJoinHandler {
    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 检查玩家背包是否已有我们的指南书
            boolean hasBook = player.getInventory().items.stream()
                    .anyMatch(stack -> stack.getItem() == PatchouliAPI.get().getBookItem() &&
                            "st:st_guide".equals(PatchouliAPI.get().getBookId(stack)));
            
            if (!hasBook) {
                // 使用 Patchouli API 直接生成书籍
                ItemStack book = PatchouliAPI.get().getBookStack("st:st_guide");
                if (!player.getInventory().add(book)) {
                    player.drop(book, false);
                }
            }
        }
    }
}
