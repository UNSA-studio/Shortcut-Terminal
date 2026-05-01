package unsa.st.com.music;

import javazoom.jl.decoder.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
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
        public int source = -1;
        public List<Integer> buffers = new ArrayList<>();
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
        playInThread(playback);

        return "Now playing: " + actualPath.getFileName() + (loop > 0 ? " (loop " + loop + ")" : "");
    }

    private static void playInThread(ActivePlayback playback) {
        scheduler.submit(() -> {
            try {
                playMp3OpenAL(playback, new File(playback.currentFile));
            } catch (Exception e) {
                ShortcutTerminal.LOGGER.error("Audio playback error: {}", e.getMessage());
                cleanup(playback);
                activePlaybacks.remove(playback.ownerUUID);
            }
        });
    }

    private static void playMp3OpenAL(ActivePlayback playback, File file) throws Exception {
        Bitstream bitstream = new Bitstream(new FileInputStream(file));
        Decoder decoder = new Decoder();
        boolean hasMoreFrames = true;

        // 创建 OpenAL 源
        int source = AL10.alGenSources();
        playback.source = source;

        // 循环解码并流式播放
        while (hasMoreFrames && !playback.stopped) {
            Header header = bitstream.readFrame();
            if (header == null) {
                hasMoreFrames = false;
                break;
            }
            SampleBuffer output = (SampleBuffer) decoder.decodeFrame(header, bitstream);
            short[] pcm = output.getBuffer();
            int channels = output.getChannelCount();
            int sampleRate = output.getSampleFrequency();

            // 将 PCM 数据转为 ByteBuffer
            ByteBuffer pcmBuffer = ByteBuffer.allocateDirect(pcm.length * 2);
            pcmBuffer.order(ByteOrder.nativeOrder());
            ShortBuffer shortBuffer = pcmBuffer.asShortBuffer();
            shortBuffer.put(pcm);
            pcmBuffer.rewind();

            // 创建 OpenAL 缓冲区
            int buffer = AL10.alGenBuffers();
            playback.buffers.add(buffer);
            int format = (channels == 2) ? AL10.AL_FORMAT_STEREO16 : AL10.AL_FORMAT_MONO16;
            AL10.alBufferData(buffer, format, pcmBuffer, sampleRate);

            // 将缓冲区加入队列
            AL10.alSourceQueueBuffers(source, buffer);

            // 如果源未播放，启动播放
            if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
                AL10.alSourcePlay(source);
            }

            bitstream.closeFrame();
        }
        bitstream.close();

        // 等待播放完毕或停止
        while (!playback.stopped && AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING) {
            Thread.sleep(100);
        }
        // 如果正常结束，处理循环
        if (!playback.stopped) {
            if (playback.loopRemaining > 0) {
                playback.loopRemaining--;
                // 重新开始播放
                cleanup(playback);
                playInThread(playback);
            } else {
                cleanup(playback);
                activePlaybacks.remove(playback.ownerUUID);
            }
        } else {
            cleanup(playback);
            activePlaybacks.remove(playback.ownerUUID);
        }
    }

    private static void cleanup(ActivePlayback playback) {
        if (playback.source != -1) {
            AL10.alSourceStop(playback.source);
            for (int buf : playback.buffers) {
                AL10.alDeleteBuffers(buf);
            }
            AL10.alDeleteSources(playback.source);
            playback.source = -1;
            playback.buffers.clear();
        }
    }

    public static void stopPlayback(UUID ownerUUID) {
        ActivePlayback p = activePlaybacks.get(ownerUUID);
        if (p != null) {
            p.stopped = true;
            cleanup(p);
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

    private static Player getPlayer(UUID uuid) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.hasSingleplayerServer()) {
            return mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);
        }
        return null;
    }
}
