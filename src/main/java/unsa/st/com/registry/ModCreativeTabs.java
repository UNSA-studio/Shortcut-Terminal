package unsa.st.com.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
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
                    .icon(() -> new ItemStack(ModItems.TERMINAL_PANEL.get()))
                    .displayItems((params, output) -> {
                        // Tier 0: raw materials
                        output.accept(ModItems.RAW_SILICON.get());
                        output.accept(ModItems.SILICON_WAFER.get());
                        output.accept(ModItems.LOGIC_WAFER.get());
                        output.accept(ModItems.LOGIC_CHIP.get());
                        output.accept(ModItems.REFINED_IRON.get());
                        output.accept(ModItems.COPPER_WIRE.get());
                        // Tier 1: components
                        output.accept(ModItems.CIRCUIT_BOARD.get());
                        output.accept(ModItems.ADVANCED_CIRCUIT.get());
                        output.accept(ModItems.PROCESSING_UNIT.get());
                        output.accept(ModItems.MEMORY_BANK.get());
                        output.accept(ModItems.DISPLAY_SCREEN.get());
                        output.accept(ModItems.POWER_COIL.get());
                        // Final products
                        output.accept(ModItems.TERMINAL_CORE.get());
                        output.accept(ModItems.TERMINAL_PANEL.get());
                    })
                    .build()
    );

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}