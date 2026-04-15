package unsa.st.com.event;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public class PlayerJoinHandler {
    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 检查玩家背包是否已有我们的手册
            boolean hasBook = player.getInventory().items.stream().anyMatch(stack -> {
                if (stack.getItem() == Items.WRITTEN_BOOK && stack.hasTag()) {
                    CompoundTag tag = stack.getTag();
                    // 检查 patchouli:book 标签是否等于我们的书籍ID
                    return tag != null && tag.contains("patchouli:book") &&
                           "st:st_guide".equals(tag.getString("patchouli:book"));
                }
                return false;
            });

            if (!hasBook) {
                // 创建一本成书，并通过NBT将其标记为Patchouli手册
                ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
                CompoundTag tag = book.getOrCreateTag();
                tag.putString("patchouli:book", "st:st_guide");

                // 给书一个显眼的名称，方便识别（可选）
                tag.putString("title", "Shortcut Terminal Guide");
                tag.putString("author", "UNSA-STUDIO");

                // 将书放入玩家背包，如果背包满了就掉落在脚边
                if (!player.getInventory().add(book)) {
                    player.drop(book, false);
                }
            }
        }
    }
}
