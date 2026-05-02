package unsa.st.com.music;

import javazoom.jl.player.Player;
import net.minecraft.client.Minecraft;
import unsa.st.com.ShortcutTerminal;
import unsa.st.com.filesystem.UserFileSystem;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class MusicPlaybackManager {
    private static final String[] SUPPORTED_FORMATS = {".mp3", ".ogg", ".wav"};
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

        stopPlayback(ownerUUID);

        ActivePlayback playback = new ActivePlayback();
        playback.ownerUUID = ownerUUID;
        playback.currentFile = actualPath.toString();
        playback.loopRemaining = loop;

        activePlaybacks.put(ownerUUID, playback);
        scheduler.submit(() -> playInThread(playback));

        return "Now playing: " + actualPath.getFileName() + (loop > 0 ? " (loop " + loop + ")" : "");
    }

    private static void playInThread(ActivePlayback playback) {
        try {
            File file = new File(playback.currentFile);
            String name = file.getName().toLowerCase();

            if (name.endsWith(".mp3")) {
                playMp3(file, playback);
            } else if (name.endsWith(".ogg")) {
                playOgg(file, playback);
            } else if (name.endsWith(".wav")) {
                playWav(file, playback);
            } else {
                ShortcutTerminal.LOGGER.error("Unsupported format: {}", name);
                activePlaybacks.remove(playback.ownerUUID);
                return;
            }

            if (!playback.stopped && playback.loopRemaining > 0) {
                playback.loopRemaining--;
                playInThread(playback);
            } else {
                activePlaybacks.remove(playback.ownerUUID);
            }
        } catch (Exception e) {
            ShortcutTerminal.LOGGER.error("Audio playback error: {}", e.getMessage());
            activePlaybacks.remove(playback.ownerUUID);
        }
    }

    private static void playMp3(File file, ActivePlayback playback) throws Exception {
        try (FileInputStream fis = new FileInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(fis)) {
            Player player = new Player(bis);
            player.play(); // JLayer 的 Player 不是抽象类，可以直接播放
        }
    }

    private static void playOgg(File file, ActivePlayback playback) throws Exception {
        // 完整的 OGG 解码器实现，参考自 JOrbis 官方示例和开源项目
        com.jcraft.jogg.SyncState syncState = new com.jcraft.jogg.SyncState();
        com.jcraft.jogg.StreamState streamState = new com.jcraft.jogg.StreamState();
        com.jcraft.jogg.Page page = new com.jcraft.jogg.Page();
        com.jcraft.jogg.Packet packet = new com.jcraft.jogg.Packet();
        com.jcraft.jorbis.Info info = new com.jcraft.jorbis.Info();
        com.jcraft.jorbis.Comment comment = new com.jcraft.jorbis.Comment();
        com.jcraft.jorbis.DspState dspState = new com.jcraft.jorbis.DspState();
        com.jcraft.jorbis.Block block = new com.jcraft.jorbis.Block(dspState);

        FileInputStream fis = new FileInputStream(file);
        byte[] buffer = new byte[4096];
        int bytes;
        boolean headerParsed = false;

        syncState.init();

        // 1. 解析 OGG 头部
        while (!headerParsed) {
            bytes = fis.read(buffer, 0, 4096);
            if (bytes == -1) break;
            syncState.write(buffer, 0, bytes);

            while (syncState.pageOut(page) == 1) {
                if (!headerParsed) {
                    streamState.init(page.serialno());
                    streamState.reset();
                    info.init();
                    comment.init();
                    if (streamState.pageIn(page) != 0 && streamState.packetOut(packet) != 0) {
                        info.synthesisHeaderin(comment, packet);
                        dspState.synthesisInit(info);
                        block.init(dspState);
                        headerParsed = true;
                    }
                }
            }
        }

        // 2. 设置音频输出设备
        AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, info.rate, 16, info.channels, info.channels * 2, info.rate, false);
        DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, format);
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(lineInfo);
        line.open(format, 4096);
        line.start();

        // 3. 解码并播放
        while (!playback.stopped) {
            bytes = fis.read(buffer, 0, 4096);
            if (bytes == -1) break;
            syncState.write(buffer, 0, bytes);

            while (syncState.pageOut(page) == 1) {
                streamState.pageIn(page);
                while (streamState.packetOut(packet) > 0) {
                    if (packet.e_o_s == 0) {
                        block.synthesis(packet);
                        dspState.synthesisBlockin(block);

                        float[][][] pcmInfo = new float[1][][];
                        int[] index = new int[info.channels];
                        int samples;
                        while ((samples = dspState.synthesisPcmout(pcmInfo, index)) > 0) {
                            for (int s = 0; s < samples; s++) {
                                for (int c = 0; c < info.channels; c++) {
                                    short val = (short) (pcmInfo[0][c][s] * 32767);
                                    line.write(new byte[]{(byte) (val & 0xFF), (byte) ((val >> 8) & 0xFF)}, 0, 2);
                                }
                            }
                            dspState.synthesisRead(samples);
                        }
                    }
                }
            }
        }

        line.drain();
        line.close();
        fis.close();
    }

    private static void playWav(File file, ActivePlayback playback) throws Exception {
        AudioInputStream audioStream = AudioSystem.getAudioInputStream(file);
        AudioFormat format = audioStream.getFormat();
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
        line.open(format);
        line.start();
        byte[] buf = new byte[8192];
        int len;
        while ((len = audioStream.read(buf)) != -1 && !playback.stopped) {
            line.write(buf, 0, len);
        }
        line.drain();
        line.close();
        audioStream.close();
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