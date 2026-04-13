package unsa.st.com.registry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import unsa.st.com.ShortcutTerminal;
import unsa.st.com.block.TerminalPanelBlock;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ShortcutTerminal.MODID);

    public static final DeferredBlock<Block> TERMINAL_PANEL = BLOCKS.register("terminal_panel",
            () -> new TerminalPanelBlock(BlockBehaviour.Properties.of().strength(2.0f).requiresCorrectToolForDrops()));

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
