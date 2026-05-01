package unsa.st.com.music;

import javazoom.jl.decoder.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.openal.AL10;
import unsa.st.com.ShortcutTerminal;
import unsa.st.com.filesystem.UserFileSystem;

import javax.sound.sampled.AudioFormat;
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
    private static final ResourceLocation DYNAMIC_AUDIO = ResourceLocation.fromNamespaceAndPath("shortcutterminal", "dynamic_audio");

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

        stopPlayback(ownerUUID);

        ActivePlayback playback = new ActivePlayback();
        playback.ownerUUID = ownerUUID;
        playback.currentFile = actualPath.toString();
        playback.loopRemaining = loop;

        activePlaybacks.put(ownerUUID, playback);
        scheduler.submit(() -> streamOgg(playback));

        return "Now playing: " + actualPath.getFileName() + (loop > 0 ? " (loop " + loop + ")" : "");
    }

    private static void streamOgg(ActivePlayback playback) {
        try {
            // 1. 解码整个MP3到PCM
            byte[] pcm = decodeMp3(playback.currentFile);
            if (pcm == null) {
                ShortcutTerminal.LOGGER.error("Failed to decode MP3");
                activePlaybacks.remove(playback.ownerUUID);
                return;
            }

            // 2. 将PCM编码为OGG Vorbis
            byte[] oggData = encodePcmToOgg(pcm);
            if (oggData == null) {
                ShortcutTerminal.LOGGER.error("Failed to encode OGG");
                activePlaybacks.remove(playback.ownerUUID);
                return;
            }

            // 3. 通过SoundEngine播放这个OGG流
            Minecraft mc = Minecraft.getInstance();
            Player player = mc.player;
            SoundEngine engine = mc.getSoundManager();
            engine.play(new DynamicAudioStream(oggData));

            // 4. 等待播放结束（根据PCM长度估算）
            int durationMs = (pcm.length / (44100 * 2 * 2)) * 1000;
            long startTime = System.currentTimeMillis();
            while (!playback.stopped && System.currentTimeMillis() - startTime < durationMs) {
                Thread.sleep(100);
            }

            // 5. 处理循环
            if (!playback.stopped && playback.loopRemaining > 0) {
                playback.loopRemaining--;
                streamOgg(playback);
            } else {
                activePlaybacks.remove(playback.ownerUUID);
            }
        } catch (Exception e) {
            ShortcutTerminal.LOGGER.error("Audio streaming error: {}", e.getMessage());
            activePlaybacks.remove(playback.ownerUUID);
        }
    }

    private static byte[] decodeMp3(String filePath) throws Exception {
        Bitstream bitstream = new Bitstream(new FileInputStream(filePath));
        Decoder decoder = new Decoder();
        ByteArrayOutputStream pcmOut = new ByteArrayOutputStream();
        Header header;
        while ((header = bitstream.readFrame()) != null) {
            SampleBuffer sampleBuf = (SampleBuffer) decoder.decodeFrame(header, bitstream);
            short[] samples = sampleBuf.getBuffer();
            ByteBuffer buf = ByteBuffer.allocate(samples.length * 2);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            ShortBuffer shortBuf = buf.asShortBuffer();
            shortBuf.put(samples);
            pcmOut.write(buf.array());
            bitstream.closeFrame();
        }
        bitstream.close();
        return pcmOut.toByteArray();
    }

    private static byte[] encodePcmToOgg(byte[] pcm) throws Exception {
        java.io.ByteArrayOutputStream oggOut = new java.io.ByteArrayOutputStream();
        de.jarnbjo.vorbis.VorbisEncoder encoder = new de.jarnbjo.vorbis.VorbisEncoder();
        // 配置编码参数
        de.jarnbjo.vorbis.CommentHeader comment = new de.jarnbjo.vorbis.CommentHeader();
        de.jarnbjo.vorbis.IdentificationHeader info = new de.jarnbjo.vorbis.IdentificationHeader(44100, 2, 0);
        encoder.open(oggOut, info, comment);

        short[] samples = new short[pcm.length / 2];
        ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples);
        encoder.writeFrames(samples);
        encoder.close();
        return oggOut.toByteArray();
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

    // 简单的动态音频流包装类
    private static class DynamicAudioStream implements AudioStream {
        private final byte[] data;
        private int position = 0;

        DynamicAudioStream(byte[] data) {
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
