package unsa.st.com.music;

import javazoom.jl.decoder.*;
import net.minecraft.client.Minecraft;
import org.lwjgl.openal.AL10;
import unsa.st.com.ShortcutTerminal;
import unsa.st.com.filesystem.UserFileSystem;

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
        scheduler.submit(() -> playDirect(playback));

        return "Now playing: " + actualPath.getFileName() + (loop > 0 ? " (loop " + loop + ")" : "");
    }

    private static void playDirect(ActivePlayback playback) {
        try {
            // 1. 解码 MP3 到 PCM
            ByteBuffer pcmBuf = decodeMp3(playback.currentFile);
            if (pcmBuf == null) {
                activePlaybacks.remove(playback.ownerUUID);
                return;
            }

            // 2. 创建 OpenAL 源和缓冲，直接交给 OpenAL 播放
            int source = AL10.alGenSources();
            int buffer = AL10.alGenBuffers();

            AL10.alBufferData(buffer, AL10.AL_FORMAT_STEREO16, pcmBuf, 44100);
            AL10.alSourcei(source, AL10.AL_BUFFER, buffer);

            // 设置音源位置（在玩家身上）和衰减距离
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                AL10.alSource3f(source, AL10.AL_POSITION,
                        (float) mc.player.getX(), (float) mc.player.getY(), (float) mc.player.getZ());
                AL10.alSourcef(source, AL10.AL_REFERENCE_DISTANCE, 1.0f);
                AL10.alSourcef(source, AL10.AL_MAX_DISTANCE, 15.0f);
                AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 1.0f);
            }

            // 3. 播放并等待结束
            AL10.alSourcePlay(source);
            int state;
            do {
                Thread.sleep(100);
                state = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
            } while (!playback.stopped && state == AL10.AL_PLAYING);

            // 4. 清理
            AL10.alSourceStop(source);
            AL10.alDeleteSources(source);
            AL10.alDeleteBuffers(buffer);

            // 5. 处理循环
            if (!playback.stopped && playback.loopRemaining > 0) {
                playback.loopRemaining--;
                playDirect(playback);
            } else {
                activePlaybacks.remove(playback.ownerUUID);
            }
        } catch (Exception e) {
            ShortcutTerminal.LOGGER.error("Direct playback error: {}", e.getMessage());
            activePlaybacks.remove(playback.ownerUUID);
        }
    }

    // 解码 MP3 文件到 PCM ByteBuffer
    private static ByteBuffer decodeMp3(String filePath) throws Exception {
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

        byte[] pcmBytes = pcmOut.toByteArray();
        ByteBuffer directBuf = ByteBuffer.allocateDirect(pcmBytes.length);
        directBuf.order(ByteOrder.nativeOrder());
        directBuf.put(pcmBytes);
        directBuf.flip();
        return directBuf;
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
}
