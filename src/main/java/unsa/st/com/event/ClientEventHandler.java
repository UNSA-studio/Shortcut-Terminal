package unsa.st.com.event;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import unsa.st.com.ShortcutTerminal;
import unsa.st.com.client.BlackScreenHandler;

@EventBusSubscriber(modid = ShortcutTerminal.MODID, value = Dist.CLIENT)
public class ClientEventHandler {
    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiLayerEvent.Post event) {
        if (BlackScreenHandler.isEnabled()) {
            var graphics = event.getGuiGraphics();
            int width = graphics.guiWidth();
            int height = graphics.guiHeight();
            graphics.fill(0, 0, width, height, 0xFF000000);
        }
    }
}