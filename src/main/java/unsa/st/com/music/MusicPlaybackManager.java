package unsa.st.com.music;

import net.minecraft.client.Minecraft;
import unsa.st.com.ShortcutTerminal;
import unsa.st.com.filesystem.UserFileSystem;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class MusicPlaybackManager {
    private static final String[] SUPPORTED_FORMATS = {".ogg", ".wav"};
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

            if (name.endsWith(".ogg")) {
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

    private static void playOgg(File file, ActivePlayback playback) throws Exception {
        // 1. 底层解码 OGG 到 PCM
        byte[] pcm = decodeOgg(file);
        if (pcm == null) return;

        // 2. 播放 PCM 数据
        AudioFormat format = new AudioFormat(44100, 16, 2, true, false);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
        line.open(format);
        line.start();

        int offset = 0;
        while (offset < pcm.length && !playback.stopped) {
            int toWrite = Math.min(8192, pcm.length - offset);
            line.write(pcm, offset, toWrite);
            offset += toWrite;
        }

        line.drain();
        line.close();
    }

    private static byte[] decodeOgg(File file) throws Exception {
        // 使用底层 JOrbis API，完全避开 VorbisFile
        FileInputStream fis = new FileInputStream(file);
        ByteArrayOutputStream pcmOut = new ByteArrayOutputStream();

        com.jcraft.jogg.SyncState sync = new com.jcraft.jogg.SyncState();
        com.jcraft.jogg.StreamState stream = new com.jcraft.jogg.StreamState();
        com.jcraft.jogg.Page page = new com.jcraft.jogg.Page();
        com.jcraft.jogg.Packet packet = new com.jcraft.jogg.Packet();
        com.jcraft.jorbis.Info info = new com.jcraft.jorbis.Info();
        com.jcraft.jorbis.Comment comment = new com.jcraft.jorbis.Comment();
        com.jcraft.jorbis.DspState dsp = new com.jcraft.jorbis.DspState();
        com.jcraft.jorbis.Block block = new com.jcraft.jorbis.Block(dsp);

        byte[] buffer = new byte[4096];
        int bytes;
        boolean headerParsed = false;

        sync.init();
        while ((bytes = fis.read(buffer)) != -1) {
            sync.write(buffer, 0, bytes);
        }

        // 解析 OGG 页
        loop:
        while (true) {
            switch (sync.pageOut(page)) {
                case 0: break loop;
                case -1: break loop;
                default:
                    if (!headerParsed) {
                        // 第一个流
                        stream.init(page.serialno());
                        stream.reset();
                        info.init();
                        comment.init();
                        if (stream.pageIn(page) != 0) {
                            stream.packetOut(packet);
                            info.synthesisHeaderin(comment, packet);
                            dsp.synthesisInit(info);
                            block.init(dsp);
                            headerParsed = true;
                        }
                    } else {
                        if (stream.pageIn(page) != 0) {
                            while (stream.packetOut(packet) > 0) {
                                if (packet.e_o_s == 0) {
                                    block.synthesis(packet);
                                    dsp.synthesisBlockin(block);
                                    float[][][] pcmInfo = new float[1][][];
                                    int[] index = new int[info.channels];
                                    int samples;
                                    while ((samples = dsp.synthesisPcmout(pcmInfo, index)) > 0) {
                                        for (int s = 0; s < samples; s++) {
                                            for (int c = 0; c < info.channels; c++) {
                                                short val = (short) (pcmInfo[0][c][s] * 32767);
                                                pcmOut.write((byte) (val & 0xFF));
                                                pcmOut.write((byte) ((val >> 8) & 0xFF));
                                            }
                                        }
                                        dsp.synthesisRead(samples);
                                    }
                                }
                            }
                        }
                    }
            }
        }

        fis.close();
        return pcmOut.toByteArray();
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
