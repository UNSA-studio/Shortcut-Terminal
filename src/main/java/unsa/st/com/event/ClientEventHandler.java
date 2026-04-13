package unsa.st.com.event;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientChatEvent;
import unsa.st.com.terminal.TerminalManager;

public class ClientEventHandler {
    @SubscribeEvent
    public void onClientChat(ClientChatEvent event) {
        String message = event.getMessage();
        var player = Minecraft.getInstance().player;
        if (player != null && TerminalManager.handleChatInput(message, player)) {
            event.setCanceled(true);
        }
    }
}
