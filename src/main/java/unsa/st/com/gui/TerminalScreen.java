package unsa.st.com.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import unsa.st.com.ShortcutTerminal;
import unsa.st.com.client.*;
import unsa.st.com.client.TerminalSessionManager.SessionData;
import unsa.st.com.compute.*;
import unsa.st.com.network.*;
import java.util.*;
import java.util.concurrent.*;

/** Main-thread UI, per-session state and output routing, scrollable tabs and clipped/wrapped output. */
public class TerminalScreen extends Screen {
    private static final int PAD = 7, TAB_H = 22, SCROLL_W = 5;
    private static final ExecutorService COMMANDS = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "STOS-CommandIO"); t.setDaemon(true); return t;
    });
    private static TerminalScreen instance;
    private final ItemStack panel;
    private int left, top, panelWidth, panelHeight, current, firstTab, historyIndex;
    private int scroll, rowWidth = -1;
    private boolean dragging, dirtyRows = true, followTail = true;
    private EditBox input;
    private String playerName, notice = "";
    private List<SessionData> sessions;
    private final List<TabHit> tabs = new ArrayList<>();
    private final List<OutputRow> rows = new ArrayList<>();
    private final Deque<SessionData> pendingServerResults = new ArrayDeque<>();
    private SyncAction confirm;
    private enum SyncAction { TO_SERVER, TO_LOCAL }
    private record TabHit(int index, int x, int width) {}
    private record OutputRow(FormattedCharSequence text, int color) {}

    public TerminalScreen() { this(ItemStack.EMPTY); }
    public TerminalScreen(ItemStack panel) { super(Component.literal("STOS Terminal")); this.panel = panel.copy(); }
    public static TerminalScreen getInstance() { return instance; }
    public ItemStack hardwarePanel() { return panel; }
    private SessionData session() { return sessions.get(current); }
    private ClientCommandExecutor executor() { return session().createExecutor(); }
    @Override protected void init() {
        if (minecraft == null || minecraft.player == null) return;
        instance = this;
        if (sessions == null) {
            playerName = minecraft.player.getName().getString();
            sessions = TerminalSessionManager.getSessions(playerName);
        } else rememberInput();
        panelWidth = Math.max(120, Math.min(600, width - 16));
        panelHeight = Math.max(100, Math.min(340, height - 16));
        left = (width - panelWidth) / 2; top = (height - panelHeight) / 2;
        input = new EditBox(font, left + PAD, inputY(), panelWidth - 2 * PAD, font.lineHeight + 3, Component.literal("Command"));
        input.setMaxLength(4096); input.setBordered(false); input.setTextColor(0xFF88EEAA);
        input.setCanLoseFocus(false);
        input.setResponder(value -> { if (sessions != null) session().draft = value; });
        addRenderableWidget(input); setInitialFocus(input);
        loadSession(current);
    }
    private int inputY() { return top + panelHeight - 2 * font.lineHeight - 13; }
    private int outputTop() { return top + TAB_H + PAD; }
    private int outputHeight() { return Math.max(font.lineHeight, inputY() - PAD - outputTop()); }
    private int outputWidth() { return panelWidth - 3 * PAD - SCROLL_W; }
    private void rememberInput() { if (input != null && sessions != null && !sessions.isEmpty()) session().draft = input.getValue(); }
    private void saveCurrentSession() {
        if (sessions == null) return;
        rememberInput();
        for (SessionData data : sessions) if (data.executor != null) data.updateFromExecutor(data.executor, data.outputLines);
        TerminalSessionManager.saveCurrentSessions(playerName, sessions);
    }
    private void loadSession(int index) {
        if (index < 0 || index >= sessions.size()) return;
        // init/resize must not reset current or write the old executor into a different index.
        if (input != null && index != current) rememberInput();
        current = index; confirm = null; notice = "";
        SessionData data = session();
        data.createExecutor().setOutputConsumer(result -> Minecraft.getInstance().execute(() -> appendResult(data, result)));
        historyIndex = data.commandHistory.size();
        input.setValue(data.draft); input.setFocused(true);
        if (!data.booted && data.bootLines == null) {
            data.bootLines = ComputePolicy.bootSequence(executor().stosLevel(), ClientHardware.clientRamMb(), ClientHardware.clientSsdGb());
            data.nextBootMs = System.currentTimeMillis();
        }
        dirtyRows = true; followTail = true;
    }
    private void newSession() {
        rememberInput();
        String error = WindowResourcePolicy.creationError(sessions.size(), ClientHardware.cpuWindowLimit(),
                totalMemoryUnits(), ClientHardware.totalMemoryCapacityLines());
        if (error != null) { notice = error; appendLine(session(), error); return; }
        saveCurrentSession();
        TerminalSessionManager.createSession(playerName); // Manager owns the insertion. Never sessions.add again.
        loadSession(sessions.size() - 1); saveCurrentSession();
    }
    private void closeSession(int index) {
        if (sessions.size() <= 1 || index < 0 || index >= sessions.size()) return;
        saveCurrentSession();
        SessionData active = session(), removed = sessions.remove(index);
        TerminalSessionManager.reindex(sessions);
        current = active == removed ? Math.min(index, sessions.size() - 1) : sessions.indexOf(active);
        // Drop the old input reference until the new session has been selected.
        input.setValue(sessions.get(current).draft);
        loadSession(current); saveCurrentSession();
    }
    @Override public void tick() {
        if (input == null || sessions == null) return;
        SessionData data = session();
        long now = System.currentTimeMillis();
        if (!data.booted && data.bootLines != null && now >= data.nextBootMs) {
            appendLine(data, data.bootLines.get(data.bootIndex++));
            data.nextBootMs = now + ComputePolicy.bootLineDelayMs(executor().stosLevel());
            if (data.bootIndex >= data.bootLines.size()) {
                data.booted = true; data.bootLines = null; saveCurrentSession();
            }
        }
    }
    private static int sessionUnits(SessionData data) {
        long units = WindowResourcePolicy.WINDOW_OVERHEAD_UNITS;
        for (String line : data.outputLines) units += Math.max(1, (line.length() * 2L + 32 + 1023) / 1024);
        for (String line : data.commandHistory) units += Math.max(1, (line.length() * 2L + 32 + 1023) / 1024);
        if (!data.draft.isEmpty()) units += Math.max(1, (data.draft.length() * 2L + 1023) / 1024);
        return (int) Math.min(Integer.MAX_VALUE, units);
    }
    private int usedMemoryUnits() {
        long total = 0; for (SessionData data : sessions) total += sessionUnits(data);
        return (int) Math.min(Integer.MAX_VALUE, total);
    }
    public static int totalMemoryUnits() {
        return instance == null || instance.sessions == null ? 0 : instance.usedMemoryUnits();
    }
    public static int totalUsedLines() {
        if (instance == null || instance.sessions == null) return 0;
        int total = 0; for (SessionData data : instance.sessions) total += data.outputLines.size(); return total;
    }
    public static int windowCount() { return instance == null || instance.sessions == null ? 0 : instance.sessions.size(); }
    private void appendLine(SessionData target, String line) {
        if (!sessions.contains(target)) return;
        if (line == null) line = "";
        // A single addon/result cannot allocate unbounded UI text.
        if (line.length() > 16384) line = line.substring(0, 16384) + " [truncated]";
        target.outputLines.add(line);
        int cap = ClientHardware.totalMemoryCapacityLines();
        while (usedMemoryUnits() > cap && !target.outputLines.isEmpty()) target.outputLines.remove(0);
        while (usedMemoryUnits() > cap && target.commandHistory.size() > 1) target.commandHistory.remove(0);
        if (target.executor != null) target.executor.setCommandHistory(target.commandHistory);
        if (target == session()) { dirtyRows = true; followTail = true; }
    }
    private void appendResult(SessionData target, String result) {
        if (!sessions.contains(target) || result == null) return;
        if (instance != null && instance != this && instance.sessions == sessions) {
            instance.appendResult(target, result); return;
        }
        for (String line : result.split("\\R", -1)) appendLine(target, line);
        if (target.executor != null) target.currentPath = target.executor.getCurrentPath();
        saveCurrentSession();
    }
    public void addOutputLine(String text) { appendResult(session(), text); }
    private void rebuildRows() {
        if (!dirtyRows && rowWidth == outputWidth()) return;
        rows.clear(); rowWidth = outputWidth();
        for (String line : session().outputLines) {
            int color = line.startsWith("STOS:") || line.startsWith("Error:") || line.startsWith("bash:") ? 0xFFFF8888
                    : line.contains(" $ ") ? 0xFF88DDDD : 0xFF77DD99;
            var parts = font.split(Component.literal(line.isEmpty() ? " " : line), rowWidth);
            for (FormattedCharSequence part : parts) rows.add(new OutputRow(part, color));
        }
        dirtyRows = false;
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (input == null || sessions == null) return;
        g.fill(0, 0, width, height, 0x88000000);
        g.fill(left, top, left + panelWidth, top + panelHeight, 0xFF080D13);
        g.renderOutline(left, top, panelWidth, panelHeight, 0xFF748391);
        renderTabs(g);
        rebuildRows();
        int lineHeight = font.lineHeight + 1, max = Math.max(0, rows.size() * lineHeight - outputHeight());
        if (followTail) scroll = max;
        scroll = Math.max(0, Math.min(scroll, max));
        g.enableScissor(left + PAD, outputTop(), left + PAD + outputWidth(), outputTop() + outputHeight());
        int first = scroll / lineHeight;
        for (int i = first; i < Math.min(rows.size(), first + outputHeight() / lineHeight + 2); i++)
            g.drawString(font, rows.get(i).text(), left + PAD, outputTop() + i * lineHeight - scroll, rows.get(i).color(), false);
        g.disableScissor();
        if (max > 0) {
            int x = left + panelWidth - PAD - SCROLL_W, thumb = Math.max(10, outputHeight() * outputHeight() / (rows.size() * lineHeight));
            int y = outputTop() + (int)((long)scroll * (outputHeight() - thumb) / max);
            g.fill(x, outputTop(), x + SCROLL_W, outputTop() + outputHeight(), 0xFF273340);
            g.fill(x, y, x + SCROLL_W, y + thumb, 0xFF8BA7B6);
        }
        g.fill(left + PAD, inputY() - 4, left + panelWidth - PAD, inputY() - 3, 0xFF283544);
        String prompt = "~/" + playerName + executor().getCurrentPath() + " $ ";
        prompt = font.plainSubstrByWidth(prompt, panelWidth / 2);
        int promptW = font.width(prompt);
        input.setX(left + PAD + promptW); input.setY(inputY()); input.setWidth(Math.max(24, panelWidth - 2 * PAD - promptW));
        input.setVisible(confirm == null); input.setEditable(!session().busy && confirm == null);
        if (confirm == null) g.drawString(font, prompt, left + PAD, inputY(), 0xFF88DDDD, false);
        else g.drawString(font, font.plainSubstrByWidth("Sync " + (confirm == SyncAction.TO_SERVER ? "to server" : "from server") + "? [Y/N]", panelWidth - 2 * PAD), left + PAD, inputY(), 0xFFFFDD77, false);
        int pct = WindowResourcePolicy.usagePercent(totalMemoryUnits(), ClientHardware.totalMemoryCapacityLines());
        String status = notice.isEmpty() ? "CPU " + sessions.size() + "/" + ClientHardware.cpuWindowLimit() + "  RAM " + pct + "%"
                + (session().busy ? "  Working..." : "  Ctrl+T new | Ctrl+W close") : notice;
        g.drawString(font, font.plainSubstrByWidth(status, panelWidth - 2 * PAD), left + PAD, top + panelHeight - font.lineHeight - 3, notice.isEmpty() ? 0xFF99AABB : 0xFFFF8888, false);
        super.render(g, mouseX, mouseY, partialTick);
    }
    private void renderTabs(GuiGraphics g) {
        g.fill(left + 1, top + 1, left + panelWidth - 1, top + TAB_H, 0xFF1B2531);
        int visible = WindowResourcePolicy.visibleTabCount(panelWidth);
        firstTab = WindowResourcePolicy.firstVisibleTab(current, firstTab, visible, sessions.size());
        int tabWidth = Math.min(110, (panelWidth - 64) / visible);
        tabs.clear();
        for (int i = firstTab, x = left + 20; i < Math.min(sessions.size(), firstTab + visible); i++, x += tabWidth) {
            boolean active = i == current;
            g.fill(x, top + 2, x + tabWidth - 2, top + TAB_H - 1, active ? 0xFF344354 : 0xFF151D27);
            if (active) g.fill(x, top + 2, x + tabWidth - 2, top + 4, 0xFF70D9D1);
            String path = sessions.get(i).currentPath;
            String label = (i + 1) + ": " + (path == null || path.equals("/") ? "~" : path.substring(path.lastIndexOf('/') + 1));
            g.drawString(font, font.plainSubstrByWidth(label, tabWidth - 20), x + 4, top + 8, active ? 0xFFFFFFFF : 0xFF99AABB, false);
            g.drawString(font, "x", x + tabWidth - 13, top + 8, 0xFFCC9999, false);
            tabs.add(new TabHit(i, x, tabWidth));
        }
        g.drawString(font, "<", left + 6, top + 8, 0xFFBBDDDD, false);
        g.drawString(font, ">", left + panelWidth - 38, top + 8, 0xFFBBDDDD, false);
        g.drawString(font, "+", left + panelWidth - 18, top + 8, 0xFFBBDDDD, false);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (input == null) return false;
        if (button == 0 && x >= left && x < left + panelWidth && y >= top && y < top + TAB_H) {
            if (confirm != null) return true;
            if (x >= left + panelWidth - 25) newSession();
            else if (x >= left + panelWidth - 45) loadSession(Math.min(sessions.size() - 1, firstTab + WindowResourcePolicy.visibleTabCount(panelWidth)));
            else if (x < left + 20) loadSession(Math.max(0, firstTab - 1));
            else for (TabHit hit : tabs) if (x >= hit.x && x < hit.x + hit.width) {
                if (x >= hit.x + hit.width - 17) closeSession(hit.index); else loadSession(hit.index);
                break;
            }
            return true;
        }
        if (button == 0 && x >= left + panelWidth - PAD - SCROLL_W && x <= left + panelWidth - PAD && y >= outputTop() && y < outputTop() + outputHeight()) {
            dragging = true; setScrollFromMouse(y); return true;
        }
        return super.mouseClicked(x, y, button);
    }
    private void setScrollFromMouse(double y) {
        rebuildRows(); followTail = false;
        int max = Math.max(0, rows.size() * (font.lineHeight + 1) - outputHeight());
        scroll = (int)(Math.max(0, Math.min(1, (y - outputTop()) / outputHeight())) * max);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (dragging) { setScrollFromMouse(y); return true; }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0) dragging = false;
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (x >= left && x <= left + panelWidth && y >= outputTop() && y < outputTop() + outputHeight()) {
            followTail = false; scroll -= (int)(sy * 30); return true;
        }
        return super.mouseScrolled(x, y, sx, sy);
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (input == null) return super.keyPressed(key, scan, mods);
        boolean ctrl = (mods & GLFW.GLFW_MOD_CONTROL) != 0, shift = (mods & GLFW.GLFW_MOD_SHIFT) != 0;
        if (key == GLFW.GLFW_KEY_ESCAPE && confirm != null) { confirm = null; return true; }
        if (confirm != null) {
            if (key == GLFW.GLFW_KEY_Y) { performSync(); confirm = null; }
            else if (key == GLFW.GLFW_KEY_N) confirm = null;
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_T) { newSession(); return true; }
        if (ctrl && key == GLFW.GLFW_KEY_W) { closeSession(current); return true; }
        if (ctrl && (key == GLFW.GLFW_KEY_TAB || key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_PAGE_DOWN)) {
            int direction = key == GLFW.GLFW_KEY_PAGE_UP || shift ? -1 : 1;
            loadSession(Math.floorMod(current + direction, sessions.size())); return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_L) { clearSession(); return true; }
        // Delete/Ctrl+C/Ctrl+X are editing keys, NOT destructive close/switch shortcuts.
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (!session().busy && !input.getValue().isBlank()) processCommand(input.getValue()); return true;
        }
        List<String> history = executor().getCommandHistory();
        if (key == GLFW.GLFW_KEY_UP) {
            if (historyIndex > 0) input.setValue(history.get(--historyIndex)); return true;
        }
        if (key == GLFW.GLFW_KEY_DOWN) {
            if (historyIndex < history.size() - 1) input.setValue(history.get(++historyIndex));
            else { historyIndex = history.size(); input.setValue(""); } return true;
        }
        return super.keyPressed(key, scan, mods);
    }
    @Override public boolean charTyped(char c, int mods) { return confirm != null || super.charTyped(c, mods); }
    private void clearSession() {
        SessionData data = session(); data.outputLines.clear(); data.booted = true; data.bootLines = null;
        scroll = 0; dirtyRows = true; notice = ""; saveCurrentSession();
    }
    private void processCommand(String command) {
        SessionData target = session(); ClientCommandExecutor exec = executor();
        input.setValue(""); target.draft = "";
        String[] parts = command.trim().split("\\s+"); String cmd = parts[0].toLowerCase(Locale.ROOT);
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);
        exec.addCommandToHistory(command);
        while (exec.getCommandHistory().size() > 256) exec.getCommandHistory().remove(0);
        target.commandHistory = exec.getCommandHistory(); historyIndex = target.commandHistory.size();
        if (cmd.equals("clear")) { clearSession(); return; }
        appendLine(target, "~/" + playerName + exec.getCurrentPath() + " $ " + command);
        if (cmd.equals("run") && args.length >= 2 && args[0].equalsIgnoreCase("synchrony")) {
            confirm = args[1].equalsIgnoreCase("-server") ? SyncAction.TO_LOCAL
                    : args[1].equalsIgnoreCase("-local") ? SyncAction.TO_SERVER : null;
            if (confirm != null) { saveCurrentSession(); return; }
        }
        if (cmd.equals("refresh") || cmd.equals("user")) {
            pendingServerResults.add(target); PacketDistributor.sendToServer(new ExecuteCommandPayload(command));
        } else if (cmd.equals("pkg") || cmd.equals("winget")) {
            target.busy = true;
            COMMANDS.execute(() -> {
                String result;
                try { result = exec.execute(cmd, args); }
                catch (Exception e) { ShortcutTerminal.LOGGER.error("Terminal command failed", e); result = "Error: " + e.getMessage(); }
                final String output = result;
                Minecraft.getInstance().execute(() -> { target.busy = false; appendResult(target, output); });
            });
        } else {
            try { appendResult(target, exec.execute(cmd, args)); }
            catch (Exception e) { ShortcutTerminal.LOGGER.error("Terminal command failed", e); appendResult(target, "Error: " + e.getMessage()); }
            if (exec.hasPendingChanges()) { syncFileSystemToServer(); exec.clearPendingChanges(); }
        }
        target.currentPath = exec.getCurrentPath(); saveCurrentSession();
    }
    private void performSync() {
        if (confirm == SyncAction.TO_SERVER) { syncFileSystemToServer(); appendLine(session(), "[Sync] Sent local files to server."); }
        else if (confirm == SyncAction.TO_LOCAL) requestSyncFromServer();
        saveCurrentSession();
    }
    private void syncFileSystemToServer() {
        var id = executor().getPlayerUuid(); if (id == null) return;
        PacketDistributor.sendToServer(new SyncFileSystemPacket(id.toString(), ClientVirtualFileSystem.getFileSystemSnapshot(id.toString())));
    }
    public void requestSyncFromServer() {
        var id = executor().getPlayerUuid(); if (id == null) return;
        PacketDistributor.sendToServer(new RequestServerSyncPayload(id.toString()));
        appendLine(session(), "[Sync] Requesting server files...");
    }
    public static void receiveServerSyncData(String uuid, Map<String, String> files) {
        var mc = Minecraft.getInstance(); if (mc.player == null) return;
        String owner = mc.player.getUUID().toString(); int written = 0;
        for (var entry : files.entrySet()) {
            String path = ClientVirtualFileSystem.normalizePath("/", entry.getKey());
            int slash = path.lastIndexOf('/');
            if (ClientVirtualFileSystem.writeFile(owner, slash <= 0 ? "/" : path.substring(0, slash), path.substring(slash + 1), entry.getValue())) written++;
        }
        if (instance != null) instance.addOutputLine("[Sync] Applied " + written + "/" + files.size() + " files."
                + (written < files.size() ? " Storage full or invalid paths." : ""));
    }
    public static void receiveCommandResult(String result) {
        if (instance == null || instance.sessions == null) return;
        SessionData target = instance.pendingServerResults.pollFirst();
        instance.appendResult(target == null ? instance.session() : target, result);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics g, int x, int y, float p) {}
    @Override public void removed() { saveCurrentSession(); if (instance == this) instance = null; super.removed(); }
    @Override public void onClose() { saveCurrentSession(); super.onClose(); }
}
