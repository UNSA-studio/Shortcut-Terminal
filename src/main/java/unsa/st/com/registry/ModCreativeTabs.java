package unsa.st.com.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import unsa.st.com.ShortcutTerminal;

import java.util.function.Supplier;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ShortcutTerminal.MODID);

    public static final Supplier<CreativeModeTab> SHORTCUT_TERMINAL_TAB = CREATIVE_MODE_TABS.register(
            "shortcut_terminal_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.shortcutterminal"))
                    .icon(() -> new ItemStack(Items.COMMAND_BLOCK))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.TERMINAL_CORE.get());
                        output.accept(ModItems.TERMINAL_PANEL.get());
                    })
                    .build()
    );

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
