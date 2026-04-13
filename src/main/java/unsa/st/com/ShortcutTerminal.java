package unsa.st.com;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;
import unsa.st.com.command.ModCommands;
import unsa.st.com.terminal.TerminalManager;
import unsa.st.com.event.ClientEventHandler;
import unsa.st.com.registry.ModItems;
import unsa.st.com.registry.ModBlocks;
import unsa.st.com.plugin.BinaryPluginManager;

@Mod(ShortcutTerminal.MODID)
public class ShortcutTerminal {
    public static final String MODID = "st";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ShortcutTerminal(IEventBus modEventBus) {
        LOGGER.info("Shortcut Terminal Mod initializing...");

        // 注册物品和方块
        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);

        // 初始化二进制插件系统
        BinaryPluginManager.init();

        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(new TerminalManager());
        NeoForge.EVENT_BUS.register(new ClientEventHandler());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher());
        LOGGER.info("Shortcut Terminal commands registered");
    }
}
