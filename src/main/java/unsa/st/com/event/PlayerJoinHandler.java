package unsa.st.com.event;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public class PlayerJoinHandler {
    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            String ourBookId = "st:st_guide";

            // 检查玩家背包是否已有我们的手册
            boolean hasBook = player.getInventory().items.stream().anyMatch(stack -> {
                if (stack.getItem() == Items.WRITTEN_BOOK) {
                    CompoundTag customData = stack.get(DataComponents.CUSTOM_DATA);
                    return customData != null && ourBookId.equals(customData.getString("patchouli:book"));
                }
                return false;
            });

            if (!hasBook) {
                ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
                
                // 通过 CUSTOM_DATA 组件附加 patchouli:book 标识
                CompoundTag customData = new CompoundTag();
                customData.putString("patchouli:book", ourBookId);
                book.set(DataComponents.CUSTOM_DATA, customData);

                // 设置成书的基本内容（可选）
                book.set(DataComponents.WRITTEN_BOOK_CONTENT, 
                    new net.minecraft.world.item.component.WrittenBookContent(
                        net.minecraft.util.Filterable.passThrough("Shortcut Terminal Guide"),
                        net.minecraft.util.Filterable.passThrough("UNSA-STUDIO"),
                        0,
                        java.util.List.of(),
                        true
                    )
                );

                if (!player.getInventory().add(book)) {
                    player.drop(book, false);
                }
            }
        }
    }
}
