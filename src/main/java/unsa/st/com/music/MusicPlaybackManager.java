package unsa.st.com.music;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import unsa.st.com.ShortcutTerminal;
import unsa.st.com.pkg.PkgManager;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class MusicPlaybackManager {
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private static final Map<UUID, ActivePlayback> activePlaybacks = new ConcurrentHashMap<>();

    public static class ActivePlayback {
        public UUID ownerUUID;
        public String currentPath;
        public int loopRemaining;
        public volatile boolean stopped = false;
    }

    public static String startPlayback(UUID ownerUUID, String filePath, int loop) {
        Path actualPath = resolvePath(ownerUUID, filePath);
        if (actualPath == null || !Files.exists(actualPath)) {
            return "File not found: " + filePath;
        }

        ActivePlayback playback = new ActivePlayback();
        playback.ownerUUID = ownerUUID;
        playback.currentPath = actualPath.toString();
        playback.loopRemaining = loop;

        activePlaybacks.put(ownerUUID, playback);
        playNext(playback);

        return "Now playing: " + actualPath.getFileName() + (loop > 0 ? " (loop " + loop + ")" : "");
    }

    private static void playNext(ActivePlayback playback) {
        if (playback.stopped || !activePlaybacks.containsKey(playback.ownerUUID)) return;
        
        Minecraft.getInstance().execute(() -> {
            Minecraft mc = Minecraft.getInstance();
            Path path = Path.of(playback.currentPath);
            SimpleSoundInstance sound = new SimpleSoundInstance(
                path.toUri().toString(), // 使用file://协议
                SoundSource.RECORDS,
                1.0F, 1.0F,
                mc.level.getRandom(),
                false, 0,
                net.minecraft.client.resources.sounds.SoundInstance.Attenuation.LINEAR,
                0.0, 0.0, 0.0,
                true
            );
            mc.getSoundManager().play(sound);
        });

        // 简单的循环逻辑：等待播放结束后重新播放
        if (playback.loopRemaining > 0) {
            playback.loopRemaining--;
            scheduler.schedule(() -> playNext(playback), 10, TimeUnit.SECONDS);
        } else if (playback.loopRemaining == 0) {
            // 单次播放，不循环
        } else {
            // loopRemaining < 0：无限循环
            scheduler.schedule(() -> playNext(playback), 10, TimeUnit.SECONDS);
        }
    }

    public static void stopPlayback(UUID ownerUUID) {
        ActivePlayback p = activePlaybacks.remove(ownerUUID);
        if (p != null) p.stopped = true;
    }

    private static Path resolvePath(UUID uuid, String relativePath) {
        // 优先从用户目录查找，然后从共享 Program 目录查找
        Path userPath = unsa.st.com.filesystem.UserFileSystem.getUserPath(uuid);
        Path userFile = relativePath.startsWith("/") ? userPath.resolve(relativePath.substring(1)) : userPath.resolve(relativePath);
        if (Files.exists(userFile)) return userFile;
        
        // 从公共 Program 目录查找
        Path programPath = PkgManager.getPathFile(false).getParent().resolve("Program");
        Path programFile = relativePath.startsWith("/") ? programPath.resolve(relativePath.substring(1)) : programPath.resolve(relativePath);
        if (Files.exists(programFile)) return programFile;
        
        return null;
    }
}
