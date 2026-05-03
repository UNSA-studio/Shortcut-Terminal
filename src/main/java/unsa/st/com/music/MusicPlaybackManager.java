package unsa.st.com.music;

// MP3 播放模块已暂时关闭维护。
// 原因：在安卓环境下，Java 标准音频 API (javax.sound) 和第三方 MP3 解码库均无法稳定工作。
// 如需启用，请删除此文件并恢复 Git 历史版本。
public class MusicPlaybackManager {
    public static String startPlayback(java.util.UUID ownerUUID, String filePath, int loop) {
        return "MP3 module is currently disabled.";
    }
}
