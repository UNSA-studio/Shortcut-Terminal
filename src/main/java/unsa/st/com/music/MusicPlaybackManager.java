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

            // 处理循环
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
        com.jcraft.jorbis.VorbisFile vf = new com.jcraft.jorbis.VorbisFile(file.getAbsolutePath());
        com.jcraft.jorbis.Info[] infoArray = vf.getInfo();
        int channels = infoArray[0].channels;
        int rate = infoArray[0].rate;

        AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, rate, 16, channels, channels * 2, rate, false);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
        line.open(format);
        line.start();

        byte[] buf = new byte[4096 * 4];
        int len;
        while ((len = vf.read(buf, 0, buf.length)) > 0 && !playback.stopped) {
            line.write(buf, 0, len);
        }
        line.drain();
        line.close();
        vf.close();
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
