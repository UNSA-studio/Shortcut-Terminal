package unsa.st.com.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class TerminalScreen extends Screen {
    private static final int BORDER_COLOR = 0xFFFFFFFF; // 白色边框
    private static final int BG_COLOR = 0xFF000000;     // 黑色背景
    private static final int TEXT_COLOR = 0xFF55FF55;   // 绿色文字

    private StringBuilder inputBuffer = new StringBuilder();
    private String prompt = "~/User $ ";

    public TerminalScreen() {
        super(Component.literal("Terminal"));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 绘制黑色背景
        guiGraphics.fill(0, 0, this.width, this.height, BG_COLOR);
        
        // 绘制白色边框（一个像素宽的矩形）
        guiGraphics.renderOutline(0, 0, this.width, this.height, BORDER_COLOR);
        
        // 绘制命令提示符和输入内容
        String displayText = prompt + inputBuffer.toString();
        int y = this.height - 20;
        guiGraphics.drawString(this.font, displayText, 10, y, TEXT_COLOR);
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        inputBuffer.append(codePoint);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 259) { // 退格键
            if (!inputBuffer.isEmpty()) {
                inputBuffer.deleteCharAt(inputBuffer.length() - 1);
            }
            return true;
        }
        if (keyCode == 257 || keyCode == 335) { // 回车键
            executeCommand(inputBuffer.toString());
            inputBuffer.setLength(0);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void executeCommand(String command) {
        // 这里可以调用命令执行器，暂时仅显示反馈
        // 实际应该发包到服务端执行，这里简化为客户端占位
        // 后续可扩展
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
