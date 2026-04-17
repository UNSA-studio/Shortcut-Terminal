package unsa.st.com.util;

import net.minecraft.server.level.ServerPlayer;
import unsa.st.com.core.CoreCommandExecutor;

public class CommandExecutor {
    private final CoreCommandExecutor core = new CoreCommandExecutor(false);
    private String currentPath = "";

    public String execute(ServerPlayer player, String command, String[] args, String sessionPath) {
        core.setPlayer(player);
        core.setCurrentPath(sessionPath);
        return core.execute(command, args);
    }

    public static String executeFromGUI(ServerPlayer player, String input) {
        CommandExecutor exec = new CommandExecutor();
        String[] parts = input.trim().split("\\s+");
        String cmd = parts[0];
        String[] args = new String[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);
        return exec.execute(player, cmd, args, "");
    }

    public boolean wasCdSuccessful() { return core.wasCdSuccessful(); }
    public String getCurrentPath() { return core.getCurrentPath(); }
}
