package unsa.st.com.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import unsa.st.com.network.ExecuteCommandPayload;

import java.util.ArrayList;
import java.util.List;

public class TerminalScreen extends Screen {
    private static final int BORDER_COLOR = 0xFFFFFFFF;
    private static final int BG_COLOR = 0xFF000000;
    private static final int TEXT_COLOR = 0xFF55FF55;
    private static final int PROMPT_COLOR = 0xFF55FFFF;
    private static final int GUI_WIDTH = 320;
    private static final int GUI_HEIGHT = 200;
    private static final int PADDING = 6;
    private static final int SCROLLBAR_WIDTH = 6;

    private int leftPos;
    private int topPos;
    private EditBox commandInput;
    
    // 输出缓冲区：每行是一个字符串
    private List<String> outputLines = new ArrayList<>();
    private double scrollOffset = 0;
    private boolean isDraggingScrollbar = false;
    
    // 命令历史
    private List<String> commandHistory = new ArrayList<>();
    private int historyIndex = -1;
    
    // 当前提示符（客户端本地维护，初始为 "~ $ "，之后由服务端响应更新）
    private String currentPrompt = "~ $ ";
    
    // 单例实例，用于接收服务端返回的结果
    private static TerminalScreen instance;

    public TerminalScreen() {
        super(Component.literal("Terminal"));
        instance = this;
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - GUI_WIDTH) / 2;
        this.topPos = (this.height - GUI_HEIGHT) / 2;

        this.commandInput = new EditBox(
                this.font,
                this.leftPos + PADDING,
                this.topPos + GUI_HEIGHT - this.font.lineHeight - PADDING,
                GUI_WIDTH - 2 * PADDING,
                this.font.lineHeight + 2,
                Component.literal("")
        );
        this.commandInput.setMaxLength(256);
        this.commandInput.setFocused(true);
        this.commandInput.setBordered(false);
        this.commandInput.setTextColor(TEXT_COLOR);
        this.commandInput.setCanLoseFocus(false);
        this.addRenderableWidget(this.commandInput);
        
