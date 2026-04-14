package unsa.st.com.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class GuideBookScreen extends Screen {
    public GuideBookScreen() {
        super(Component.translatable("st.guide.title"));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        
        String[] lines = {
            "=== Shortcut Terminal Commands ===",
            "/ST Help - Show this help",
            "/ST ls - List files",
            "/ST mkdir <name> - Create directory",
            "/ST touch <name> - Create file",
            "/ST rm <name> - Remove file/directory",
            "/ST cat <name> - Show file contents",
            "/ST cd <path> - Change directory",
            "/ST pwd - Print working directory",
            "/ST whoami - Show username",
            "/ST clear - Clear screen",
            "/ST User <player> <action> [params] - Admin",
            "/ST refresh bf - Refresh binary plugins",
            "/ST open terminal page - Enter terminal mode"
        };
        
        int y = 40;
        for (String line : lines) {
            guiGraphics.drawString(this.font, line, 20, y, 0xCCCCCC);
            y += 12;
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
