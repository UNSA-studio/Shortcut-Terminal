package unsa.st.com.music;

import javazoom.jl.decoder.*;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
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
    private static boolean wasInWorld = false;

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

        stopPlayback(ownerUUID);

        ActivePlayback playback = new ActivePlayback();
        playback.ownerUUID = ownerUUID;
        playback.currentFile = actualPath.toString();
        playback.loopRemaining = loop;

        activePlaybacks.put(ownerUUID, playback);
        scheduler.submit(() -> streamOpenAL(playback));

        return "Now playing: " + actualPath.getFileName() + (loop > 0 ? " (loop " + loop + ")" : "");
    }

    private static void streamOpenAL(ActivePlayback playback) {
        try {
            Bitstream bitstream = new Bitstream(new FileInputStream(playback.currentFile));
            Decoder decoder = new Decoder();
            playback.source = AL10.alGenSources();

            int[] buffers = new int[3];
            for (int i = 0; i < 3; i++) buffers[i] = AL10.alGenBuffers();
            playback.buffers = new ArrayList<>(Arrays.asList(buffers[0], buffers[1], buffers[2]));

            Header header = bitstream.readFrame();
            if (header == null) return;
            SampleBuffer sample = (SampleBuffer) decoder.decodeFrame(header, bitstream);
            int channels = sample.getChannelCount();
            int sampleRate = sample.getSampleFrequency();
            int format = (channels == 2) ? AL10.AL_FORMAT_STEREO16 : AL10.AL_FORMAT_MONO16;

            ByteBuffer pcm = decodeToBuffer(sample.getBuffer());
            AL10.alBufferData(buffers[0], format, pcm, sampleRate);
            AL10.alSourceQueueBuffers(playback.source, buffers[0]);
            bitstream.closeFrame();

            AL10.alSourcePlay(playback.source);

            int currentBuf = 1;
            while (!playback.stopped) {
                int processed = AL10.alGetSourcei(playback.source, AL10.AL_BUFFERS_PROCESSED);
                while (processed > 0) {
                    AL10.alSourceUnqueueBuffers(playback.source);
                    processed--;
                }

                header = bitstream.readFrame();
                if (header != null) {
                    sample = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                    pcm = decodeToBuffer(sample.getBuffer());
                    AL10.alBufferData(buffers[currentBuf], format, pcm, sampleRate);
                    AL10.alSourceQueueBuffers(playback.source, buffers[currentBuf]);
                    bitstream.closeFrame();
                    currentBuf = (currentBuf + 1) % 3;
                } else {
                    break;
                }

                if (AL10.alGetSourcei(playback.source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
                    AL10.alSourcePlay(playback.source);
                }
                Thread.sleep(10);
            }

            bitstream.close();
            while (AL10.alGetSourcei(playback.source, AL10.AL_BUFFERS_PROCESSED) < 3 && !playback.stopped) {
                Thread.sleep(50);
            }

            if (!playback.stopped) {
                if (playback.loopRemaining > 0) {
                    playback.loopRemaining--;
                    cleanup(playback);
                    streamOpenAL(playback);
                } else {
                    cleanup(playback);
                    activePlaybacks.remove(playback.ownerUUID);
                }
            } else {
                cleanup(playback);
                activePlaybacks.remove(playback.ownerUUID);
            }
        } catch (Exception e) {
            ShortcutTerminal.LOGGER.error("OpenAL streaming error: {}", e.getMessage());
            cleanup(playback);
            activePlaybacks.remove(playback.ownerUUID);
        }
    }

    private static ByteBuffer decodeToBuffer(short[] pcm) {
        ByteBuffer buf = ByteBuffer.allocateDirect(pcm.length * 2);
        buf.order(ByteOrder.nativeOrder());
        ShortBuffer shortBuf = buf.asShortBuffer();
        shortBuf.put(pcm);
        buf.rewind();
        return buf;
    }

    private static void cleanup(ActivePlayback playback) {
        if (playback.source != -1) {
            AL10.alSourceStop(playback.source);
            AL10.alDeleteSources(playback.source);
            for (int buf : playback.buffers) {
                AL10.alDeleteBuffers(buf);
            }
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

    // 用 ClientTickEvent 检测世界退出，自动停止所有播放
    @EventBusSubscriber(modid = "shortcutterminal", value = Dist.CLIENT)
    public static class ClientEvents {
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            boolean inWorld = mc.level != null;
            if (wasInWorld && !inWorld) {
                for (UUID uuid : new ArrayList<>(activePlaybacks.keySet())) {
                    stopPlayback(uuid);
                }
                activePlaybacks.clear();
            }
            wasInWorld = inWorld;
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
