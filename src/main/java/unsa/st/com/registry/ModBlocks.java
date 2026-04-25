package unsa.st.com.registry;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import unsa.st.com.ShortcutTerminal;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ShortcutTerminal.MODID);

    // 目前没有方块，保留空注册以便后续扩展

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
