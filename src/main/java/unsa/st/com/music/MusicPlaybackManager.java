package unsa.st.com.music;

import javazoom.jl.decoder.*;
import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.JavaSoundAudioDevice;
import javazoom.jl.player.advanced.AdvancedPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import unsa.st.com.ShortcutTerminal;
import unsa.st.com.filesystem.UserFileSystem;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class MusicPlaybackManager {
    private static final String[] SUPPORTED_FORMATS = {".mp3", ".wav"};
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private static final Map<UUID, ActivePlayback> activePlaybacks = new ConcurrentHashMap<>();

    public static class ActivePlayback {
        public UUID ownerUUID;
        public Vec3 position;
        public String currentFile;
        public int loopRemaining;
        public boolean isSonglist;
        public List<String> songlist;
        public int songlistIndex;
        public volatile boolean stopped = false;
    }

    public static String startPlayback(UUID ownerUUID, String filePath, int loop, boolean isSonglist) {
        Player owner = getPlayer(ownerUUID);
        if (owner == null) return "Player not found.";

        Path actualPath = resolvePath(ownerUUID, filePath);
        if (actualPath == null || !Files.exists(actualPath)) {
            return "File not found: " + filePath;
        }

        ActivePlayback playback = new ActivePlayback();
        playback.ownerUUID = ownerUUID;
        playback.position = owner.position();
        playback.currentFile = actualPath.toString();
        playback.loopRemaining = loop;
        playback.isSonglist = isSonglist;

        if (isSonglist) {
            playback.songlist = loadSonglist(actualPath);
            if (playback.songlist.isEmpty()) return "Songlist is empty or invalid.";
            playback.songlistIndex = 0;
            playback.currentFile = playback.songlist.get(0);
        }

        activePlaybacks.put(ownerUUID, playback);
        playNextInThread(playback);

        return "Now playing: " + actualPath.getFileName() + (loop > 0 ? " (loop " + loop + ")" : "");
    }

    private static List<String> loadSonglist(Path file) {
        List<String> list = new ArrayList<>();
        try {
            String content = Files.readString(file);
            MusicSonglist songlist = new com.google.gson.Gson().fromJson(content, MusicSonglist.class);
            if (songlist != null && songlist.songs != null) {
                Path baseDir = file.getParent();
                for (String songName : songlist.songs) {
                    list.add(baseDir.resolve(songName).toString());
                }
            }
        } catch (IOException e) {}
        return list;
    }

    public static String generateSonglist(UUID ownerUUID, String dirPath) {
        Path dir = resolvePath(ownerUUID, dirPath);
        if (dir == null || !Files.isDirectory(dir)) return "Directory not found: " + dirPath;

        List<String> songs = new ArrayList<>();
        try {
            Files.walk(dir, 1)
                .filter(Files::isRegularFile)
                .forEach(file -> {
                    String name = file.getFileName().toString().toLowerCase();
                    for (String fmt : SUPPORTED_FORMATS) {
                        if (name.endsWith(fmt)) {
                            songs.add(file.getFileName().toString());
                            break;
                        }
                    }
                });
        } catch (IOException ignored) {}

        MusicSonglist songlist = new MusicSonglist();
        songlist.songs = songs;
        Path slFile = dir.resolve("Music.sl");
        try {
            Files.writeString(slFile, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(songlist));
            return "Songlist created with " + songs.size() + " songs: " + slFile.toString();
        } catch (IOException e) {
            return "Failed to write songlist.";
        }
    }

    private static void playNextInThread(ActivePlayback playback) {
        scheduler.submit(() -> {
            try {
                File file = new File(playback.currentFile);
                String fileName = file.getName().toLowerCase();

                if (fileName.endsWith(".wav")) {
                    playWav(file, playback);
                } else if (fileName.endsWith(".mp3")) {
                    playMp3(file, playback);
                } else {
                    ShortcutTerminal.LOGGER.error("Unsupported audio format: {}", fileName);
                    activePlaybacks.remove(playback.ownerUUID);
                }
            } catch (Exception e) {
                ShortcutTerminal.LOGGER.error("Audio playback error: {}", e.getMessage());
                activePlaybacks.remove(playback.ownerUUID);
            }
        });
    }

    private static void playWav(File file, ActivePlayback playback) throws Exception {
        AudioInputStream audioStream = AudioSystem.getAudioInputStream(file);
        AudioFormat format = audioStream.getFormat();
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
        line.open(format);
        line.start();

        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = audioStream.read(buffer)) != -1 && !playback.stopped) {
            line.write(buffer, 0, bytesRead);
        }

        line.drain();
        line.close();
        audioStream.close();

        handlePlaybackFinished(playback);
    }

    private static void playMp3(File file, ActivePlayback playback) throws Exception {
        FileInputStream fis = new FileInputStream(file);
        BufferedInputStream bis = new BufferedInputStream(fis);
        AdvancedPlayer player = new AdvancedPlayer(bis);
        
        // 开启播放线程，每帧检查是否被停止
        new Thread(() -> {
            try {
                player.play();
            } catch (Exception ignored) {}
            handlePlaybackFinished(playback);
        }).start();

        // 轮询等待停止信号
        while (!playback.stopped && activePlaybacks.containsKey(playback.ownerUUID)) {
            Thread.sleep(100);
        }
        player.close();
        bis.close();
        fis.close();
    }

    private static void handlePlaybackFinished(ActivePlayback playback) {
        if (!playback.stopped) {
            if (playback.loopRemaining > 0) {
                playback.loopRemaining--;
                playNextInThread(playback);
            } else if (playback.isSonglist && playback.songlistIndex < playback.songlist.size() - 1) {
                playback.songlistIndex++;
                playback.currentFile = playback.songlist.get(playback.songlistIndex);
                playNextInThread(playback);
            } else {
                activePlaybacks.remove(playback.ownerUUID);
            }
        }
    }

    public static void stopPlayback(UUID ownerUUID) {
        ActivePlayback p = activePlaybacks.get(ownerUUID);
        if (p != null) p.stopped = true;
        activePlaybacks.remove(ownerUUID);
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

    static class MusicSonglist {
        List<String> songs;
    }
}
