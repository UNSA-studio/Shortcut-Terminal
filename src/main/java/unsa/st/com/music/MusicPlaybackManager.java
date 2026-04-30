package unsa.st.com.music;

import javazoom.jl.player.Player;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import unsa.st.com.ShortcutTerminal;
import unsa.st.com.filesystem.UserFileSystem;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class MusicPlaybackManager {
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private static final Map<UUID, ActivePlayback> activePlaybacks = new ConcurrentHashMap<>();

    public static class ActivePlayback {
        public UUID ownerUUID;
        public String currentFile;
        public int loopRemaining;
        public volatile boolean stopped = false;
    }

    public static String startPlayback(UUID ownerUUID, String filePath, int loop) {
        Player owner = getPlayer(ownerUUID);
        if (owner == null) return "Player not found.";

        Path actualPath = resolvePath(ownerUUID, filePath);
        if (actualPath == null || !Files.exists(actualPath)) {
            return "File not found: " + filePath;
        }

        ActivePlayback playback = new ActivePlayback();
        playback.ownerUUID = ownerUUID;
        playback.currentFile = actualPath.toString();
        playback.loopRemaining = loop;

        activePlaybacks.put(ownerUUID, playback);
        playNextInThread(playback);

        return "Now playing: " + actualPath.getFileName() + (loop > 0 ? " (loop " + loop + ")" : "");
    }

    private static void playNextInThread(ActivePlayback playback) {
        scheduler.submit(() -> {
            try {
                File file = new File(playback.currentFile);
                try (FileInputStream fis = new FileInputStream(file);
                     BufferedInputStream bis = new BufferedInputStream(fis)) {
                    Player mp3Player = new Player(bis);
                    mp3Player.play();
                }
                handlePlaybackFinished(playback);
            } catch (Exception e) {
                ShortcutTerminal.LOGGER.error("Audio playback error: {}", e.getMessage());
                activePlaybacks.remove(playback.ownerUUID);
            }
        });
    }

    private static void handlePlaybackFinished(ActivePlayback playback) {
        if (!playback.stopped) {
            if (playback.loopRemaining > 0) {
                playback.loopRemaining--;
                playNextInThread(playback);
            } else {
                activePlaybacks.remove(playback.ownerUUID);
            }
        }
    }

    public static void stopPlayback(UUID ownerUUID) {
        ActivePlayback p = activePlaybacks.get(ownerUUID);
        if (p != null) p.stopped = true;
        activePlaybacks.remove(ownerUUID);
    }

    private static Path resolvePath(UUID uuid, String relativePath) {
        Path userRoot = UserFileSystem.getUserPath(uuid);
        if (relativePath.startsWith("/")) {
            return userRoot.resolve(relativePath.substring(1));
        }
        return userRoot.resolve(relativePath);
    }

    private static Player getPlayer(UUID uuid) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.hasSingleplayerServer()) {
            return mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);
        }
        return null;
    }
}
