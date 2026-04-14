package unsa.st.com.registry;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import unsa.st.com.ShortcutTerminal;
import unsa.st.com.item.GuideBookItem;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ShortcutTerminal.MODID);

    public static final DeferredItem<Item> TERMINAL_CORE = ITEMS.register("terminal_core",
            () -> new Item(new Item.Properties()));

    public static final DeferredItem<BlockItem> TERMINAL_PANEL = ITEMS.register("terminal_panel",
            () -> new BlockItem(ModBlocks.TERMINAL_PANEL.get(), new Item.Properties()));

    public static final DeferredItem<Item> GUIDE_BOOK = ITEMS.register("guide_book",
            () -> new GuideBookItem(new Item.Properties().stacksTo(1)));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
