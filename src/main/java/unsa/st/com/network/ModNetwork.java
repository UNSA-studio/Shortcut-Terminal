package unsa.st.com.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import unsa.st.com.ShortcutTerminal;

public class ModNetwork {
    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(ShortcutTerminal.MODID);
        registrar.playToClient(
                BlackScreenPayload.TYPE,
                BlackScreenPayload.STREAM_CODEC,
                BlackScreenPayload::handleClient
        );
    }
}
