package unsa.st.com.music;

import javazoom.jl.decoder.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.openal.AL10;
import unsa.st.com.ShortcutTerminal;
import unsa.st.com.filesystem.UserFileSystem;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
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
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return "Player not found.";

        Path actualPath = resolvePath(ownerUUID, filePath);
        if (actualPath == null || !Files.exists(actualPath)) {
            return "File not found: " + filePath;
        }

        // 停止旧的播放
        stopPlayback(ownerUUID);

        ActivePlayback playback = new ActivePlayback();
        playback.ownerUUID = ownerUUID;
        playback.currentFile = actualPath.toString();
        playback.loopRemaining = loop;

        activePlaybacks.put(ownerUUID, playback);
        scheduler.submit(() -> playWithSoundEngine(playback));

        return "Now playing: " + actualPath.getFileName() + (loop > 0 ? " (loop " + loop + ")" : "");
    }

    private static void playWithSoundEngine(ActivePlayback playback) {
        try {
            // 1. 用 jlayer 解码整个 MP3 到内存
            byte[] pcmData = decodeMp3ToPcm(playback.currentFile);
            if (pcmData == null) {
                ShortcutTerminal.LOGGER.error("Failed to decode MP3: {}", playback.currentFile);
                activePlaybacks.remove(playback.ownerUUID);
                return;
            }

            // 2. 将 PCM 数据包装成 Minecraft 的 AudioStream
            AudioStream audioStream = new PcmAudioStream(pcmData);
            
            // 3. 获取播放者位置作为音源
            Minecraft mc = Minecraft.getInstance();
            Player player = mc.player;
            double x = player.getX();
            double y = player.getY();
            double z = player.getZ();

            // 4. 通过 SoundEngine 播放，范围约 15 格（同唱片机）
            SimpleSoundInstance sound = new SimpleSoundInstance(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("shortcutterminal", "custom_mp3"),
                SoundSource.RECORDS,
                1.0F, 1.0F,
                mc.level.getRandom(),
                false, 0,
                net.minecraft.client.resources.sounds.SoundInstance.Attenuation.LINEAR,
                x, y, z,
                true
            );

            mc.getSoundManager().play(sound);
            
            // 等待播放结束（根据 PCM 数据长度估算时间）
            int durationMs = (pcmData.length / (44100 * 2 * 2)) * 1000;
            long startTime = System.currentTimeMillis();
            while (!playback.stopped && System.currentTimeMillis() - startTime < durationMs) {
                Thread.sleep(100);
            }

            // 处理循环
            if (!playback.stopped && playback.loopRemaining > 0) {
                playback.loopRemaining--;
                playWithSoundEngine(playback);
            } else {
                activePlaybacks.remove(playback.ownerUUID);
            }
        } catch (Exception e) {
            ShortcutTerminal.LOGGER.error("SoundEngine playback error: {}", e.getMessage());
            activePlaybacks.remove(playback.ownerUUID);
        }
    }

    private static byte[] decodeMp3ToPcm(String filePath) {
        try {
            Bitstream bitstream = new Bitstream(new FileInputStream(filePath));
            Decoder decoder = new Decoder();
            ByteArrayOutputStream pcmOut = new ByteArrayOutputStream();
            boolean hasMoreFrames = true;

            while (hasMoreFrames) {
                Header header = bitstream.readFrame();
                if (header == null) {
                    hasMoreFrames = false;
                    break;
                }
                SampleBuffer output = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                short[] samples = output.getBuffer();
                ByteBuffer buf = ByteBuffer.allocate(samples.length * 2);
                buf.order(ByteOrder.LITTLE_ENDIAN);
                ShortBuffer shortBuf = buf.asShortBuffer();
                shortBuf.put(samples);
                pcmOut.write(buf.array());
                bitstream.closeFrame();
            }
            bitstream.close();
            return pcmOut.toByteArray();
        } catch (Exception e) {
            ShortcutTerminal.LOGGER.error("MP3 decoding error: {}", e.getMessage());
            return null;
        }
    }

    public static void stopPlayback(UUID ownerUUID) {
        ActivePlayback p = activePlaybacks.get(ownerUUID);
        if (p != null) {
            p.stopped = true;
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

    // 简单的 PCM AudioStream 包装类
    private static class PcmAudioStream implements AudioStream {
        private final byte[] data;
        private int position = 0;

        PcmAudioStream(byte[] data) {
            this.data = data;
        }

        @Override
        public AudioFormat getFormat() {
            return new AudioFormat(44100, 16, 2, true, false);
        }

        @Override
        public ByteBuffer read(int size) {
            int remaining = data.length - position;
            int toRead = Math.min(size, remaining);
            ByteBuffer buf = ByteBuffer.allocateDirect(toRead);
            buf.put(data, position, toRead);
            buf.flip();
            position += toRead;
            return buf;
        }

        @Override
        public void close() {}
    }
}
