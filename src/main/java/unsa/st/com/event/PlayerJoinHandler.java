package unsa.st.com.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.fml.ModList;
import vazkii.patchouli.api.PatchouliAPI;

public class PlayerJoinHandler {
    private static final String PATCHOULI_MOD_ID = "patchouli";
    private static final ResourceLocation OUR_BOOK_ID = ResourceLocation.fromNamespaceAndPath("shortcutterminal", "st_guide");

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!ModList.get().isLoaded(PATCHOULI_MOD_ID)) {
            return;
        }

        if (event.getEntity() instanceof ServerPlayer player) {
            // 检查玩家背包是否已包含我们的指南书
            boolean hasBook = player.getInventory().items.stream()
                    .anyMatch(stack -> {
                        ResourceLocation bookId = PatchouliAPI.get().getOpenBook(stack);
                        return OUR_BOOK_ID.equals(bookId);
                    });

            if (!hasBook) {
                // 使用官方 API 获取书籍物品栈
                ItemStack book = PatchouliAPI.get().getBookStack(OUR_BOOK_ID);
                if (!player.getInventory().add(book)) {
                    player.drop(book, false);
                }
            }
        }
    }
}
