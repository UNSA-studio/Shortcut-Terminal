package unsa.st.com.terminal;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import unsa.st.com.filesystem.UserFileSystem;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TerminalManager {
    private static final Map<UUID, Boolean> terminalMode = new HashMap<>();
    private static final Map<UUID, TerminalSession> sessions = new HashMap<>();

    public static void enterTerminalMode(ServerPlayer player) {
        UUID uuid = player.getUUID();
        terminalMode.put(uuid, true);
        sessions.put(uuid, new TerminalSession(player));
        UserFileSystem.createUserDirectory(uuid);
    }

    public static void exitTerminalMode(ServerPlayer player) {
        UUID uuid = player.getUUID();
        terminalMode.remove(uuid);
        sessions.remove(uuid);
    }

    public static boolean isInTerminalMode(ServerPlayer player) {
        return terminalMode.getOrDefault(player.getUUID(), false);
    }

    public static TerminalSession getSession(ServerPlayer player) {
        return sessions.get(player.getUUID());
    }

    public static boolean handleChatInput(String message, net.minecraft.world.entity.player.Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        if (!isInTerminalMode(serverPlayer)) return false;
        if (message.equalsIgnoreCase("exit")) {
            exitTerminalMode(serverPlayer);
            serverPlayer.sendSystemMessage(
                net.minecraft.network.chat.Component.literal("Exited terminal mode")
                    .withStyle(net.minecraft.ChatFormatting.GREEN)
            );
            return true;
        }
        TerminalSession session = getSession(serverPlayer);
        if (session != null) {
            session.executeCommand(message);
        }
        return true;
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID uuid = player.getUUID();
            terminalMode.remove(uuid);
            sessions.remove(uuid);
        }
    }
}
