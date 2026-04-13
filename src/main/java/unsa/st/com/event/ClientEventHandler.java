package unsa.st.com.event;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientChatEvent;
import unsa.st.com.terminal.TerminalManager;

public class ClientEventHandler {
    @SubscribeEvent
    public void onClientChat(ClientChatEvent event) {
        String message = event.getMessage();
        if (TerminalManager.handleChatInput(message, event.getPlayer())) {
            event.setCanceled(true);
        }
    }
}
