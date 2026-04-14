package unsa.st.com.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.level.Level;

public class GuideBookItem extends WrittenBookItem {
    public GuideBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // 如果书本没有内容，则写入预设内容
        if (!stack.has(DataComponents.WRITTEN_BOOK_CONTENT)) {
            setupBookContent(stack);
        }
        return super.use(level, player, hand);
    }

    private void setupBookContent(ItemStack stack) {
        // 通过 NBT 设置书本内容
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString("author", "UNSA-STUDIO");
        tag.putString("title", Component.translatable("st.guide.title").getString());
        
        ListTag pages = new ListTag();
        pages.add(StringTag.valueOf(Component.literal(
            "=== Shortcut Terminal ===\n\n" +
            "Welcome to Shortcut Terminal!\n\n" +
            "This guide contains all commands.\n\n" +
            "Use /ST Help in chat to see them."
        ).getString()));
        pages.add(StringTag.valueOf(Component.literal(
            "/ST ls - List files\n" +
            "/ST mkdir <name> - Create directory\n" +
            "/ST touch <name> - Create file\n" +
            "/ST rm <name> - Remove file\n" +
            "/ST cat <name> - View file\n" +
            "/ST cd <path> - Change directory\n" +
            "/ST pwd - Show current path\n" +
            "/ST whoami - Show username\n" +
            "/ST clear - Clear screen\n" +
            "/ST refresh bf - Refresh plugins"
        ).getString()));
        pages.add(StringTag.valueOf(Component.literal(
            "/ST User <player> <action> [params]\n\n" +
            "Actions:\n" +
            "- switchingmode <mode>\n" +
            "- changebirthpoint\n" +
            "- transmitto_online <x y z|home>\n" +
            "- transmitto_offline <x y z|home>\n\n" +
            "Example:\n" +
            "/ST User Steve switchingmode creative"
        ).getString()));
        tag.put("pages", pages);
    }
}
