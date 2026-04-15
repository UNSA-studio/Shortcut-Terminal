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
            ItemStack book = PatchouliAPI.get().getBookStack(OUR_BOOK_ID);
            
            // 检查背包是否已有相同的书
            if (!player.getInventory().contains(book)) {
                if (!player.getInventory().add(book)) {
                    player.drop(book, false);
                }
            }
        }
    }
}
