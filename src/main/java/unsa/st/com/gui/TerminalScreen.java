package unsa.st.com.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import unsa.st.com.client.ClientCommandExecutor;
import unsa.st.com.client.ClientVirtualFileSystem;
import unsa.st.com.client.TerminalSessionManager;
import unsa.st.com.client.TerminalSessionManager.SessionData;
import unsa.st.com.network.ExecuteCommandPayload;
import unsa.st.com.network.RequestServerSyncPayload;
import unsa.st.com.network.SyncFileSystemPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class TerminalScreen extends Screen {
    private static final int BORDER_COLOR = 0xFFFFFFFF;
    private static final int BG_COLOR = 0xFF000000;
    private static final int TEXT_COLOR = 0xFF55FF55;
    private static final int GUI_WIDTH = 320;
    private static final int GUI_HEIGHT = 200;
    private static final int PADDING = 6;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final double SCROLL_SPEED = 40.0;

    private int leftPos, topPos;
    private EditBox commandInput;
    private List<String> outputLines = new ArrayList<>();
    /** 启动动画调度器（守护线程，逐行渐显）。 */
    private static final ScheduledExecutorService BOOT_SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "STOS-Boot");
        t.setDaemon(true);
        return t;
    });
    private final List<ScheduledFuture<?>> bootTasks = new ArrayList<>();
    private double scrollOffset = 0;
    private boolean isDraggingScrollbar = false;
    private List<String> commandHistory = new ArrayList<>();
    private int historyIndex = -1;
    private String currentPrompt = "~ $ ";
    private static TerminalScreen instance;

    /** 顶部标签栏每帧重建的点击区：{startX, width}。 */
    private final List<int[]> tabBounds = new ArrayList<>();
    /** "+"（新建窗口）按钮的起始 X；-1 表示尚未绘制。 */
    private int addTabX = -1;
    
    private ClientCommandExecutor executor;
    private String playerName;
    private List<SessionData> sessions;
    private int currentSessionIndex = 0;
    
    private boolean ctrlXPressed = false;
    private boolean deletePressed = false;
    
    private boolean forcibleState = false;
    private String forciblePrompt = "";
    private SyncAction pendingSyncAction = null;
    
    private enum SyncAction { TO_SERVER, TO_LOCAL }

    public TerminalScreen() {
        super(Component.literal("Terminal"));
        instance = this;
    }

    public static TerminalScreen getInstance() {
        return instance;
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - GUI_WIDTH) / 2;
        this.topPos = (this.height - GUI_HEIGHT) / 2;

        this.playerName = Minecraft.getInstance().player.getName().getString();
        this.sessions = TerminalSessionManager.getSessions(playerName);
        this.currentSessionIndex = 0;
        loadSession(0);

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
    
    private void loadSession(int index) {
        if (index < 0 || index >= sessions.size()) return;
        cancelBootTasks(); // 切换/重载会话时停止未播完的启动动画
        if (executor != null) {
            sessions.get(currentSessionIndex).updateFromExecutor(executor, outputLines);
            TerminalSessionManager.saveCurrentSessions(playerName, sessions);
        }
        currentSessionIndex = index;
        SessionData data = sessions.get(index);
        this.executor = data.createExecutor();
        this.outputLines = new ArrayList<>(data.outputLines);
        // 新会话首次播放 STOS 启动动画（逐行渐显，速度取决于处理器等级）
        if (this.outputLines.isEmpty()) {
            playBootSequence(executor.stosLevel());
        }
        this.commandHistory = new ArrayList<>(data.commandHistory);
        this.historyIndex = commandHistory.size();
        updatePrompt();
    }
    
    private void saveCurrentSession() {
        if (executor != null) {
            sessions.get(currentSessionIndex).updateFromExecutor(executor, outputLines);
            TerminalSessionManager.saveCurrentSessions(playerName, sessions);
        }
    }
    
    private void newSession() {
        // CPU 硬性上限：处理器线程数决定最多能开多少窗口（与每窗口用量无关）
        int cpuLimit = unsa.st.com.client.ClientHardware.cpuWindowLimit();
        if (sessions.size() >= cpuLimit) {
            appendLine("STOS:There are no available CPU resources to calculate a new page");
            saveCurrentSession();
            scrollOffset = Double.MAX_VALUE;
            return;
        }
        // RAM 容量：任一窗口占用已超过 80% 容量，就不允许再开新窗口
        if (unsa.st.com.client.ClientHardware.ramUnderPressure()) {
            appendLine("STOS:Insufficient RAM capacity to create a new window");
            saveCurrentSession();
            scrollOffset = Double.MAX_VALUE;
            return;
        }
        SessionData newData = TerminalSessionManager.createSession(playerName);
        sessions.add(newData);
        TerminalSessionManager.saveCurrentSessions(playerName, sessions);
        loadSession(sessions.size() - 1);
    }

    /** 关闭指定窗口；关闭当前窗口时自动切到相邻窗口。 */
    private void closeSession(int index) {
        if (sessions.size() <= 1 || index < 0 || index >= sessions.size()) return;
        if (executor != null) {
            sessions.get(currentSessionIndex).updateFromExecutor(executor, outputLines);
            executor = null; // 阻止 loadSession 再把状态写回已删除的窗口
        }
        sessions.remove(index);
        for (int i = 0; i < sessions.size(); i++) sessions.get(i).index = i;
        if (currentSessionIndex >= sessions.size()) currentSessionIndex = sessions.size() - 1;
        TerminalSessionManager.saveCurrentSessions(playerName, sessions);
        loadSession(currentSessionIndex);
    }

    private void deleteCurrentSession() {
        closeSession(currentSessionIndex);
    }

    // ==================== 启动动画 ====================

    /** 播放 STOS 启动序列：逐行渐显，行间隔按处理器等级（L9 秒开 / 低等级慢吞吞）。 */
    private void playBootSequence(int level) {
        int delay = unsa.st.com.compute.ComputePolicy.bootLineDelayMs(level);
        int ramMb = unsa.st.com.client.ClientHardware.clientRamMb();
        int ssdGb = unsa.st.com.client.ClientHardware.clientSsdGb();
        long at = 0;
        for (String line : unsa.st.com.compute.ComputePolicy.bootSequence(level, ramMb, ssdGb)) {
            at += delay;
            final String text = line;
            bootTasks.add(BOOT_SCHEDULER.schedule(() -> Minecraft.getInstance().execute(() -> {
                if (instance == this) {
                    appendLine(text);
                    scrollOffset = Double.MAX_VALUE;
                }
            }), at, TimeUnit.MILLISECONDS));
        }
    }

    /** 取消未播完的启动动画任务。 */
    private void cancelBootTasks() {
        for (ScheduledFuture<?> f : bootTasks) {
            try { f.cancel(false); } catch (Exception ignored) {}
        }
        bootTasks.clear();
    }

    private void updatePrompt() {
        String path = executor.getCurrentPath();
        String displayPath = path.equals("/") ? "" : path;
        currentPrompt = "~/" + playerName + displayPath + " $ ";
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + GUI_HEIGHT, BG_COLOR);
        guiGraphics.renderOutline(leftPos, topPos, GUI_WIDTH, GUI_HEIGHT, BORDER_COLOR);
        renderTabBar(guiGraphics);

        int outputStartX = leftPos + PADDING;
        int outputStartY = terminalOutputTop();
        int outputHeight = terminalOutputHeight();
        int lineHeight = this.font.lineHeight + 1;
        int maxVisibleLines = outputHeight / lineHeight;
        
        int totalContentHeight = outputLines.size() * lineHeight;
        int maxScroll = Math.max(0, totalContentHeight - outputHeight);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        
        int startLine = (int) (scrollOffset / lineHeight);
        int endLine = Math.min(outputLines.size(), startLine + maxVisibleLines + 1);
        
        for (int i = startLine; i < endLine; i++) {
            int y = outputStartY + (i - startLine) * lineHeight - (int)(scrollOffset % lineHeight);
            if (y < outputStartY) y = outputStartY;
            if (y + lineHeight > outputStartY + outputHeight) continue;
            String line = outputLines.get(i);
            int color = TEXT_COLOR;
            if (line.startsWith("~/") && line.contains(" $ ")) {
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

        if (forcibleState) {
            guiGraphics.drawString(this.font, forciblePrompt, leftPos + PADDING, this.commandInput.getY(), 0xFFFFFF55);
            this.commandInput.setVisible(false);
        } else {
            String promptDisplay = currentPrompt;
            int promptWidth = this.font.width(promptDisplay);
            guiGraphics.drawString(this.font, promptDisplay, leftPos + PADDING, this.commandInput.getY(), 0xFF55FF55);
            this.commandInput.setX(leftPos + PADDING + promptWidth);
            this.commandInput.setWidth(GUI_WIDTH - 2 * PADDING - promptWidth - SCROLLBAR_WIDTH);
            this.commandInput.setVisible(true);
        }
        
        String sessionInfo = "[" + (currentSessionIndex + 1) + "/" + sessions.size() + " win]";
        int infoWidth = this.font.width(sessionInfo);
        guiGraphics.drawString(this.font, sessionInfo, 
                leftPos + GUI_WIDTH - PADDING - infoWidth - SCROLLBAR_WIDTH, 
                topPos + GUI_HEIGHT - this.font.lineHeight - PADDING, 0xFFAAAAAA);
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    // ==================== 顶部标签栏（Windows Terminal 风格） ====================

    /** 标签栏高度。 */
    private int tabBarHeight() { return this.font.lineHeight + 6; }

    /** 输出区域顶部 Y。 */
    private int terminalOutputTop() { return topPos + tabBarHeight() + PADDING; }

    /** 输出区域高度。 */
    private int terminalOutputHeight() {
        return GUI_HEIGHT - tabBarHeight() - this.font.lineHeight - 4 * PADDING;
    }

    /** 绘制顶部标签栏：可点击切换 / 关闭窗口，"+" 新建窗口。 */
    private void renderTabBar(GuiGraphics g) {
        int tabH = tabBarHeight();
        g.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + tabH, 0xFF141414);
        g.fill(leftPos, topPos + tabH - 1, leftPos + GUI_WIDTH, topPos + tabH, 0xFF444444);

        int addW = 14;
        int avail = GUI_WIDTH - addW - 6;
        int n = Math.max(1, sessions.size());
        int tabW = Math.min(88, Math.max(24, avail / n));
        if (tabW * n > avail) tabW = Math.max(18, avail / n);

        tabBounds.clear();
        int x = leftPos + 2;
        for (int i = 0; i < sessions.size(); i++) {
            boolean active = i == currentSessionIndex;
            g.fill(x, topPos + 2, x + tabW, topPos + tabH - 3, active ? 0xFF2C2C2C : 0xFF0A0A0A);
            if (active) g.fill(x, topPos + 2, x + tabW, topPos + 3, 0xFF55FFFF);
            String label = trimToWidth(tabLabel(i), tabW - 16);
            g.drawString(this.font, label, x + 4, topPos + (tabH - this.font.lineHeight) / 2,
                    active ? 0xFFFFFFFF : 0xFF808080);
            g.drawString(this.font, "x", x + tabW - 9, topPos + (tabH - this.font.lineHeight) / 2,
                    active ? 0xFFFF8888 : 0xFF777777);
            tabBounds.add(new int[]{x, tabW});
            x += tabW + 1;
        }
        this.addTabX = x;
        g.fill(x, topPos + 2, x + addW, topPos + tabH - 3, 0xFF1C1C1C);
        g.drawString(this.font, "+", x + 5, topPos + (tabH - this.font.lineHeight) / 2, 0xFFCCCCCC);
    }

    /** 标签标题：序号 + 当前目录名。 */
    private String tabLabel(int i) {
        String path = (i == currentSessionIndex && executor != null)
                ? executor.getCurrentPath() : sessions.get(i).currentPath;
        if (path == null || path.isEmpty()) path = "/";
        String name = path.equals("/") ? "/" : path.substring(path.lastIndexOf('/') + 1);
        if (name.isEmpty()) name = "/";
        return (i + 1) + ": " + name;
    }

    /** 按像素宽度裁剪字符串（超出加省略号）。 */
    private String trimToWidth(String s, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (this.font.width(s) <= maxWidth) return s;
        String ell = "..";
        int ew = this.font.width(ell);
        StringBuilder sb = new StringBuilder();
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            String c = String.valueOf(s.charAt(i));
            int cw = this.font.width(c);
            if (w + cw + ew > maxWidth) break;
            sb.append(c);
            w += cw;
        }
        return sb + ell;
    }

    /** 当前打开的窗口数量（硬件统计用）。 */
    public static int windowCount() {
        return instance == null || instance.sessions == null ? 1 : instance.sessions.size();
    }

    /** 所有窗口中最高的一份输出行数（RAM 压力判定用）。 */
    public static int maxUsedLines() {
        if (instance == null || instance.sessions == null) return 0;
        int idx = instance.currentSessionIndex;
        int max = instance.outputLines != null ? instance.outputLines.size() : 0;
        for (int i = 0; i < instance.sessions.size(); i++) {
            if (i == idx) continue;
            java.util.List<String> lines = instance.sessions.get(i).outputLines;
            if (lines != null) max = Math.max(max, lines.size());
        }
        return max;
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= leftPos && mouseX <= leftPos + GUI_WIDTH && mouseY >= topPos && mouseY <= topPos + GUI_HEIGHT;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isMouseOver(mouseX, mouseY)) {
            scrollOffset -= scrollY * SCROLL_SPEED;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseY >= topPos && mouseY <= topPos + tabBarHeight()) {
            if (addTabX >= 0 && mouseX >= addTabX && mouseX <= addTabX + 14) {
                newSession();
                return true;
            }
            for (int i = 0; i < tabBounds.size(); i++) {
                int[] b = tabBounds.get(i);
                if (mouseX >= b[0] && mouseX <= b[0] + b[1]) {
                    if (mouseX >= b[0] + b[1] - 11) closeSession(i);
                    else loadSession(i);
                    return true;
                }
            }
            return true;
        }
        if (button == 0) {
            int scrollbarX = leftPos + GUI_WIDTH - PADDING - SCROLLBAR_WIDTH;
            int outputStartY = terminalOutputTop();
            int outputHeight = terminalOutputHeight();
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
            int outputHeight = terminalOutputHeight();
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
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        
        if (forcibleState) {
            if (keyCode == GLFW.GLFW_KEY_Y) {
                performSync();
                forcibleState = false;
                pendingSyncAction = null;
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_N) {
                forcibleState = false;
                pendingSyncAction = null;
                appendLine("Sync cancelled.");
                return true;
            }
            return true;
        }
        
        if (ctrl) {
            if (keyCode == GLFW.GLFW_KEY_T) {
                newSession();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_W) {
                closeSession(currentSessionIndex);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_X) {
                if (!ctrlXPressed) { ctrlXPressed = true; newSession(); }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_Z) {
                if (currentSessionIndex > 0) loadSession(currentSessionIndex - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_C) {
                if (currentSessionIndex < sessions.size() - 1) loadSession(currentSessionIndex + 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_L) {
                outputLines.clear(); saveCurrentSession(); scrollOffset = 0;
                return true;
            }
        } else {
            if (keyCode == GLFW.GLFW_KEY_LEFT_CONTROL || keyCode == GLFW.GLFW_KEY_RIGHT_CONTROL) {
                ctrlXPressed = false; deletePressed = false;
            }
        }
        
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            if (!deletePressed) { deletePressed = true; deleteCurrentSession(); }
            return true;
        }
        
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            String command = this.commandInput.getValue();
            if (!command.isBlank()) {
                processCommand(command);
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
        return this.commandInput.keyPressed(keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_LEFT_CONTROL || keyCode == GLFW.GLFW_KEY_RIGHT_CONTROL) {
            ctrlXPressed = false; deletePressed = false;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE) deletePressed = false;
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (forcibleState) return true;
        return this.commandInput.charTyped(codePoint, modifiers) || super.charTyped(codePoint, modifiers);
    }

    private void processCommand(String command) {
        appendLine(currentPrompt + command);
        executor.addCommandToHistory(command);
        commandHistory = executor.getCommandHistory();
        historyIndex = commandHistory.size();
        
        String[] parts = command.trim().split("\\s+");
        String cmd = parts[0];
        String[] args = new String[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);
        
        if (cmd.equals("run") && args.length >= 1 && args[0].equals("synchrony")) {
            if (args.length >= 2) {
                if (args[1].equals("-server")) { 
                    pendingSyncAction = SyncAction.TO_LOCAL; 
                    forcibleState = true; 
                    forciblePrompt = "Are you sure you want to sync from server? [Y/N]"; 
                    this.commandInput.setValue(""); 
                    return; 
                } else if (args[1].equals("-local")) { 
                    pendingSyncAction = SyncAction.TO_SERVER; 
                    forcibleState = true; 
                    forciblePrompt = "Are you sure you want to sync to server? [Y/N]"; 
                    this.commandInput.setValue(""); 
                    return; 
                }
            }
        }
        
        String result = executor.execute(cmd, args);
        if (result != null && !result.isEmpty()) {
            for (String line : result.split("\n")) appendLine(line);
        }
        
        if (cmd.equalsIgnoreCase("refresh") || cmd.equalsIgnoreCase("user")) {
            PacketDistributor.sendToServer(new ExecuteCommandPayload(command));
        } else {
            if (executor.hasPendingChanges()) {
                syncFileSystemToServer();
                executor.clearPendingChanges();
            }
        }
        
        saveCurrentSession();
        this.commandInput.setValue("");
        updatePrompt();
        scrollOffset = Double.MAX_VALUE;
    }
    
    private void performSync() {
    if (pendingSyncAction == SyncAction.TO_SERVER) {
        // 显示开始消息
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.literal("§e[Sync] File sync started, please do not shut down the game."), false);
        }
        syncFileSystemToServer();
        appendLine("Local data synced to server.");
        // 显示完成消息
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.literal("§a[Sync] File synchronization completed."), false);
        }
    } else if (pendingSyncAction == SyncAction.TO_LOCAL) {
        requestSyncFromServer();
    }
    saveCurrentSession();
    updatePrompt();
    scrollOffset = Double.MAX_VALUE;
}

    private void syncFileSystemToServer() {
        var snapshot = ClientVirtualFileSystem.getFileSystemSnapshot(executor.getPlayerUuid().toString());
        PacketDistributor.sendToServer(new SyncFileSystemPacket(executor.getPlayerUuid().toString(), snapshot));
    }

    public void requestSyncFromServer() {
        PacketDistributor.sendToServer(new RequestServerSyncPayload(executor.getPlayerUuid().toString()));
        appendLine("Requesting file list from server...");
    }

    public static void receiveServerSyncData(String uuid, Map<String, String> files) {
        if (instance == null) return;
        boolean storageFull = false;
        for (Map.Entry<String, String> entry : files.entrySet()) {
            String fullPath = entry.getKey();
            String content = entry.getValue();
            int lastSlash = fullPath.lastIndexOf('/');
            String dirPath = lastSlash > 0 ? fullPath.substring(0, lastSlash + 1) : "/";
            String fileName = fullPath.substring(lastSlash + 1);
            if (!ClientVirtualFileSystem.writeFile(instance.playerName, dirPath, fileName, content)) {
                storageFull = true;
            }
        }
        instance.addOutputLine("§a[Sync] Received and applied " + files.size() + " files from server.");
        if (storageFull) instance.appendLine("§c[Sync] Storage full - some files were not written (install a bigger SSD).");
    }

    public static void receiveCommandResult(String result) {
        if (instance != null && result != null && !result.isEmpty()) {
            for (String line : result.split("\n")) instance.appendLine(line);
            instance.saveCurrentSession();
            instance.scrollOffset = Double.MAX_VALUE;
        }
    }

    /** 追加输出行，并按面板 RAM 限制历史缓冲行数（超出时丢弃最老的输出）。 */
    private void appendLine(String line) {
        outputLines.add(line);
        int limit = unsa.st.com.client.ClientHardware.scrollbackLimit();
        if (limit > 0 && outputLines.size() > limit) {
            outputLines.subList(0, outputLines.size() - limit).clear();
        }
    }

    public void addOutputLine(String line) {
        appendLine(line);
        saveCurrentSession();
        scrollOffset = Double.MAX_VALUE;
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {}
    @Override
    public void removed() {
        super.removed();
        cancelBootTasks();
        if (instance == this) instance = null;
    }
    @Override public boolean shouldCloseOnEsc() { saveCurrentSession(); return true; }
    @Override
    public void onClose() {
        saveCurrentSession();
        super.onClose();
    }
}