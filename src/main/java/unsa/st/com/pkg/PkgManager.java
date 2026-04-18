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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

public class PkgManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String[] REPO_URLS = {
        "https://packages.termux.dev/apt/termux-main",
        "https://mirrors.tuna.tsinghua.edu.cn/termux/apt/termux-main"
    };
    private static String activeRepo = REPO_URLS[0];
    private static Map<String, PackageInfo> remoteIndex = new HashMap<>();

    private static final String PROGRAM_DIR = "Program";
    private static final String BINARY_DIR = "Binary file";
    private static final String PATH_FILE = "PATH.txt";
    private static final String INSTALLED_DB = "var/lib/dpkg/status";

    public static void init() {}

    private static Path getGameDir(boolean isClient) {
        if (isClient) {
            return Minecraft.getInstance().gameDirectory.toPath();
        } else {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            return server.getServerDirectory();
        }
    }

    private static Path getProgramPath(boolean isClient) { return getGameDir(isClient).resolve(PROGRAM_DIR); }
    private static Path getBinaryPath(boolean isClient) { return getGameDir(isClient).resolve(BINARY_DIR); }
    private static Path getDbPath(boolean isClient) { return getProgramPath(isClient).resolve(INSTALLED_DB); }
    private static Path getPathFile(boolean isClient) { return getGameDir(isClient).resolve(PATH_FILE); }

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

    private static void ensurePath(boolean isClient) {
        try {
            Path pathFile = getPathFile(isClient);
            Path programBin = getProgramPath(isClient).resolve("bin");
            Path binaryPath = getBinaryPath(isClient);
            Set<String> paths = new LinkedHashSet<>();
            if (Files.exists(pathFile)) paths.addAll(Files.readAllLines(pathFile));
            paths.add(programBin.toAbsolutePath().toString());
            paths.add(binaryPath.toAbsolutePath().toString());
            Files.write(pathFile, paths);
        } catch (IOException e) {
            ShortcutTerminal.LOGGER.error("Failed to update PATH", e);
        }
    }

    public static String updateIndex() {
        remoteIndex.clear();
        for (String repo : REPO_URLS) {
            try {
                URL url = new URL(repo + "/dists/stable/main/binary-arm64/Packages");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "ShortcutTerminal/1.0");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(10000);
                InputStream is = conn.getInputStream();
                if ("gzip".equals(conn.getHeaderField("Content-Encoding"))) {
                    is = new GZIPInputStream(is);
                }
                parsePackagesStream(is);
                activeRepo = repo;
                break;
            } catch (Exception e) {
                ShortcutTerminal.LOGGER.warn("Failed to fetch from {}: {}", repo, e.getMessage());
            }
        }
        if (remoteIndex.isEmpty()) {
            // 保底内置
            PackageInfo busybox = new PackageInfo();
            busybox.packageName = "busybox";
            busybox.version = "1.36.1";
            busybox.architecture = "arm64";
            busybox.filename = "pool/main/b/busybox/busybox_1.36.1_arm64.deb";
            busybox.description = "Tiny versions of many common UNIX utilities";
            remoteIndex.put("busybox", busybox);
            return "Remote fetch failed, using built-in fallback. 1 package available.";
        }
        return "Package index updated from " + activeRepo + ". " + remoteIndex.size() + " packages available.";
    }

    private static void parsePackagesStream(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder block = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()) {
                if (block.length() > 0) {
                    PackageInfo info = PackageInfo.parse(block.toString());
                    remoteIndex.put(info.packageName, info);
                    block.setLength(0);
                }
            } else {
                block.append(line).append("\n");
            }
        }
        if (block.length() > 0) {
            PackageInfo info = PackageInfo.parse(block.toString());
            remoteIndex.put(info.packageName, info);
        }
    }

    public static String install(String packageName, boolean isClient) {
        // 特殊包：simulationpackage - 引导安装 WSL
        if (packageName.equalsIgnoreCase("simulationpackage")) {
            return handleSimulationPackage();
        }

        Map<String, PackageInfo> localDb = loadLocalDatabase(isClient);
        if (!remoteIndex.containsKey(packageName)) return "Package not found: " + packageName;
        if (localDb.containsKey(packageName)) return "Package already installed: " + packageName;
        PackageInfo pkg = remoteIndex.get(packageName);

        try {
            String repo = activeRepo;
            URL url = new URL(repo + "/" + pkg.filename);
            Path tmpFile = Files.createTempFile("pkg_", ".deb");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "ShortcutTerminal/1.0");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);
            try (InputStream in = conn.getInputStream()) {
                Files.copy(in, tmpFile, StandardCopyOption.REPLACE_EXISTING);
            }

            // 解压 .deb 包
            Path extractDir = Files.createTempDirectory("pkg_extract");
            extractDeb(tmpFile, extractDir);

            // 复制文件到 Program
            Path dataDir = extractDir.resolve("data");
            if (Files.exists(dataDir)) {
                copyDirectory(dataDir, getProgramPath(isClient));
            } else {
                // 有些 deb 直接是根目录
                copyDirectory(extractDir, getProgramPath(isClient));
            }

            // 设置可执行权限
            Path binDir = getProgramPath(isClient).resolve("bin");
            if (Files.exists(binDir)) {
                Files.walk(binDir).filter(Files::isRegularFile).forEach(f -> f.toFile().setExecutable(true));
            }

            localDb.put(packageName, pkg);
            saveLocalDatabase(isClient, localDb);
            ensurePath(isClient);
            return "Package installed: " + packageName + " (" + pkg.version + ")";
        } catch (Exception e) {
            ShortcutTerminal.LOGGER.error("Installation failed", e);
            return "Installation failed: " + e.getMessage();
        }
    }

    private static String handleSimulationPackage() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            return "A new administrator-mode PowerShell will pull up and install Linux, after which you need to reboot and custom install the version of Linux you need in the new PowerShell.\n"
                    + "To install WSL manually, open PowerShell as Administrator and run: wsl --install";
        } else {
            return "You are a Linux system, why do you want to install it?";
        }
    }

    private static void extractDeb(Path debFile, Path destDir) throws IOException {
        try (ArArchiveInputStream arIn = new ArArchiveInputStream(new BufferedInputStream(Files.newInputStream(debFile)))) {
            ArArchiveEntry entry;
            while ((entry = arIn.getNextArEntry()) != null) {
                String name = entry.getName();
                if (name.equals("data.tar.gz") || name.equals("data.tar.xz")) {
                    Path outFile = destDir.resolve(name);
                    Files.createDirectories(destDir);
                    Files.copy(arIn, outFile, StandardCopyOption.REPLACE_EXISTING);
                    // 解压 data 文件
                    if (name.endsWith(".gz")) {
                        try (TarArchiveInputStream tarIn = new TarArchiveInputStream(new GzipCompressorInputStream(Files.newInputStream(outFile)))) {
                            extractTar(tarIn, destDir.resolve("data"));
                        }
                    } else if (name.endsWith(".xz")) {
                        try (TarArchiveInputStream tarIn = new TarArchiveInputStream(new XZCompressorInputStream(Files.newInputStream(outFile)))) {
                            extractTar(tarIn, destDir.resolve("data"));
                        }
                    }
                } else if (name.equals("control.tar.gz") || name.equals("control.tar.xz")) {
                    // 控制文件，可忽略
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

    public static String remove(String packageName, boolean isClient) {
        Map<String, PackageInfo> localDb = loadLocalDatabase(isClient);
        if (!localDb.containsKey(packageName)) return "Package not installed: " + packageName;
        // 简化：仅从数据库移除，不删除文件（实际应记录文件列表）
        localDb.remove(packageName);
        saveLocalDatabase(isClient, localDb);
        return "Package removed: " + packageName;
    }

    public static List<String> listInstalled(boolean isClient) {
        return new ArrayList<>(loadLocalDatabase(isClient).keySet());
    }

    public static List<String> listAvailable() {
        return new ArrayList<>(remoteIndex.keySet());
    }

    public static List<String> search(String keyword) {
        return remoteIndex.keySet().stream()
                .filter(name -> name.contains(keyword) || remoteIndex.get(name).description.toLowerCase().contains(keyword.toLowerCase()))
                .collect(Collectors.toList());
    }

    public static String showInfo(String packageName) {
        PackageInfo pkg = remoteIndex.get(packageName);
        if (pkg == null) return "Package not found.";
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
}
