package unsa.st.com.client;

import net.minecraft.client.Minecraft;
import unsa.st.com.core.CoreCommandExecutor;

import java.util.*;

public class ClientCommandExecutor {
    private final CoreCommandExecutor core;
    private List<String> commandHistory = new ArrayList<>();
    private boolean pendingChanges = false;

    public ClientCommandExecutor() {
        this.core = new CoreCommandExecutor(true);
        String playerName = Minecraft.getInstance().player.getName().getString();
        String uuid = Minecraft.getInstance().player.getUUID().toString();
        core.setPlayer(playerName, uuid);
    }

    public ClientCommandExecutor(String playerName) {
        this.core = new CoreCommandExecutor(true);
        String uuid = Minecraft.getInstance().player.getUUID().toString();
        core.setPlayer(playerName, uuid);
    }

    public String execute(String command, String[] args) {
        String cmd = command.toLowerCase(Locale.ROOT);
        if (cmd.equals("mkdir") || cmd.equals("touch") || cmd.equals("rm") || cmd.equals("echo")) {
            pendingChanges = true;
        }
        return core.execute(command, args);
    }

    public String getCurrentPath() { return core.getCurrentPath(); }
    public void setCurrentPath(String path) { core.setCurrentPath(path); }
    public String getPlayerName() { return Minecraft.getInstance().player.getName().getString(); }
    public String getPlayerUuid() { return Minecraft.getInstance().player.getUUID().toString(); }
    public List<String> getCommandHistory() { return commandHistory; }
    public void setCommandHistory(List<String> history) { this.commandHistory = new ArrayList<>(history); }
    public void addCommandToHistory(String command) { this.commandHistory.add(command); }
    public boolean hasPendingChanges() { return pendingChanges; }
    public void clearPendingChanges() { pendingChanges = false; }
}
