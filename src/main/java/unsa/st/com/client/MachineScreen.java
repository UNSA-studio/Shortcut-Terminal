package unsa.st.com.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/** Shared inventory-screen drawing. Slot frames use the menu's coordinates, not a second layout. */
public abstract class MachineScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {
    protected MachineScreen(M menu, Inventory inventory, Component title, int width, int height, int labelY) {
        super(menu, inventory, title);
        imageWidth = width; imageHeight = height;
        titleLabelX = 10; titleLabelY = 8;
        inventoryLabelX = 26; inventoryLabelY = labelY;
    }
    @Override public void render(GuiGraphics g, int x, int y, float partialTick) {
        renderBackground(g, x, y, partialTick);
        super.render(g, x, y, partialTick);
        renderTooltip(g, x, y);
        if (hoveredSlot != null && !hoveredSlot.hasItem()) {
            int index = menu.slots.indexOf(hoveredSlot);
            String key = emptySlotKey(index);
            if (key != null) g.renderTooltip(font, Component.translatable(key), x, y);
        }
    }
    protected abstract String emptySlotKey(int index);
    protected void frame(GuiGraphics g, int inputs) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFF202733);
        g.renderOutline(x, y, imageWidth, imageHeight, 0xFF738395);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + 22, 0xFF354354);
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            int sx = x + slot.x - 1, sy = y + slot.y - 1;
            int edge = i < inputs ? 0xFF64C8D2 : 0xFF708090;
            g.fill(sx, sy, sx + 18, sy + 18, 0xFF0F151E);
            g.renderOutline(sx, sy, 18, 18, edge);
        }
    }
    protected void text(GuiGraphics g, Component text, int x, int y, int width, int color) {
        g.drawString(font, font.substrByWidth(text, width), x, y, color, false);
    }
    @Override protected void renderLabels(GuiGraphics g, int x, int y) {
        text(g, title, titleLabelX, titleLabelY, imageWidth - 20, 0xFFF1F5F9);
        text(g, playerInventoryTitle, inventoryLabelX, inventoryLabelY, imageWidth - 36, 0xFFB8C4D0);
    }
}