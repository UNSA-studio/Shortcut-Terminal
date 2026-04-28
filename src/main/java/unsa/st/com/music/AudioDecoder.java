package unsa.st.com.music;

import javazoom.jl.player.Player;
import com.github.axet.flac.FLACDecoder;

import javax.sound.sampled.*;
import java.io.*;

public class AudioDecoder {
    public static void decodeMP3(File file, SourceDataLine line) throws Exception {
        try (FileInputStream fis = new FileInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(fis)) {
            Player player = new Player(bis);
            player.play();
        }
    }

    public static void decodeFLAC(File file, SourceDataLine line) throws Exception {
        FLACDecoder decoder = new FLACDecoder(new FileInputStream(file));
        byte[] buf = new byte[4096];
        int len;
        while ((len = decoder.read(buf)) > 0) {
            line.write(buf, 0, len);
        }
        decoder.close();
    }

    public static void decodeOGG(File file, SourceDataLine line) throws Exception {
        com.jcraft.jorbis.VorbisFile vf = new com.jcraft.jorbis.VorbisFile(file.getAbsolutePath());
        byte[] buf = new byte[4096 * 4];
        int len;
        while ((len = vf.read(buf, 0, buf.length)) > 0) {
            line.write(buf, 0, len);
        }
        vf.close();
    }

    public static void decodeWAV(File file, SourceDataLine line) throws Exception {
        AudioInputStream audioStream = AudioSystem.getAudioInputStream(file);
        byte[] buf = new byte[8192];
        int len;
        while ((len = audioStream.read(buf)) != -1) {
            line.write(buf, 0, len);
        }
        audioStream.close();
    }
}
