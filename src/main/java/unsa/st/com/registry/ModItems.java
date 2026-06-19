package unsa.st.com.registry;
npublic class ModItems {

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import unsa.st.com.ShortcutTerminal;
import unsa.st.com.item.TerminalPanelItem;

import java.util.List;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ShortcutTerminal.MODID);

    public static final DeferredItem<Item> TERMINAL_CORE = ITEMS.register("terminal_core",
            () -> new Item(new Item.Properties()) {
                @Override
                public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
                    tooltip.add(Component.translatable("st.terminal_core.desc"));
                }
            });

    public static final DeferredItem<Item> TERMINAL_PANEL = ITEMS.register("terminal_panel",
            () -> new TerminalPanelItem(new Item.Properties().stacksTo(1)) {
                @Override
                public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
                    tooltip.add(Component.translatable("st.terminal_panel.desc"));
                }
            });

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}

    public static final Supplier<Item> CLOUD_CORE = ITEMS.register("cloud_core", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> RECEIVER = ITEMS.register("receiver", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> RADAR = ITEMS.register("radar", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> CLOUD_STORAGE_MANAGER = ITEMS.register("cloud_storage_manager", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> REFINED_IRON = ITEMS.register("refined_iron", () -> new Item(new Item.Properties()));
}
