package unsa.st.com.registry;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import unsa.st.com.ShortcutTerminal;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ShortcutTerminal.MODID);

    // 终端核心是一个普通物品，不应该被放置
    public static final DeferredItem<Item> TERMINAL_CORE = ITEMS.register("terminal_core",
            () -> new Item(new Item.Properties()));

    // 终端面板是一个方块物品，可以被放置
    public static final DeferredItem<BlockItem> TERMINAL_PANEL = ITEMS.register("terminal_panel",
            () -> new BlockItem(ModBlocks.TERMINAL_PANEL.get(), new Item.Properties()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
