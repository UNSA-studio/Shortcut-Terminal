package unsa.st.com.pkg;

import net.minecraft.client.Minecraft;
import unsa.st.com.ShortcutTerminal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 简易包管理器
 * 程序安装目录: Program/
 * 环境变量文件: PATH.txt (根目录)
 * 二进制插件目录: Binary file/
 */
public class PackageManager {
    private static final String PROGRAM_DIR = "Program";
    private static final String BINARY_DIR = "Binary file";
    private static final String PATH_FILE = "PATH.txt";
    private static final String PATH_BACKUP_FILE = BINARY_DIR + "/PATH.txt";

    private static Path gameDir = null;
    private static Path programPath = null;
    private static Path binaryPath = null;
    private static Path pathFile = null;
    private static Path pathBackupFile = null;

    private static Set<String> pathEntries = new LinkedHashSet<>();

    public static void init() {
        Minecraft mc = Minecraft.getInstance();
        gameDir = mc.gameDirectory.toPath();
        programPath = gameDir.resolve(PROGRAM_DIR);
        binaryPath = gameDir.resolve(BINARY_DIR);
        pathFile = gameDir.resolve(PATH_FILE);
        pathBackupFile = binaryPath.resolve(PATH_FILE);

        try {
            Files.createDirectories(programPath);
            Files.createDirectories(binaryPath);
        } catch (IOException e) {
            ShortcutTerminal.LOGGER.error("Failed to create package directories", e);
        }

        loadPath();
    }

    private static void loadPath() {
        pathEntries.clear();
        if (Files.exists(pathFile)) {
            try {
                List<String> lines = Files.readAllLines(pathFile);
                for (String line : lines) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        pathEntries.add(line);
                    }
                }
            } catch (IOException e) {
                ShortcutTerminal.LOGGER.error("Failed to load PATH.txt", e);
            }
        }
        // 默认包含 Program 和 Binary file
        pathEntries.add(programPath.toAbsolutePath().toString());
        pathEntries.add(binaryPath.toAbsolutePath().toString());
        savePath();
    }

    private static void savePath() {
        try {
            List<String> lines = new ArrayList<>(pathEntries);
            Files.write(pathFile, lines);
            // 备份到 Binary file
            Files.createDirectories(binaryPath);
            Files.write(pathBackupFile, lines);
        } catch (IOException e) {
            ShortcutTerminal.LOGGER.error("Failed to save PATH.txt", e);
        }
    }

    public static List<String> getPathEntries() {
        return new ArrayList<>(pathEntries);
    }

    public static boolean installPackage(String packageName) {
        // 从 Binary file 中查找同名文件（忽略扩展名）
        Path source = null;
        try (var stream = Files.newDirectoryStream(binaryPath)) {
            for (Path entry : stream) {
                String name = entry.getFileName().toString();
                if (name.equals(packageName) || name.startsWith(packageName + ".")) {
                    source = entry;
                    break;
                }
            }
        } catch (IOException e) {
            return false;
        }
        if (source == null) return false;

        Path dest = programPath.resolve(packageName);
        try {
            Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
            // 确保可执行权限（在支持的系统上）
            dest.toFile().setExecutable(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean uninstallPackage(String packageName) {
        Path target = programPath.resolve(packageName);
        try {
            return Files.deleteIfExists(target);
        } catch (IOException e) {
            return false;
        }
    }

    public static List<String> listInstalledPackages() {
        try (var stream = Files.newDirectoryStream(programPath)) {
            return Files.list(programPath)
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    public static List<String> listAvailablePackages() {
        try (var stream = Files.newDirectoryStream(binaryPath)) {
            return Files.list(binaryPath)
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    public static boolean isInstalled(String packageName) {
        return Files.exists(programPath.resolve(packageName));
    }

    public static Path findExecutable(String command) {
        // 在 PATH 中搜索可执行文件
        for (String dir : pathEntries) {
            Path exec = Paths.get(dir, command);
            if (Files.isExecutable(exec)) {
                return exec;
            }
        }
        return null;
    }
}
