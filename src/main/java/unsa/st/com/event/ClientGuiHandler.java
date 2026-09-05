package unsa.st.com.event;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import unsa.st.com.ShortcutTerminal;
import unsa.st.com.client.LithographyMachineScreen;

/** 客户端 GUI 注册。 */
@EventBusSubscriber(modid = ShortcutTerminal.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class ClientGuiHandler {
    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(unsa.st.com.registry.ModBlockEntities.LITHOGRAPHY_MACHINE_MENU.get(),
                LithographyMachineScreen::new);
    }
}