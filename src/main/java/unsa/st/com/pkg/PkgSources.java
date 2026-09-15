package unsa.st.com.pkg;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * pkg 源配置：发行版（Debian / Ubuntu）+ 版本（release）+ 镜像站（mirror）三级可选。
 * 配置持久化在 Program/etc/pkg-sources.json，重启后保留。
 * 不再强制用户只能用一个源——默认首个为国内高速镜像，失败自动按顺序回退。
 */
public final class PkgSources {
    private PkgSources() {}

    /** 单个镜像站。 */
    public static class Mirror {
        public String id;
        public String label;
        public String distro;
        public String baseUrl;
        public Mirror() {}
        public Mirror(String id, String label, String distro, String baseUrl) {
            this.id = id; this.label = label; this.distro = distro; this.baseUrl = baseUrl;
        }
    }

    /** 持久化配置。 */
    public static class Config {
        public String distro = "debian";
        public String release = "bookworm";
        public String mirror = "tuna";
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 内置镜像清单（国内优先）。 */
    private static final List<Mirror> MIRRORS = List.of(
            // ===== Debian 镜像 =====
            new Mirror("tuna",     "Tsinghua TUNA",  "debian", "https://mirrors.tuna.tsinghua.edu.cn/debian"),
            new Mirror("ustc",     "USTC",           "debian", "https://mirrors.ustc.edu.cn/debian"),
            new Mirror("aliyun",   "Aliyun",         "debian", "https://mirrors.aliyun.com/debian"),
            new Mirror("huawei",   "Huawei Cloud",   "debian", "https://mirrors.huaweicloud.com/debian"),
            new Mirror("tencent",  "Tencent Cloud",  "debian", "https://mirrors.cloud.tencent.com/debian"),
            new Mirror("official", "Debian Official","debian", "https://deb.debian.org/debian"),
            // ===== Ubuntu 镜像 =====
            new Mirror("tuna",     "Tsinghua TUNA",  "ubuntu", "https://mirrors.tuna.tsinghua.edu.cn/ubuntu"),
            new Mirror("ustc",     "USTC",           "ubuntu", "https://mirrors.ustc.edu.cn/ubuntu"),
            new Mirror("aliyun",   "Aliyun",         "ubuntu", "https://mirrors.aliyun.com/ubuntu"),
            new Mirror("huawei",   "Huawei Cloud",   "ubuntu", "https://mirrors.huaweicloud.com/ubuntu"),
            new Mirror("official", "Ubuntu Official","ubuntu", "http://archive.ubuntu.com/ubuntu")
    );

    /** 发行版 → 可选版本。 */
    private static final Map<String, String[]> RELEASES = new LinkedHashMap<>();
    static {
        RELEASES.put("debian", new String[]{"bookworm", "trixie", "sid"});
        RELEASES.put("ubuntu", new String[]{"jammy", "noble"});
    }

    // ==================== 持久化 ====================

    private static Path configPath(boolean isClient) {
        return PkgManager.getGameDir(isClient).resolve("Program").resolve("etc").resolve("pkg-sources.json");
    }

    public static Config load(boolean isClient) {
        Path f = configPath(isClient);
        if (Files.exists(f)) {
            try {
                Config c = GSON.fromJson(Files.readString(f), Config.class);
                if (c != null && isValidDistro(c.distro)) {
                    if (c.mirror == null || c.mirror.isEmpty()) c.mirror = "tuna";
                    if (c.release == null || c.release.isEmpty()) c.release = releasesFor(c.distro)[0];
                    return c;
                }
            } catch (Exception ignored) {}
        }
        return new Config();
    }

    public static void save(boolean isClient, Config cfg) {
        try {
            Path f = configPath(isClient);
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(cfg));
        } catch (IOException ignored) {}
    }

    // ==================== 查询 ====================

    public static List<Mirror> mirrorsFor(String distro) {
        List<Mirror> out = new ArrayList<>();
        for (Mirror m : MIRRORS) if (m.distro.equals(distro)) out.add(m);
        return out;
    }

    /** 解析当前配置对应的镜像（找不到时回退到该发行版首个）。 */
    public static Mirror resolve(Config cfg) {
        for (Mirror m : MIRRORS) {
            if (m.distro.equals(cfg.distro) && m.id.equals(cfg.mirror)) return m;
        }
        return mirrorsFor(cfg.distro).get(0);
    }

    /** 下载/更新的尝试顺序：当前镜像优先，其余同发行版镜像兜底。 */
    public static List<Mirror> fallbackOrder(Config cfg) {
        List<Mirror> all = new ArrayList<>(mirrorsFor(cfg.distro));
        Mirror cur = resolve(cfg);
        all.remove(cur);
        all.add(0, cur);
        return all;
    }

    public static String[] releasesFor(String distro) {
        return RELEASES.getOrDefault(distro, new String[]{"bookworm"});
    }

    public static boolean isValidDistro(String distro) {
        return distro != null && RELEASES.containsKey(distro);
    }
}