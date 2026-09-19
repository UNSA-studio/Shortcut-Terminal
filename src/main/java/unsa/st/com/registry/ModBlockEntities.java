package unsa.st.com.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import unsa.st.com.ShortcutTerminal;
import unsa.st.com.block.AssemblyBenchBlockEntity;
import unsa.st.com.block.LithographyMachineBlockEntity;
import unsa.st.com.menu.AssemblyBenchMenu;
import unsa.st.com.menu.LithographyMachineMenu;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ShortcutTerminal.MODID);

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, ShortcutTerminal.MODID);

    /** 光刻机方块实体。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LithographyMachineBlockEntity>> LITHOGRAPHY_MACHINE =
            BLOCK_ENTITIES.register("lithography_machine",
                    () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder
                            .of(LithographyMachineBlockEntity::new, ModBlocks.LITHOGRAPHY_MACHINE.get())
                            .build(null));

    /** 光刻机菜单。 */
    public static final DeferredHolder<MenuType<?>, MenuType<LithographyMachineMenu>> LITHOGRAPHY_MACHINE_MENU =
            MENUS.register("lithography_machine",
                    () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(LithographyMachineMenu::new));

    /** 制造台方块实体。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AssemblyBenchBlockEntity>> ASSEMBLY_BENCH =
            BLOCK_ENTITIES.register("assembly_bench",
                    () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder
                            .of(AssemblyBenchBlockEntity::new, ModBlocks.ASSEMBLY_BENCH.get())
                            .build(null));

    /** 制造台菜单。 */
    public static final DeferredHolder<MenuType<?>, MenuType<AssemblyBenchMenu>> ASSEMBLY_BENCH_MENU =
            MENUS.register("assembly_bench",
                    () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(AssemblyBenchMenu::new));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
        MENUS.register(eventBus);
    }
}