package unsa.st.com.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import unsa.st.com.menu.AssemblyBenchMenu;

public class AssemblyBenchScreen extends MachineScreen<AssemblyBenchMenu> {
    private Button assembleButton;
    private static final String[] PARTS = {"shell", "board", "cpu", "ram", "ssd", "screen", "glue", "output"};
    public AssemblyBenchScreen(AssemblyBenchMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, AssemblyBenchMenu.WIDTH, AssemblyBenchMenu.HEIGHT, 112);
        inventoryLabelX = 34;
    }
    @Override protected void init() {
        super.init();
        assembleButton = addRenderableWidget(Button.builder(Component.translatable("gui.shortcutterminal.assemble"), b -> {
            if (minecraft != null && minecraft.gameMode != null && menu.canAssemble())
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
        }).bounds(leftPos + 132, topPos + 91, 88, 18).build());
        assembleButton.active = menu.canAssemble();
    }
    @Override public void containerTick() {
        super.containerTick();
        if (assembleButton != null) assembleButton.active = menu.canAssemble();
    }
    @Override protected String emptySlotKey(int i) {
        return i >= 0 && i < PARTS.length ? "gui.shortcutterminal." + PARTS[i] : null;
    }
    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        frame(g, 7);
        for (int i = 0; i < PARTS.length; i++) {
            int[] p = AssemblyBenchMenu.POSITIONS[i];
            text(g, Component.translatable(emptySlotKey(i)), leftPos + p[0] - 2, topPos + p[1] - 12,
                    i == 7 ? 30 : 28, 0xFFB8CEDB);
        }
        int stage = menu.nextStage();
        Component status = stage < 7 ? Component.translatable("gui.shortcutterminal.next", stage + 1,
                Component.translatable(emptySlotKey(stage))) : Component.translatable("gui.shortcutterminal.ready");
        text(g, status, leftPos + 10, topPos + 100, 118, 0xFF9EDDDD);
        g.drawString(font, ">>", leftPos + 171, topPos + 68, 0xFF7BE0D7, false);
    }
}
