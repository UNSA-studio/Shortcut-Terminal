package unsa.st.com.registry;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import unsa.st.com.ShortcutTerminal;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ShortcutTerminal.MODID);

    // 原有的物品
    public static final DeferredItem<Item> TERMINAL_PANEL = ITEMS.register("terminal_panel",
            () -> new Item(new Item.Properties().stacksTo(1)));

    // 新增的 Cloud Storage 系列物品
    public static final DeferredItem<Item> CLOUD_STORAGE_MANAGER = ITEMS.register("cloud_storage_manager",
            () -> new Item(new Item.Properties().stacksTo(1).fireResistant()));
    public static final DeferredItem<Item> CLOUD_CORE = ITEMS.register("cloud_core",
            () -> new Item(new Item.Properties().stacksTo(1).fireResistant()));
    public static final DeferredItem<Item> RADAR = ITEMS.register("radar",
            () -> new Item(new Item.Properties().stacksTo(64)));
    public static final DeferredItem<Item> RECEIVER = ITEMS.register("receiver",
            () -> new Item(new Item.Properties().stacksTo(64)));
}
