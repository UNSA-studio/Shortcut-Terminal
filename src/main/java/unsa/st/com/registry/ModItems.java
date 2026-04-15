package unsa.st.com.registry;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import unsa.st.com.ShortcutTerminal;
import unsa.st.com.item.TerminalPanelItem;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ShortcutTerminal.MODID);

    // 终端核心：纯物品，无交互
    public static final DeferredItem<Item> TERMINAL_CORE = ITEMS.register("terminal_core",
            () -> new Item(new Item.Properties()));

    // 终端面板：右键打开终端 GUI
    public static final DeferredItem<Item> TERMINAL_PANEL = ITEMS.register("terminal_panel",
            () -> new TerminalPanelItem(new Item.Properties().stacksTo(1)));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
