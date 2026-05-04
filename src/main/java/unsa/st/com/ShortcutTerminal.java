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
import unsa.st.com.event.PlayerJoinHandler;
import unsa.st.com.registry.ModItems;
import unsa.st.com.registry.ModBlocks;
import unsa.st.com.registry.ModCreativeTabs;
import unsa.st.com.plugin.BinaryPluginManager;
import unsa.st.com.util.OfflineTeleportManager;
import unsa.st.com.network.ModNetwork;
import unsa.st.com.pkg.PackageManager;
import unsa.st.com.remote.RemoteControlManager;
import unsa.st.com.terminal.TerminalIdManager;
import unsa.st.com.remote.RemoteControlManager;
import unsa.st.com.terminal.TerminalIdManager;

@Mod(ShortcutTerminal.MODID)
public class ShortcutTerminal {
    public static final String MODID = "shortcutterminal";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ShortcutTerminal(IEventBus modEventBus) {
        LOGGER.info("Shortcut Terminal Mod initializing...");

        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        
        BinaryPluginManager.init();
        OfflineTeleportManager.init();
        PackageManager.init();
        RemoteControlManager.init();
        TerminalIdManager.init();
        RemoteControlManager.init();
        TerminalIdManager.init();

        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(new TerminalManager());
        NeoForge.EVENT_BUS.register(new PlayerJoinHandler());
        NeoForge.EVENT_BUS.register(new OfflineTeleportManager());
        modEventBus.register(ModNetwork.class);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher());
        LOGGER.info("Shortcut Terminal commands registered");
    }
}
