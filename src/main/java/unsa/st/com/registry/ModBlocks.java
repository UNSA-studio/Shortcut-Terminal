package unsa.st.com.registry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import unsa.st.com.ShortcutTerminal;
import unsa.st.com.block.LithographyMachineBlock;
import unsa.st.com.block.MachineCasingBlock;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ShortcutTerminal.MODID);

    /** 光刻机控制器（多方块核心）。 */
    public static final DeferredBlock<LithographyMachineBlock> LITHOGRAPHY_MACHINE =
            BLOCKS.register("lithography_machine",
                    () -> new LithographyMachineBlock(
                            BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                                    .strength(3.5f, 8.0f).requiresCorrectToolForDrops()));

    /** 光刻机外壳。 */
    public static final DeferredBlock<MachineCasingBlock> MACHINE_CASING =
            BLOCKS.register("machine_casing",
                    () -> new MachineCasingBlock(
                            BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                                    .strength(3.0f, 6.0f).requiresCorrectToolForDrops()));

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}