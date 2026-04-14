package unsa.st.com.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.level.Level;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class GuideBookItem extends WrittenBookItem {
    public GuideBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.has(DataComponents.WRITTEN_BOOK_CONTENT)) {
            // 设置书本内容
            var content = new net.minecraft.world.item.component.WrittenBookContent(
                Component.translatable("st.guide.title"),
                "UNSA-STUDIO",
                0,
                createPages(),
                true
            );
            stack.set(DataComponents.WRITTEN_BOOK_CONTENT, content);
        }
        return super.use(level, player, hand);
    }

    private static java.util.List<Component> createPages() {
        return java.util.List.of(
            Component.literal(
                "=== Shortcut Terminal ===\n\n" +
                "Welcome to Shortcut Terminal!\n\n" +
                "This guide contains all commands.\n\n" +
                "Use /ST Help in chat to see them."
            ),
            Component.literal(
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
            ),
            Component.literal(
                "/ST User <player> <action> [params]\n\n" +
                "Actions:\n" +
                "- switchingmode <mode>\n" +
                "- changebirthpoint\n" +
                "- transmitto_online <x y z|home>\n" +
                "- transmitto_offline <x y z|home>\n\n" +
                "Example:\n" +
                "/ST User Steve switchingmode creative"
            )
        );
    }
}
