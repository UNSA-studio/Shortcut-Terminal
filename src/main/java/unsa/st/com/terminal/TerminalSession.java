package unsa.st.com.terminal;

import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import unsa.st.com.util.CommandExecutor;

public class TerminalSession {
    private final ServerPlayer player;
    private String currentPath;
    private final CommandExecutor executor;

    public TerminalSession(ServerPlayer player) {
        this.player = player;
        this.currentPath = "";
        this.executor = new CommandExecutor();
    }

    public void executeCommand(String input) {
        if (input.isBlank()) return;
        String[] parts = input.trim().split("\\s+");
        String command = parts[0];
        String[] args = new String[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);
        String result = executor.execute(player, command, args, currentPath);
        if (result != null && !result.isEmpty()) {
            if (command.equalsIgnoreCase("cd") && executor.wasCdSuccessful()) {
                this.currentPath = executor.getCurrentPath();
            }
            ChatFormatting color = result.startsWith("Error:") || result.startsWith("You do not")
                ? ChatFormatting.RED : ChatFormatting.WHITE;
            player.sendSystemMessage(Component.literal(result).withStyle(color));
        }
    }

    public String getCurrentPath() { return currentPath; }
    public void setCurrentPath(String path) { this.currentPath = path; }
}
