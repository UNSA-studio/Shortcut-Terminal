package unsa.st.com.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import unsa.st.com.menu.AssemblyBenchMenu;

/** 制造台 GUI：深色工业面板 + 有序装配槽位。 */
public class AssemblyBenchScreen extends AbstractContainerScreen<AssemblyBenchMenu> {

    public AssemblyBenchScreen(AssemblyBenchMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
        this.titleLabelX = 8;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFF1B1B1B);
        g.renderOutline(x, y, imageWidth, imageHeight, 0xFF888888);
        // 装配槽底衬
        for (int i = 0; i <= 6; i++) {
            int sx = x + 7 + i * 20, sy = y + 19;
            g.fill(sx, sy, sx + 18, sy + 18, 0xFF3A3A3A);
        }
        // 输出槽
        g.fill(x + 151, y + 19, x + 169, y + 37, 0xFF4A3A2A);
        // 玩家背包
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                g.fill(x + 7 + col * 18, y + 83 + row * 18, x + 25 + col * 18, y + 101 + row * 18, 0xFF3A3A3A);
        for (int col = 0; col < 9; col++)
            g.fill(x + 7 + col * 18, y + 141, x + 25 + col * 18, y + 159, 0xFF3A3A3A);
    }

    /** 装配顺序提示（标题下方）。 */
    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        g.drawString(this.font, "shell > board > cpu > ram > ssd > screen > glue",
                this.titleLabelX, 44, 0xFF7A7A7A, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltip(g, mouseX, mouseY);
    }
}
