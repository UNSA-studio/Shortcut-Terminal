package unsa.st.com.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import vazkii.patchouli.api.PatchouliAPI;

public class PlayerJoinHandler {
    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ResourceLocation ourBookId = ResourceLocation.fromNamespaceAndPath("st", "st_guide");
            
            // 检查玩家背包是否已包含我们的 Patchouli 指南书
            boolean hasBook = player.getInventory().items.stream()
                    .anyMatch(stack -> stack.getItem() == Items.WRITTEN_BOOK &&
                            ourBookId.equals(stack.get(PatchouliAPI.BOOK_COMPONENT)));
            
            if (!hasBook) {
                ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
                book.set(PatchouliAPI.BOOK_COMPONENT, ourBookId);
                
                if (!player.getInventory().add(book)) {
                    player.drop(book, false);
                }
            }
        }
    }
}
