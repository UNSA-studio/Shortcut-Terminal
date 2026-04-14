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
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.util.Filterable;

import java.util.List;
import java.util.stream.Collectors;

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
        // 构建书本内容
        WrittenBookContent content = new WrittenBookContent(
            Filterable.passThrough("Shortcut Terminal Guide"),
            Filterable.passThrough("UNSA-STUDIO"),
            0, // generation
            createPages(),
            true // resolved
        );
        stack.set(DataComponents.WRITTEN_BOOK_CONTENT, content);
    }

    private List<Filterable<Component>> createPages() {
        return List.of(
            Filterable.passThrough(Component.literal(
                "=== Shortcut Terminal ===\n\n" +
                "Welcome to Shortcut Terminal!\n\n" +
                "This guide contains all commands.\n\n" +
                "Use /ST Help in chat to see them."
            )),
            Filterable.passThrough(Component.literal(
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
            )),
            Filterable.passThrough(Component.literal(
                "/ST User <player> <action> [params]\n\n" +
                "Actions:\n" +
                "- switchingmode <mode>\n" +
                "- changebirthpoint\n" +
                "- transmitto_online <x y z|home>\n" +
                "- transmitto_offline <x y z|home>\n\n" +
                "Example:\n" +
                "/ST User Steve switchingmode creative"
            ))
        );
    }
}
