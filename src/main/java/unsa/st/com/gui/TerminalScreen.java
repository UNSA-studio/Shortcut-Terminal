package unsa.st.com.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import unsa.st.com.client.ClientCommandExecutor;
import unsa.st.com.client.ClientVirtualFileSystem;
import unsa.st.com.network.ExecuteCommandPacket;
import unsa.st.com.network.SyncFileSystemPacket;

import java.util.ArrayList;
import java.util.List;

public class TerminalScreen extends Screen {
    private static final int BORDER_COLOR = 0xFFFFFFFF;
    private static final int BG_COLOR = 0xFF000000;
    private static final int TEXT_COLOR = 0xFF55FF55;
    private static final int GUI_WIDTH = 320;
    private static final int GUI_HEIGHT = 200;
    private static final int PADDING = 6;
    private static final int SCROLLBAR_WIDTH = 6;

    private int leftPos, topPos;
    private EditBox commandInput;
    private List<String> outputLines = new ArrayList<>();
    private double scrollOffset = 0;
    private boolean isDraggingScrollbar = false;
    private List<String> commandHistory = new ArrayList<>();
    private int historyIndex = -1;
    private String currentPrompt = "~ $ ";
    private static TerminalScreen instance;
    
    private final ClientCommandExecutor executor = new ClientCommandExecutor();

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
                this.font, this.leftPos + PADDING, this.topPos + GUI_HEIGHT - this.font.lineHeight - PADDING,
                GUI_WIDTH - 2 * PADDING, this.font.lineHeight + 2, Component.literal(""));
        this.commandInput.setMaxLength(256);
        this.commandInput.setBordered(false);
        this.commandInput.setTextColor(TEXT_COLOR);
        this.commandInput.setCanLoseFocus(false);
        this.addRenderableWidget(this.commandInput);
        this.setInitialFocus(this.commandInput);
        this.commandInput.setFocused(true);
        
        updatePrompt();
    }

    private void updatePrompt() {
        String path = executor.getCurrentPath();
        currentPrompt = path.equals("/") ? "~ $ " : "~" + path + " $ ";
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + GUI_HEIGHT, BG_COLOR);
        guiGraphics.renderOutline(leftPos, topPos, GUI_WIDTH, GUI_HEIGHT, BORDER_COLOR);

        int outputStartX = leftPos + PADDING;
        int outputStartY = topPos + PADDING;
        int outputHeight = GUI_HEIGHT - this.font.lineHeight - 4 * PADDING;
        int lineHeight = this.font.lineHeight + 1;
        int maxVisibleLines = outputHeight / lineHeight;
        
        int totalContentHeight = outputLines.size() * lineHeight;
        int maxScroll = Math.max(0, totalContentHeight - outputHeight);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        
        int startLine = (int) (scrollOffset / lineHeight);
        int endLine = Math.min(outputLines.size(), startLine + maxVisibleLines + 1);
        
        for (int i = startLine; i < endLine; i++) {
            int y = outputStartY + (i - startLine) * lineHeight - (int)(scrollOffset % lineHeight);
            String line = outputLines.get(i);
            int color = TEXT_COLOR;
            if (line.startsWith("~") || line.contains(" $ ")) {
                color = 0xFF55FFFF;
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
        guiGraphics.drawString(this.font, promptDisplay, leftPos + PADDING, this.commandInput.getY(), 0xFF55FF55);
        
        this.commandInput.setX(leftPos + PADDING + promptWidth);
        this.commandInput.setWidth(GUI_WIDTH - 2 * PADDING - promptWidth - SCROLLBAR_WIDTH);
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= leftPos && mouseX <= leftPos + GUI_WIDTH && mouseY >= topPos && mouseY <= topPos + GUI_HEIGHT;
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int scrollbarX = leftPos + GUI_WIDTH - PADDING - SCROLLBAR_WIDTH;
            int outputStartY = topPos + PADDING;
            int outputHeight = GUI_HEIGHT - this.font.lineHeight - 4 * PADDING;
            if (mouseX >= scrollbarX && mouseX <= scrollbarX + SCROLLBAR_WIDTH && mouseY >= outputStartY && mouseY <= outputStartY + outputHeight) {
                isDraggingScrollbar = true;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) isDraggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            String command = this.commandInput.getValue();
            if (!command.isBlank()) {
                outputLines.add(currentPrompt + command);
                commandHistory.add(command);
                historyIndex = commandHistory.size();
                
                // 本地执行
                String[] parts = command.trim().split("\\s+");
                String cmd = parts[0];
                String[] args = new String[parts.length - 1];
                System.arraycopy(parts, 1, args, 0, args.length);
                
                String result = executor.execute(cmd, args);
                if (result != null && !result.isEmpty()) {
                    for (String line : result.split("\n")) {
                        outputLines.add(line);
                    }
                }
                
                // 如果是需要服务端权限的命令，发送给服务端
                if (cmd.equalsIgnoreCase("refresh") || cmd.equalsIgnoreCase("user")) {
                    PacketDistributor.sendToServer(new ExecuteCommandPacket(command));
                } else {
                    // 其他命令：异步同步整个文件系统到服务端
                    syncFileSystemToServer();
                }
                
                this.commandInput.setValue("");
                updatePrompt();
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
        return this.commandInput.keyPressed(keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return this.commandInput.charTyped(codePoint, modifiers) || super.charTyped(codePoint, modifiers);
    }

    private void syncFileSystemToServer() {
        var snapshot = ClientVirtualFileSystem.getFileSystemSnapshot(executor.getPlayerUuid());
        PacketDistributor.sendToServer(new SyncFileSystemPacket(executor.getPlayerUuid(), snapshot));
    }

    public static void receiveCommandResult(String result) {
        if (instance != null && result != null && !result.isEmpty()) {
            for (String line : result.split("\n")) {
                instance.outputLines.add(line);
            }
            instance.scrollOffset = Double.MAX_VALUE;
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {}
    @Override
    public boolean shouldCloseOnEsc() { return true; }
}