        this.setInitialFocus(this.commandInput);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 绘制背景和边框
        guiGraphics.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + GUI_HEIGHT, BG_COLOR);
        guiGraphics.renderOutline(leftPos, topPos, GUI_WIDTH, GUI_HEIGHT, BORDER_COLOR);

        int outputStartX = leftPos + PADDING;
        int outputStartY = topPos + PADDING;
        int outputWidth = GUI_WIDTH - 2 * PADDING - SCROLLBAR_WIDTH;
        int outputHeight = GUI_HEIGHT - this.font.lineHeight - 4 * PADDING;
        int lineHeight = this.font.lineHeight + 1;
        int maxVisibleLines = outputHeight / lineHeight;
        
        int totalContentHeight = outputLines.size() * lineHeight;
        int maxScroll = Math.max(0, totalContentHeight - outputHeight);
        
        if (isMouseOver(mouseX, mouseY)) {
            // 滚轮逻辑在 mouseScrolled 中
        }
        
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        
        int startLine = (int) (scrollOffset / lineHeight);
        int endLine = Math.min(outputLines.size(), startLine + maxVisibleLines + 1);
        
        for (int i = startLine; i < endLine; i++) {
            int y = outputStartY + (i - startLine) * lineHeight - (int)(scrollOffset % lineHeight);
            String line = outputLines.get(i);
            
            int color = TEXT_COLOR;
            if (line.startsWith("~") || line.contains(" $ ")) {
                color = PROMPT_COLOR;
            } else if (line.startsWith("Error:") || line.startsWith("bash:")) {
                color = 0xFFFF5555;
            }
            guiGraphics.drawString(this.font, line, outputStartX, y, color);
        }
        
        if (maxScroll > 0) {
            int scrollbarX = leftPos + GUI_WIDTH - PADDING - SCROLLBAR_WIDTH;
            int scrollbarHeight = outputHeight;
            int thumbHeight = Math.max(10, (int)((float)outputHeight / totalContentHeight * scrollbarHeight));
            int thumbY = topPos + PADDING + (int)(scrollOffset / maxScroll * (scrollbarHeight - thumbHeight));
            guiGraphics.fill(scrollbarX, topPos + PADDING, scrollbarX + SCROLLBAR_WIDTH, topPos + PADDING + scrollbarHeight, 0xFF333333);
            guiGraphics.fill(scrollbarX, thumbY, scrollbarX + SCROLLBAR_WIDTH, thumbY + thumbHeight, 0xFFAAAAAA);
        }

        String promptDisplay = currentPrompt;
        int promptWidth = this.font.width(promptDisplay);
        guiGraphics.drawString(this.font, promptDisplay, leftPos + PADDING, this.commandInput.getY(), PROMPT_COLOR);
        
        this.commandInput.setX(leftPos + PADDING + promptWidth);
        this.commandInput.setWidth(GUI_WIDTH - 2 * PADDING - promptWidth - SCROLLBAR_WIDTH);
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= leftPos && mouseX <= leftPos + GUI_WIDTH &&
               mouseY >= topPos && mouseY <= topPos + GUI_HEIGHT;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isMouseOver(mouseX, mouseY)) {
            scrollOffset -= scrollY * 20;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDraggingScrollbar) {
            int outputHeight = GUI_HEIGHT - this.font.lineHeight - 4 * PADDING;
            int totalContentHeight = outputLines.size() * (this.font.lineHeight + 1);
            int maxScroll = Math.max(0, totalContentHeight - outputHeight);
            float ratio = (float)(mouseY - topPos - PADDING) / outputHeight;
            scrollOffset = ratio * maxScroll;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int scrollbarX = leftPos + GUI_WIDTH - PADDING - SCROLLBAR_WIDTH;
            int outputStartY = topPos + PADDING;
            int outputHeight = GUI_HEIGHT - this.font.lineHeight - 4 * PADDING;
            if (mouseX >= scrollbarX && mouseX <= scrollbarX + SCROLLBAR_WIDTH &&
                mouseY >= outputStartY && mouseY <= outputStartY + outputHeight) {
                isDraggingScrollbar = true;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            isDraggingScrollbar = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            String command = this.commandInput.getValue();
            if (!command.isBlank()) {
                outputLines.add(currentPrompt + command);
                commandHistory.add(command);
                historyIndex = commandHistory.size();
                
                PacketDistributor.sendToServer(new ExecuteCommandPayload(command));
                
                this.commandInput.setValue("");
                scrollOffset = Double.MAX_VALUE;
            }
            return true;
        }
        
        if (keyCode == GLFW.GLFW_KEY_UP) {
            if (!commandHistory.isEmpty() && historyIndex > 0) {
                historyIndex--;
                this.commandInput.setValue(commandHistory.get(historyIndex));
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            if (historyIndex < commandHistory.size() - 1) {
                historyIndex++;
                this.commandInput.setValue(commandHistory.get(historyIndex));
            } else {
                historyIndex = commandHistory.size();
                this.commandInput.setValue("");
            }
            return true;
        }
        
        if (keyCode == GLFW.GLFW_KEY_L && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            outputLines.clear();
            scrollOffset = 0;
            return true;
        }
        
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    public static void receiveCommandResult(String result) {
        if (instance != null) {
            if (result != null && !result.isEmpty()) {
                for (String line : result.split("\n")) {
                    instance.outputLines.add(line);
                }
            }
            // 向服务端请求当前路径以更新提示符（可后续实现）
            // 暂时通过解析结果中的 "Changed directory to" 来更新提示符
            if (result != null && result.contains("Changed directory to")) {
                String path = result.substring(result.indexOf(":") + 1).trim();
                instance.currentPrompt = path.isEmpty() ? "~ $ " : "~/" + path + " $ ";
            }
            instance.scrollOffset = Double.MAX_VALUE;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 不绘制默认背景
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}
