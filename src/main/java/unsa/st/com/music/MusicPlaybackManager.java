package unsa.st.com.music;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
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
        public SoundInstance currentSound = null; // 关键：保存正在播放的声音实例
    }

    public static String startPlayback(UUID ownerUUID, String filePath, int loop) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return "Player not found.";

        Path actualPath = resolvePath(ownerUUID, filePath);
        if (actualPath == null || !Files.exists(actualPath)) {
            return "File not found: " + filePath;
        }

        stopPlayback(ownerUUID);

        ActivePlayback playback = new ActivePlayback();
        playback.ownerUUID = ownerUUID;
        playback.currentFile = actualPath.toString();
        playback.loopRemaining = loop;

        activePlaybacks.put(ownerUUID, playback);
        
        // 直接在客户端主线程执行，确保声音实例被正确创建和管理
        mc.execute(() -> playWithEngine(playback));

        return "Now playing: " + actualPath.getFileName() + (loop > 0 ? " (loop " + loop + ")" : "");
    }

    private static void playWithEngine(ActivePlayback playback) {
        try {
            Minecraft mc = Minecraft.getInstance();
            Path filePath = Path.of(playback.currentFile);
            String fileName = filePath.getFileName().toString();
            
            // 构造一个动态位置的简单声音实例
            playback.currentSound = new SimpleSoundInstance(
                ResourceLocation.fromNamespaceAndPath("shortcutterminal", "music/" + fileName),
                SoundSource.RECORDS,
                1.0F, 1.0F,
                mc.level.getRandom(),
                false, 0,
                SoundInstance.Attenuation.LINEAR,
                0.0, 0.0, 0.0, // 初始位置，会被立即更新
                true
            );

            // 播放！
            mc.getSoundManager().play(playback.currentSound);

            // 动态更新音源位置，让声音跟随玩家
            long start = System.currentTimeMillis();
            long duration = 30000; // 预估播放时长
            try { duration = Files.size(filePath) / 176; } catch (Exception ignored) {}
            
            while (!playback.stopped && mc.player != null && playback.currentSound != null) {
                // 关键：每100毫秒更新一次音源位置到玩家当前位置
                Vec3 pos = mc.player.position();
                playback.currentSound.setPosition(pos.x, pos.y, pos.z);
                
                if (System.currentTimeMillis() - start > duration) {
                    break; // 预估播放结束
                }
                Thread.sleep(100);
            }

        } catch (Exception e) {
            ShortcutTerminal.LOGGER.error("SoundEngine playback error: {}", e.getMessage());
        } finally {
            // 处理循环
            if (!playback.stopped && playback.loopRemaining > 0) {
                playback.loopRemaining--;
                playWithEngine(playback);
            } else {
                activePlaybacks.remove(playback.ownerUUID);
            }
        }
    }

    public static void stopPlayback(UUID ownerUUID) {
        ActivePlayback p = activePlaybacks.get(ownerUUID);
        if (p != null) {
            p.stopped = true;
            if (p.currentSound != null) {
                Minecraft.getInstance().getSoundManager().stop(p.currentSound);
            }
            activePlaybacks.remove(ownerUUID);
        }
    }

    private static Path resolvePath(UUID uuid, String relativePath) {
        Path userRoot = UserFileSystem.getUserPath(uuid);
        if (relativePath.startsWith("/")) {
            return userRoot.resolve(relativePath.substring(1));
        }
        return userRoot.resolve(relativePath);
    }
}
