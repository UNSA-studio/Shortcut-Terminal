package unsa.st.com.event;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

public class PlayerJoinHandler {
    private static final String PATCHOULI_MOD_ID = "patchouli";
    private static final ResourceLocation OUR_BOOK_ID = ResourceLocation.fromNamespaceAndPath("shortcutterminal", "st_guide");
    private static final String BOOK_GIVEN_TAG = "shortcutterminal_book_given";

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!ModList.get().isLoaded(PATCHOULI_MOD_ID)) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            if (hasReceivedBook(player)) {
                return;
            }
            
            boolean hasBook = player.getInventory().items.stream()
                    .anyMatch(stack -> {
                        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
                        return customData != null &&
                                OUR_BOOK_ID.toString().equals(customData.copyTag().getString("patchouli:book"));
                    });
            
            if (!hasBook) {
                ItemStack book = getPatchouliBookSafely(OUR_BOOK_ID);
                if (!book.isEmpty()) {
                    if (!player.getInventory().add(book)) {
                        player.drop(book, false);
                    }
                }
            }
            markBookReceived(player);
        }
    }

    private boolean hasReceivedBook(ServerPlayer player) {
        return player.getPersistentData().contains(BOOK_GIVEN_TAG);
    }

    private void markBookReceived(ServerPlayer player) {
        player.getPersistentData().putBoolean(BOOK_GIVEN_TAG, true);
    }

    private static ItemStack getPatchouliBookSafely(ResourceLocation bookId) {
        try {
            Class<?> apiClass = Class.forName("vazkii.patchouli.api.PatchouliAPI");
            Method getMethod = apiClass.getMethod("get");
            Object api = getMethod.invoke(null);
            Method getBookStackMethod = api.getClass().getMethod("getBookStack", ResourceLocation.class);
            Object result = getBookStackMethod.invoke(api, bookId);
            return (result instanceof ItemStack stack) ? stack : ItemStack.EMPTY;
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }
}
