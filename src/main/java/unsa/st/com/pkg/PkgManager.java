package unsa.st.com.pkg;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import unsa.st.com.ShortcutTerminal;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;

public class PkgManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String REPO_URL = "https://raw.githubusercontent.com/termux/termux-packages/master/packages";
    private static final String PROGRAM_DIR = "Program";
    private static final String BINARY_DIR = "Binary file";
    private static final String PATH_FILE = "PATH.txt";
    private static final String INSTALLED_DB = "var/lib/dpkg/status";

    private static Path gameDir;
    private static Path programPath;
    private static Path binaryPath;
    private static Path dbPath;
    private static Path pathFile;
    private static Path pathBackupFile;

    private static Map<String, PackageInfo> remoteIndex = new HashMap<>();
    private static Map<String, PackageInfo> localDb = new HashMap<>();

    public static void init() {
        Minecraft mc = Minecraft.getInstance();
        gameDir = mc.gameDirectory.toPath();
        programPath = gameDir.resolve(PROGRAM_DIR);
        binaryPath = gameDir.resolve(BINARY_DIR);
        dbPath = programPath.resolve(INSTALLED_DB);
        pathFile = gameDir.resolve(PATH_FILE);
        pathBackupFile = binaryPath.resolve(PATH_FILE);

        try {
            Files.createDirectories(programPath);
            Files.createDirectories(binaryPath);
            Files.createDirectories(dbPath.getParent());
        } catch (IOException e) {
            ShortcutTerminal.LOGGER.error("Failed to create directories", e);
        }

        loadLocalDatabase();
        ensurePath();
    }

    private static void ensurePath() {
        try {
            Set<String> paths = new LinkedHashSet<>();
            if (Files.exists(pathFile)) {
                paths.addAll(Files.readAllLines(pathFile));
            }
            paths.add(programPath.resolve("bin").toAbsolutePath().toString());
            paths.add(binaryPath.toAbsolutePath().toString());
            Files.write(pathFile, paths);
            Files.createDirectories(binaryPath);
            Files.write(pathBackupFile, paths);
        } catch (IOException e) {
            ShortcutTerminal.LOGGER.error("Failed to update PATH", e);
        }
    }

    private static void loadLocalDatabase() {
        localDb.clear();
        if (Files.exists(dbPath)) {
            try {
                String content = Files.readString(dbPath);
                Map<String, PackageInfo> loaded = GSON.fromJson(content, new TypeToken<Map<String, PackageInfo>>(){}.getType());
                if (loaded != null) localDb = loaded;
            } catch (IOException e) {
                ShortcutTerminal.LOGGER.error("Failed to load local package database", e);
            }
        }
    }

    private static void saveLocalDatabase() {
        try {
            Files.createDirectories(dbPath.getParent());
            Files.writeString(dbPath, GSON.toJson(localDb));
        } catch (IOException e) {
            ShortcutTerminal.LOGGER.error("Failed to save local package database", e);
        }
    }

    public static String updateIndex() {
        try {
            // 使用 Termux 的包列表作为演示 (简化，真实场景应使用 Packages 文件)
            // 这里直接返回一些预定义包
            remoteIndex.clear();
            
            // BusyBox
            PackageInfo busybox = new PackageInfo();
            busybox.packageName = "busybox";
            busybox.version = "1.36.1";
            busybox.architecture = "arm64";
            busybox.filename = "pool/main/b/busybox/busybox_1.36.1_arm64.ipk";
            busybox.description = "Tiny versions of many common UNIX utilities";
            remoteIndex.put("busybox", busybox);
            
            // Toybox
            PackageInfo toybox = new PackageInfo();
            toybox.packageName = "toybox";
            toybox.version = "0.8.11";
            toybox.architecture = "arm64";
            toybox.filename = "pool/main/t/toybox/toybox_0.8.11_arm64.ipk";
            toybox.description = "All-in-one Linux command line";
            remoteIndex.put("toybox", toybox);
            
            return "Package index updated. " + remoteIndex.size() + " packages available.";
        } catch (Exception e) {
            return "Failed to update index: " + e.getMessage();
        }
    }

    public static String install(String packageName) {
        if (!remoteIndex.containsKey(packageName)) {
            return "Package not found: " + packageName;
        }
        PackageInfo pkg = remoteIndex.get(packageName);
        if (localDb.containsKey(packageName)) {
            return "Package already installed: " + packageName;
        }

        try {
            // 简化：直接从预设位置复制（实际应从网络下载）
            Path source = binaryPath.resolve(packageName);
            if (!Files.exists(source)) {
                // 如果没有本地文件，尝试从内置资源解压
                return "Package file not found in Binary file. Please download " + packageName + " first.";
            }

            // 创建 bin 目录
            Path binDir = programPath.resolve("bin");
            Files.createDirectories(binDir);
            
            // 复制可执行文件
            Path dest = binDir.resolve(packageName);
            Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
            dest.toFile().setExecutable(true);
            
            // 记录到本地数据库
            localDb.put(packageName, pkg);
            saveLocalDatabase();
            
            // 更新 PATH
            ensurePath();
            
            return "Package installed: " + packageName + " (" + pkg.version + ")";
        } catch (IOException e) {
            return "Installation failed: " + e.getMessage();
        }
    }

    public static String remove(String packageName) {
        if (!localDb.containsKey(packageName)) {
            return "Package not installed: " + packageName;
        }
        try {
            Path binDir = programPath.resolve("bin");
            Path target = binDir.resolve(packageName);
            Files.deleteIfExists(target);
            localDb.remove(packageName);
            saveLocalDatabase();
            return "Package removed: " + packageName;
        } catch (IOException e) {
            return "Removal failed: " + e.getMessage();
        }
    }

    public static List<String> listInstalled() {
        return new ArrayList<>(localDb.keySet());
    }

    public static List<String> listAvailable() {
        return new ArrayList<>(remoteIndex.keySet());
    }

    public static List<String> search(String keyword) {
        return remoteIndex.keySet().stream()
                .filter(name -> name.contains(keyword) || 
                        remoteIndex.get(name).description.toLowerCase().contains(keyword.toLowerCase()))
                .collect(Collectors.toList());
    }

    public static String showInfo(String packageName) {
        PackageInfo pkg = remoteIndex.get(packageName);
        if (pkg == null) pkg = localDb.get(packageName);
        if (pkg == null) return "Package not found.";
        return String.format("Package: %s\nVersion: %s\nArchitecture: %s\nDescription: %s",
                pkg.packageName, pkg.version, pkg.architecture, pkg.description);
    }

    public static List<String> getPathEntries() {
        try {
            if (Files.exists(pathFile)) {
                return Files.readAllLines(pathFile);
            }
        } catch (IOException ignored) {}
        return new ArrayList<>();
    }

    public static Map<String, PackageInfo> getRemoteIndex() { return remoteIndex; }
    public static Map<String, PackageInfo> getLocalDb() { return localDb; }
}
