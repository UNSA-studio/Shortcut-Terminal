package unsa.st.com.pkg;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.apache.commons.compress.archivers.ar.ArArchiveEntry;
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import unsa.st.com.ShortcutTerminal;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class PkgManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String[] MIRRORS = {
        "https://mirrors.tuna.tsinghua.edu.cn/debian",
        "https://mirrors.ustc.edu.cn/debian",
        "https://deb.debian.org/debian"
    };
    private static final String DEBIAN_VERSION = "bookworm";
    private static Map<String, PackageInfo> remoteIndex = new HashMap<>();
    private static boolean indexLoaded = false;

    private static final String PROGRAM_DIR = "Program";
    private static final String BINARY_DIR = "Binary file";
    private static final String PATH_FILE = "PATH.txt";
    private static final String INSTALLED_DB = "var/lib/dpkg/status";
    private static final String INDEX_CACHE = "var/cache/pkg/index.json";

    // ========== 路径工具 ==========
    private static Path getGameDir(boolean isClient) {
        if (isClient) {
            return Minecraft.getInstance().gameDirectory.toPath();
        } else {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            return server.getServerDirectory();
        }
    }

    private static Path getProgramPath(boolean isClient) {
        return getGameDir(isClient).resolve(PROGRAM_DIR);
    }

    private static Path getBinaryPath(boolean isClient) {
        return getGameDir(isClient).resolve(BINARY_DIR);
    }

    private static Path getDbPath(boolean isClient) {
        return getProgramPath(isClient).resolve(INSTALLED_DB);
    }

    public static Path getPathFile(boolean isClient) {
        return getGameDir(isClient).resolve(PATH_FILE);
    }

    private static Path getIndexCachePath(boolean isClient) {
        return getGameDir(isClient).resolve(INDEX_CACHE);
    }

    // ========== 本地数据库 ==========
    private static Map<String, PackageInfo> loadLocalDatabase(boolean isClient) {
        Path dbPath = getDbPath(isClient);
        if (Files.exists(dbPath)) {
            try {
                String content = Files.readString(dbPath);
                Map<String, PackageInfo> loaded = GSON.fromJson(content, new TypeToken<Map<String, PackageInfo>>(){}.getType());
                return loaded != null ? loaded : new HashMap<>();
            } catch (IOException e) {
                ShortcutTerminal.LOGGER.error("Failed to load local package database", e);
            }
        }
        return new HashMap<>();
    }

    private static void saveLocalDatabase(boolean isClient, Map<String, PackageInfo> db) {
        try {
            Path dbPath = getDbPath(isClient);
            Files.createDirectories(dbPath.getParent());
            Files.writeString(dbPath, GSON.toJson(db));
        } catch (IOException e) {
            ShortcutTerminal.LOGGER.error("Failed to save local package database", e);
        }
    }

    // ========== PATH 维护 ==========
    private static void ensurePath(boolean isClient) {
        try {
            Path pathFile = getPathFile(isClient);
            Path programBin = getProgramPath(isClient).resolve("bin");
            Path programSbin = getProgramPath(isClient).resolve("sbin");
            Path programUsrBin = getProgramPath(isClient).resolve("usr/bin");
            Path programUsrSbin = getProgramPath(isClient).resolve("usr/sbin");
            Path binaryPath = getBinaryPath(isClient);

            Set<String> paths = new LinkedHashSet<>();
            if (Files.exists(pathFile)) {
                paths.addAll(Files.readAllLines(pathFile));
            }
            paths.add(programBin.toAbsolutePath().toString());
            paths.add(programSbin.toAbsolutePath().toString());
            paths.add(programUsrBin.toAbsolutePath().toString());
            paths.add(programUsrSbin.toAbsolutePath().toString());
            paths.add(binaryPath.toAbsolutePath().toString());

            Files.write(pathFile, paths);
        } catch (IOException e) {
            ShortcutTerminal.LOGGER.error("Failed to update PATH", e);
        }
    }

    // ========== 索引缓存 ==========
    private static void saveIndexCache(boolean isClient) {
        try {
            Path cachePath = getIndexCachePath(isClient);
            Files.createDirectories(cachePath.getParent());
            Files.writeString(cachePath, GSON.toJson(remoteIndex));
        } catch (IOException e) {
            ShortcutTerminal.LOGGER.warn("Failed to save index cache", e);
        }
    }

    private static boolean loadIndexCache(boolean isClient) {
        Path cachePath = getIndexCachePath(isClient);
        if (!Files.exists(cachePath)) return false;
        try {
            String json = Files.readString(cachePath);
            Map<String, PackageInfo> cached = GSON.fromJson(json, new TypeToken<Map<String, PackageInfo>>(){}.getType());
            if (cached != null) {
                remoteIndex = cached;
                indexLoaded = true;
                return true;
            }
        } catch (Exception e) {
            ShortcutTerminal.LOGGER.warn("Failed to load index cache", e);
        }
        return false;
    }

    // ========== 更新索引（核心修复） ==========
    public static String updateIndex(boolean isClient, boolean force) {
        // 如果已经加载且不是强制刷新，直接返回
        if (indexLoaded && !force) {
            return "Index already loaded. Use 'pkg update force' to refresh.";
        }

        // 尝试加载本地缓存（仅当非强制刷新时）
        if (!force && loadIndexCache(isClient)) {
            return "Loaded cached index (" + remoteIndex.size() + " packages). Use 'pkg update force' to refresh from network.";
        }

        // 从网络获取
        String arch = System.getProperty("os.arch").toLowerCase().contains("arm") ? "arm64" : "amd64";
        List<String> errors = new ArrayList<>();

        for (String mirror : MIRRORS) {
            String base = mirror + "/dists/" + DEBIAN_VERSION + "/main/binary-" + arch;
            // 优先尝试 .gz（更通用）
            for (String suffix : new String[]{"/Packages.gz", "/Packages.xz"}) {
                String urlStr = base + suffix;
                try {
                    ShortcutTerminal.LOGGER.info("Trying to fetch index from: {}", urlStr);
                    HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                    conn.setRequestProperty("User-Agent", "ShortcutTerminal/1.0");
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(15000);

                    if (conn.getResponseCode() != 200) {
                        errors.add(urlStr + " -> HTTP " + conn.getResponseCode());
                        continue;
                    }

                    InputStream is = conn.getInputStream();
                    if (suffix.endsWith(".gz")) {
                        parsePackagesStream(new GzipCompressorInputStream(is));
                    } else {
                        parsePackagesStream(new XZCompressorInputStream(is));
                    }

                    indexLoaded = true;
                    saveIndexCache(isClient);
                    return "Index updated from " + mirror + " (" + remoteIndex.size() + " packages).";
                } catch (Exception e) {
                    errors.add(urlStr + " -> " + e.getMessage());
                }
            }
        }

        // 所有镜像失败，使用保底
        ShortcutTerminal.LOGGER.error("All mirrors failed: {}", String.join("; ", errors));
        fallbackIndex();
        indexLoaded = true;
        return "Network fetch failed, using built-in fallback (1 package). Errors: " + String.join(", ", errors);
    }

    public static String updateIndex() {
        // 无参版本默认使用缓存优先
        return updateIndex(false, false);
    }

    private static void fallbackIndex() {
        remoteIndex.clear();
        PackageInfo busybox = new PackageInfo();
        busybox.packageName = "busybox";
        busybox.version = "1.36.1";
        busybox.architecture = "arm64";
        busybox.filename = "pool/main/b/busybox/busybox_1.36.1_arm64.deb";
        busybox.description = "Tiny versions of many common UNIX utilities";
        remoteIndex.put("busybox", busybox);
    }

    private static void parsePackagesStream(InputStream is) throws IOException {
        remoteIndex.clear();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder block = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()) {
                if (block.length() > 0) {
                    PackageInfo info = PackageInfo.parse(block.toString());
                    if (info.packageName != null) {
                        remoteIndex.put(info.packageName, info);
                    }
                    block.setLength(0);
                }
            } else {
                block.append(line).append("\n");
            }
        }
        // 最后一块
        if (block.length() > 0) {
            PackageInfo info = PackageInfo.parse(block.toString());
            if (info.packageName != null) {
                remoteIndex.put(info.packageName, info);
            }
        }
    }

    // ========== 安装 ==========
    public static String install(String packageName, boolean isClient) {
        // 确保索引已加载
        if (!indexLoaded) {
            updateIndex(isClient, false);
        }

        Map<String, PackageInfo> localDb = loadLocalDatabase(isClient);
        if (!remoteIndex.containsKey(packageName)) {
            return "Package not found: " + packageName;
        }
        if (localDb.containsKey(packageName)) {
            return "Package already installed: " + packageName;
        }

        PackageInfo pkg = remoteIndex.get(packageName);
        String baseUrl = MIRRORS[0]; // 默认用第一个镜像
        String debUrl = baseUrl + "/" + pkg.filename;

        Path tmpDeb = null;
        Path extractDir = null;
        try {
            // 下载 .deb
            tmpDeb = Files.createTempFile("pkg_", ".deb");
            HttpURLConnection conn = (HttpURLConnection) new URL(debUrl).openConnection();
            conn.setRequestProperty("User-Agent", "ShortcutTerminal/1.0");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);
            try (InputStream in = conn.getInputStream()) {
                Files.copy(in, tmpDeb, StandardCopyOption.REPLACE_EXISTING);
            }

            // 解压
            extractDir = Files.createTempDirectory("pkg_extract");
            extractDeb(tmpDeb, extractDir);

            // 复制文件
            Path dataDir = extractDir.resolve("data");
            Path targetDir = getProgramPath(isClient);
            if (Files.exists(dataDir)) {
                copyDirectory(dataDir, targetDir);
            } else {
                copyDirectory(extractDir, targetDir);
            }

            // 设置可执行权限
            setExecutableRecursive(targetDir.resolve("bin"));
            setExecutableRecursive(targetDir.resolve("sbin"));
            setExecutableRecursive(targetDir.resolve("usr/bin"));
            setExecutableRecursive(targetDir.resolve("usr/sbin"));

            localDb.put(packageName, pkg);
            saveLocalDatabase(isClient, localDb);
            ensurePath(isClient);

            return "Package installed: " + packageName + " (" + pkg.version + ")";
        } catch (Exception e) {
            ShortcutTerminal.LOGGER.error("Installation failed for " + packageName, e);
            return "Installation failed: " + e.getMessage();
        } finally {
            // 清理临时文件
            if (tmpDeb != null) try { Files.deleteIfExists(tmpDeb); } catch (IOException ignored) {}
            if (extractDir != null) deleteRecursive(extractDir);
        }
    }

    private static void setExecutableRecursive(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        Files.walk(dir).filter(Files::isRegularFile).forEach(f -> f.toFile().setExecutable(true));
    }

    // ========== 解压 .deb（关键修复：XZ 错误不再被吞） ==========
    private static void extractDeb(Path debFile, Path destDir) throws IOException {
        try (ArArchiveInputStream arIn = new ArArchiveInputStream(new BufferedInputStream(Files.newInputStream(debFile)))) {
            ArArchiveEntry entry;
            while ((entry = arIn.getNextArEntry()) != null) {
                String name = entry.getName();
                if (name.equals("data.tar.gz") || name.equals("data.tar.xz")) {
                    Path outFile = destDir.resolve(name);
                    Files.createDirectories(destDir);
                    Files.copy(arIn, outFile, StandardCopyOption.REPLACE_EXISTING);

                    try {
                        if (name.endsWith(".gz")) {
                            try (TarArchiveInputStream tarIn = new TarArchiveInputStream(
                                    new GzipCompressorInputStream(Files.newInputStream(outFile)))) {
                                extractTar(tarIn, destDir.resolve("data"));
                            }
                        } else if (name.endsWith(".xz")) {
                            try (TarArchiveInputStream tarIn = new TarArchiveInputStream(
                                    new XZCompressorInputStream(Files.newInputStream(outFile)))) {
                                extractTar(tarIn, destDir.resolve("data"));
                            }
                        }
                    } catch (Exception e) {
                        // 解压失败，抛出明确异常
                        throw new IOException("Failed to extract " + name + ": " + e.getMessage(), e);
                    }
                }
            }
        }
    }

    private static void extractTar(TarArchiveInputStream tarIn, Path destDir) throws IOException {
        TarArchiveEntry entry;
        while ((entry = tarIn.getNextTarEntry()) != null) {
            Path outFile = destDir.resolve(entry.getName());
            if (entry.isDirectory()) {
                Files.createDirectories(outFile);
            } else {
                Files.createDirectories(outFile.getParent());
                Files.copy(tarIn, outFile, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source).forEach(src -> {
            try {
                Path dest = target.resolve(source.relativize(src));
                if (Files.isDirectory(src)) {
                    Files.createDirectories(dest);
                } else {
                    Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static void deleteRecursive(Path path) {
        try {
            if (Files.exists(path)) {
                Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
            }
        } catch (IOException ignored) {}
    }

    // ========== 其他命令 ==========
    public static String remove(String packageName, boolean isClient) {
        Map<String, PackageInfo> localDb = loadLocalDatabase(isClient);
        if (!localDb.containsKey(packageName)) return "Package not installed: " + packageName;
        localDb.remove(packageName);
        saveLocalDatabase(isClient, localDb);
        return "Package removed: " + packageName + " (files remain on disk)";
    }

    public static List<String> listInstalled(boolean isClient) {
        return new ArrayList<>(loadLocalDatabase(isClient).keySet());
    }

    public static List<String> search(String keyword) {
        if (!indexLoaded) updateIndex(false, false);
        return remoteIndex.keySet().stream()
                .filter(name -> name.toLowerCase().contains(keyword.toLowerCase()) ||
                        remoteIndex.get(name).description.toLowerCase().contains(keyword.toLowerCase()))
                .collect(Collectors.toList());
    }

    public static String showInfo(String packageName) {
        if (!indexLoaded) updateIndex(false, false);
        PackageInfo pkg = remoteIndex.get(packageName);
        if (pkg == null) return "Package not found in index.";
        return String.format("Package: %s\nVersion: %s\nArchitecture: %s\nDescription: %s",
                pkg.packageName, pkg.version, pkg.architecture, pkg.description);
    }

    public static List<String> getPathEntries(boolean isClient) {
        try {
            Path pathFile = getPathFile(isClient);
            if (Files.exists(pathFile)) {
                return Files.readAllLines(pathFile);
            }
        } catch (IOException ignored) {}
        return new ArrayList<>();
    }

    // 兼容旧调用
    public static String getHelp() {
        return "pkg update [force] - refresh package index\n" +
               "pkg search <keyword> - search packages\n" +
               "pkg install <pkg> - install a package\n" +
               "pkg remove <pkg> - mark as removed\n" +
               "pkg list - list installed\n" +
               "pkg show <pkg> - show package details";
    }
}
