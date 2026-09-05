package unsa.st.com.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import unsa.st.com.menu.LithographyMachineMenu;

/**
 * 光刻机 GUI：进度条 + 槽位。
 * 占位贴图策略：背景用纯色填充（与项目现行原版贴图占位策略一致）。
 */
public class LithographyMachineScreen extends AbstractContainerScreen<LithographyMachineMenu> {
    private static final int IMAGE_WIDTH = 176;
    private static final int IMAGE_HEIGHT = 166;

    public LithographyMachineScreen(LithographyMachineMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = IMAGE_WIDTH;
        this.imageHeight = IMAGE_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        renderBackground(gfx, mouseX, mouseY, partialTick);
        super.render(gfx, mouseX, mouseY, partialTick);
        renderTooltip(gfx, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        // 占位背景：深灰面板 + 边框（等专属贴图后替换为 blit）
        gfx.fill(x, y, x + imageWidth, y + imageHeight, 0xFF2B2B2B);
        gfx.renderOutline(x, y, imageWidth, imageHeight, 0xFF777777);

        // 机器槽区域
        gfx.renderOutline(x + 40, y + 30, 118, 22, 0xFF555555);
        gfx.drawString(this.font, "Wafer   Mask   Coil   Output", x + 42, y + 20, 0xFFAAAAAA, false);

        // 进度条
        if (menu.machine != null) {
            int progress = menu.machine.getProgress();
            int total = menu.machine.getProcessTicks();
            int barW = 100;
            int filled = total > 0 ? (int) ((progress / (float) total) * barW) : 0;
            gfx.fill(x + 38, y + 62, x + 38 + barW, y + 68, 0xFF444444);
            if (filled > 0) gfx.fill(x + 38, y + 62, x + 38 + filled, y + 68, 0xFF55FF55);
            gfx.drawString(this.font, progress + " / " + total + " ticks", x + 38, y + 72, 0xFFAAAAAA, false);
            if (menu.machine.getTargetLevel() > 0) {
                gfx.drawString(this.font, "Target: L" + menu.machine.getTargetLevel(), x + 38, y + 84, 0xFFFFFF55, false);
            }
        }
    }
}