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
        "https://mirrors.tuna.tsinghua.edu.cn/debian",
        "https://mirrors.ustc.edu.cn/debian",
        "https://deb.debian.org/debian"
    };
    private static String activeRepo = null;
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
        activeRepo = null;
        String osArch = System.getProperty("os.arch").toLowerCase();
        String arch = osArch.contains("arm") || osArch.contains("aarch64") ? "arm64" : "amd64";
        String version = "bookworm";
        for (String repo : REPO_URLS) {
            try {
                String urlStr = repo + "/dists/" + version + "/main/binary-" + arch + "/Packages.gz";
                ShortcutTerminal.LOGGER.info("Trying to fetch package index from: {}", urlStr);
                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setRequestProperty("User-Agent", "ShortcutTerminal/1.0");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(15000);
                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    ShortcutTerminal.LOGGER.warn("Failed to fetch from {}: HTTP {}", repo, responseCode);
                    continue;
                }
                InputStream is = conn.getInputStream();
                if ("gzip".equals(conn.getHeaderField("Content-Encoding"))) {
                    is = new GZIPInputStream(is);
                }
                parsePackagesStream(new GZIPInputStream(is));
                activeRepo = repo;
                break;
            } catch (Exception e) {
                ShortcutTerminal.LOGGER.warn("Failed to fetch from {}: {}", repo, e.getMessage());
            }
        }
        if (remoteIndex.isEmpty()) {
            PackageInfo busybox = new PackageInfo();
            busybox.packageName = "busybox";
            busybox.version = "1.36.1";
            busybox.architecture = arch;
            busybox.filename = "pool/main/b/busybox/busybox_1.36.1_" + arch + ".deb";
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
                    if (info.packageName != null) {
                        remoteIndex.put(info.packageName, info);
                    }
                    block.setLength(0);
                }
            } else {
                block.append(line).append("\n");
            }
        }
        if (block.length() > 0) {
            PackageInfo info = PackageInfo.parse(block.toString());
            if (info.packageName != null) {
                remoteIndex.put(info.packageName, info);
            }
        }
    }

    public static String install(String packageName, boolean isClient) {
        if (packageName.equalsIgnoreCase("simulationpackage")) {
            return handleSimulationPackage();
        }

        Map<String, PackageInfo> localDb = loadLocalDatabase(isClient);
        if (!remoteIndex.containsKey(packageName)) return "Package not found: " + packageName;
        if (localDb.containsKey(packageName)) return "Package already installed: " + packageName;
        PackageInfo pkg = remoteIndex.get(packageName);

        try {
            String repo = activeRepo != null ? activeRepo : REPO_URLS[0];
            URL url = new URL(repo + "/" + pkg.filename);
            Path tmpFile = Files.createTempFile("pkg_", ".deb");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "ShortcutTerminal/1.0");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);
            try (InputStream in = conn.getInputStream()) {
                Files.copy(in, tmpFile, StandardCopyOption.REPLACE_EXISTING);
            }

            Path extractDir = Files.createTempDirectory("pkg_extract");
            extractDeb(tmpFile, extractDir);

            Path dataDir = extractDir.resolve("data");
            if (Files.exists(dataDir)) {
                copyDirectory(dataDir, getProgramPath(isClient));
            } else {
                copyDirectory(extractDir, getProgramPath(isClient));
            }

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
            String message = "A new PowerShell administrator process will now be invoked...";
            try {
                String cmd = "powershell Start-Process powershell -Verb runAs -ArgumentList 'wsl --install'";
                Runtime.getRuntime().exec(cmd);
                return message + "\n\nPowerShell launched. Follow on-screen instructions.";
            } catch (IOException e) {
                return message + "\n\nFailed to launch PowerShell. Run manually: wsl --install";
            }
        } else if (osName.contains("linux")) {
            String javaVendor = System.getProperty("java.vendor", "").toLowerCase();
            if (javaVendor.contains("android")) {
                return "Android detected. PKG may not be fully effective.";
            }
            return "You are already on Linux. No need to install simulation package.";
        } else if (osName.contains("mac")) {
            return "macOS is not yet supported.";
        }
        return "Your operating system is not recognized.";
    }

    private static void extractDeb(Path debFile, Path destDir) throws IOException {
        try (ArArchiveInputStream arIn = new ArArchiveInputStream(new BufferedInputStream(Files.newInputStream(debFile)))) {
            ArArchiveEntry entry;
            while ((entry = arIn.getNextArEntry()) != null) {
                String name = entry.getName();
                if (name.equals("data.tar.gz")) {
                    Path outFile = destDir.resolve(name);
                    Files.copy(arIn, outFile, StandardCopyOption.REPLACE_EXISTING);
                    try (TarArchiveInputStream tarIn = new TarArchiveInputStream(new GzipCompressorInputStream(Files.newInputStream(outFile)))) {
                        extractTar(tarIn, destDir.resolve("data"));
                    }
                } else if (name.equals("data.tar.xz")) {
                    // 尝试 XZ 解压，如果失败则记录错误并跳过
                    try {
                        Path outFile = destDir.resolve(name);
                        Files.copy(arIn, outFile, StandardCopyOption.REPLACE_EXISTING);
                        try (TarArchiveInputStream tarIn = new TarArchiveInputStream(new XZCompressorInputStream(Files.newInputStream(outFile)))) {
                            extractTar(tarIn, destDir.resolve("data"));
                        }
                    } catch (NoClassDefFoundError | Exception e) {
                        ShortcutTerminal.LOGGER.warn("XZ decompression failed, skipping data.tar.xz: {}", e.getMessage());
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

    public static String remove(String packageName, boolean isClient) {
        Map<String, PackageInfo> localDb = loadLocalDatabase(isClient);
        if (!localDb.containsKey(packageName)) return "Package not installed: " + packageName;
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
