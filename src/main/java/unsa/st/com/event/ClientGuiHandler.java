package unsa.st.com.event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import unsa.st.com.client.AssemblyBenchScreen;
import unsa.st.com.client.LithographyMachineScreen;
import unsa.st.com.registry.ModBlockEntities;
public final class ClientGuiHandler {
    private ClientGuiHandler() {}
    public static void register(IEventBus bus) { bus.addListener(ClientGuiHandler::registerScreens); }
    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModBlockEntities.LITHOGRAPHY_MACHINE_MENU.get(), LithographyMachineScreen::new);
        event.register(ModBlockEntities.ASSEMBLY_BENCH_MENU.get(), AssemblyBenchScreen::new);
    }
}
