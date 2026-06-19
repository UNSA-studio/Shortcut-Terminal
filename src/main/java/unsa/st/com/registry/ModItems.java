package unsa.st.com.registry;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;
import unsa.st.com.ShortcutTerminal;
import unsa.st.com.item.TerminalPanelItem;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ShortcutTerminal.MODID);

    public static final DeferredItem<Item> TERMINAL_CORE = ITEMS.register("terminal_core",
            () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> TERMINAL_PANEL = ITEMS.register("terminal_panel",
            () -> new TerminalPanelItem(new Item.Properties()));
    public static final DeferredItem<Item> CLOUD_CORE = ITEMS.register("cloud_core",
            () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> RADAR = ITEMS.register("radar",
            () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> RECEIVER = ITEMS.register("receiver",
            () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> CLOUD_STORAGE_MANAGER = ITEMS.register("cloud_storage_manager",
            () -> new Item(new Item.Properties()));
}
