package unsa.st.com.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.network.IContainerFactory;
import unsa.st.com.ShortcutTerminal;
import unsa.st.com.block.LithographyMachineBlockEntity;
import unsa.st.com.menu.LithographyMachineMenu;

// NOTE: IMenuTypeExtension referenced fully-qualified to avoid import ordering issues.

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ShortcutTerminal.MODID);

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, ShortcutTerminal.MODID);

    /** 光刻机方块实体。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LithographyMachineBlockEntity>> LITHOGRAPHY_MACHINE =
            BLOCK_ENTITIES.register("lithography_machine",
                    () -> new BlockEntityType<>(
                            LithographyMachineBlockEntity::new,
                            net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(
                                    LithographyMachineBlockEntity::new,
                                    ModBlocks.LITHOGRAPHY_MACHINE.get()).build(null)));

    /** 光刻机菜单。 */
    public static final DeferredHolder<MenuType<?>, MenuType<LithographyMachineMenu>> LITHOGRAPHY_MACHINE_MENU =
            MENUS.register("lithography_machine",
                    () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(LithographyMachineMenu::new));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
        MENUS.register(eventBus);
    }
}