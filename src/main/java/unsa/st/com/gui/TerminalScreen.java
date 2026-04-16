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
    private static final int GUI_WIDTH = 280;
    private static final int GUI_HEIGHT = 180;
    private static final int PADDING = 8;

    private int leftPos;
    private int topPos;
    private EditBox commandInput;
    private List<String> outputLines = new ArrayList<>();
    private static TerminalScreen instance;
    private List<String> commandHistory = new ArrayList<>();
    private int historyIndex = -1;

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
        guiGraphics.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + GUI_HEIGHT, BG_COLOR);
        guiGraphics.renderOutline(leftPos, topPos, GUI_WIDTH, GUI_HEIGHT, BORDER_COLOR);

        int outputStartY = this.topPos + PADDING;
        int outputHeight = GUI_HEIGHT - this.font.lineHeight - 4 * PADDING;
        int lineHeight = this.font.lineHeight + 1;
        int maxLines = outputHeight / lineHeight;
        
        int startIndex = Math.max(0, outputLines.size() - maxLines);
        for (int i = startIndex; i < outputLines.size(); i++) {
            int y = outputStartY + (i - startIndex) * lineHeight;
            guiGraphics.drawString(this.font, outputLines.get(i), leftPos + PADDING, y, TEXT_COLOR);
        }

        String prompt = "~/User $ ";
        int promptWidth = this.font.width(prompt);
        guiGraphics.drawString(this.font, prompt, leftPos + PADDING, this.commandInput.getY(), TEXT_COLOR);
        
        this.commandInput.setX(leftPos + PADDING + promptWidth);
        this.commandInput.setWidth(GUI_WIDTH - 2 * PADDING - promptWidth);
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            String command = this.commandInput.getValue();
            if (!command.isBlank()) {
                outputLines.add("~/User $ " + command);
                commandHistory.add(command);
                historyIndex = commandHistory.size();
                
                PacketDistributor.sendToServer(new ExecuteCommandPayload(command));
                
                this.commandInput.setValue("");
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
        
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    public static void receiveCommandResult(String result) {
        if (instance != null) {
            instance.outputLines.add(result);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 空实现，避免背景模糊
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}
