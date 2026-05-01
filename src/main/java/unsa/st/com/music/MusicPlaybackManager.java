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
        com.jcraft.jogg.SyncState sync = new com.jcraft.jogg.SyncState();
        com.jcraft.jogg.StreamState stream = new com.jcraft.jogg.StreamState();
        com.jcraft.jogg.Page page = new com.jcraft.jogg.Page();
        com.jcraft.jogg.Packet packet = new com.jcraft.jogg.Packet();
        com.jcraft.jorbis.Info info = new com.jcraft.jorbis.Info();
        com.jcraft.jorbis.Comment comment = new com.jcraft.jorbis.Comment();
        com.jcraft.jorbis.DspState dsp = new com.jcraft.jorbis.DspState();
        com.jcraft.jorbis.Block block = new com.jcraft.jorbis.Block(dsp);

        FileInputStream fis = new FileInputStream(file);
        byte[] buffer = new byte[4096];
        int bytes = fis.read(buffer, 0, 4096);
        sync.write(buffer, 0, bytes);

        while (sync.pageOut(page) == 0) {
            bytes = fis.read(buffer, 0, 4096);
            if (bytes == -1) break;
            sync.write(buffer, 0, bytes);
        }

        stream.init(page.serialno());
        stream.reset();
        info.init();
        comment.init();
        stream.pageIn(page);
        stream.packetOut(packet);
        info.synthesisHeaderin(comment, packet);

        dsp.synthesisInit(info);
        block.init(dsp);

        AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, info.rate, 16, info.channels, info.channels * 2, info.rate, false);
        DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, format);
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(lineInfo);
        line.open(format);
        line.start();

        float[][][] pcmInfo = new float[1][][];
        int[] index = new int[info.channels];

        while (!playback.stopped) {
            while (sync.pageOut(page) > 0) {
                stream.pageIn(page);
                while (stream.packetOut(packet) > 0) {
                    if (packet.e_o_s == 0) {
                        block.synthesis(packet);
                        dsp.synthesisBlockin(block);
                        int samples;
                        while ((samples = dsp.synthesisPcmout(pcmInfo, index)) > 0) {
                            for (int s = 0; s < samples; s++) {
                                for (int c = 0; c < info.channels; c++) {
                                    int val = (int) (pcmInfo[0][c][s] * 32767);
                                    line.write(new byte[]{(byte)(val & 0xFF), (byte)((val >> 8) & 0xFF)}, 0, 2);
                                }
                            }
                            dsp.synthesisRead(samples);
                        }
                    }
                }
            }
            bytes = fis.read(buffer, 0, 4096);
            if (bytes == -1) break;
            sync.write(buffer, 0, bytes);
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
