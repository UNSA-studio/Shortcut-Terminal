package unsa.st.com.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class TerminalScreen extends Screen {
    private static final int BORDER_COLOR = 0xFFFFFFFF;
    private static final int BG_COLOR = 0xFF000000;
    private static final int TEXT_COLOR = 0xFF55FF55;

    // 书本风格的尺寸
    private static final int GUI_WIDTH = 220;
    private static final int GUI_HEIGHT = 180;

    private StringBuilder inputBuffer = new StringBuilder();
    private String prompt = "~/User $ ";

    private int leftPos;
    private int topPos;

    public TerminalScreen() {
        super(Component.literal("Terminal"));
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - GUI_WIDTH) / 2;
        this.topPos = (this.height - GUI_HEIGHT) / 2;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 不调用 super.render，完全自己绘制，避免背景模糊
        // 绘制黑色背景
        guiGraphics.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + GUI_HEIGHT, BG_COLOR);
        // 绘制白色边框
        guiGraphics.renderOutline(leftPos, topPos, GUI_WIDTH, GUI_HEIGHT, BORDER_COLOR);

        // 绘制命令提示符和输入内容
        String displayText = prompt + inputBuffer.toString();
        int y = topPos + GUI_HEIGHT - 15;
        guiGraphics.drawString(this.font, displayText, leftPos + 5, y, TEXT_COLOR);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        inputBuffer.append(codePoint);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 259) { // 退格
            if (!inputBuffer.isEmpty()) {
                inputBuffer.deleteCharAt(inputBuffer.length() - 1);
            }
            return true;
        }
        if (keyCode == 257 || keyCode == 335) { // 回车
            // 执行命令逻辑（可后续扩展）
            inputBuffer.setLength(0);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 空实现，完全阻止默认的背景渲染（避免模糊）
    }
}
