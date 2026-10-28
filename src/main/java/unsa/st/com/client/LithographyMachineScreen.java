package unsa.st.com.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import unsa.st.com.menu.LithographyMachineMenu;
import unsa.st.com.block.LithographyMachineBlockEntity;

public class LithographyMachineScreen extends MachineScreen<LithographyMachineMenu> {
    public LithographyMachineScreen(LithographyMachineMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, LithographyMachineMenu.WIDTH, LithographyMachineMenu.HEIGHT, 104);
    }
    @Override protected String emptySlotKey(int i) {
        return switch (i) {
            case 0 -> "gui.shortcutterminal.wafer"; case 1 -> "gui.shortcutterminal.mask";
            case 2 -> "gui.shortcutterminal.coil"; case 3,4,5 -> "gui.shortcutterminal.output"; default -> null;
        };
    }
    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        frame(g, 3);
        for (int i = 0; i < 3; i++)
            text(g, Component.translatable(emptySlotKey(i)), leftPos + 12 + i * 32, topPos + 27, 30, 0xFFB8CEDB);
        text(g, Component.translatable("gui.shortcutterminal.output"), leftPos + 129, topPos + 27, 70, 0xFFB8CEDB);
        String status = !menu.isFormed() ? "structure" : menu.isWorking() ? "running"
                : menu.outputBlocked() ? "collect" : "idle";
        text(g, Component.translatable("gui.shortcutterminal.litho." + status), leftPos + 10, topPos + 64, imageWidth - 20,
                menu.isFormed() ? 0xFF9EDDDD : 0xFFFFCC77);
        int percent = Math.min(100, menu.getProgress() * 100 / LithographyMachineBlockEntity.PROCESS_TICKS);
        g.fill(leftPos + 12, topPos + 79, leftPos + imageWidth - 12, topPos + 87, 0xFF111923);
        g.fill(leftPos + 12, topPos + 79, leftPos + 12 + (imageWidth - 24) * percent / 100, topPos + 87, 0xFF4EB8BA);
        text(g, Component.translatable("gui.shortcutterminal.litho.progress", percent, menu.getTargetLevel(), menu.getFuelBatches()),
                leftPos + 12, topPos + 91, imageWidth - 24, 0xFFB8CEDB);
    }
}
