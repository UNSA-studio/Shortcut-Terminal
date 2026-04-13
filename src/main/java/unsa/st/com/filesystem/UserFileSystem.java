package unsa.st.com.filesystem;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import unsa.st.com.ShortcutTerminal;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class UserFileSystem {
    private static final String BASE_FOLDER = "Terminal File";
    private static Path basePath = null;

    private static Path getBasePath() {
        if (basePath == null) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                // server.getServerDirectory() 已经返回 Path，无需 toPath()
                basePath = server.getServerDirectory().resolve(BASE_FOLDER);
            } else {
                basePath = Paths.get(BASE_FOLDER);
            }
            try {
                Files.createDirectories(basePath);
            } catch (IOException e) {
                ShortcutTerminal.LOGGER.error("Failed to create Terminal File directory", e);
            }
        }
        return basePath;
    }

    public static Path getUserPath(UUID uuid) {
        return getBasePath().resolve(uuid.toString());
    }

    public static void createUserDirectory(UUID uuid) {
        try {
            Files.createDirectories(getUserPath(uuid));
        } catch (IOException e) {
            ShortcutTerminal.LOGGER.error("Failed to create user directory for " + uuid, e);
        }
    }

    public static boolean isPathValid(UUID uuid, String relativePath) {
        Path userPath = getUserPath(uuid);
        Path targetPath = userPath.resolve(relativePath).normalize();
        return targetPath.startsWith(userPath);
    }

    public static String normalizePath(String currentPath, String targetPath) {
        if (targetPath.isEmpty() || targetPath.equals(".")) return currentPath;
        if (targetPath.equals("..")) {
            int lastSlash = currentPath.lastIndexOf('/');
            return lastSlash > 0 ? currentPath.substring(0, lastSlash) : "";
        }
        return currentPath.isEmpty() ? targetPath : currentPath + "/" + targetPath;
    }

    public static List<String> listDirectory(UUID uuid, String relativePath) {
        Path dirPath = getUserPath(uuid).resolve(relativePath);
        if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) return null;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath)) {
            List<String> items = new ArrayList<>();
            for (Path entry : stream) {
                String name = entry.getFileName().toString();
                items.add(Files.isDirectory(entry) ? name + "/" : name);
            }
            return items;
        } catch (IOException e) {
            return null;
        }
    }

    public static boolean createDirectory(UUID uuid, String relativePath, String name) {
        if (!isPathValid(uuid, relativePath)) return false;
        Path dirPath = getUserPath(uuid).resolve(relativePath).resolve(name);
        try {
            Files.createDirectory(dirPath);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean createFile(UUID uuid, String relativePath, String name) {
        if (!isPathValid(uuid, relativePath)) return false;
        Path filePath = getUserPath(uuid).resolve(relativePath).resolve(name);
        try {
            Files.createFile(filePath);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean delete(UUID uuid, String relativePath, String name) {
        if (!isPathValid(uuid, relativePath)) return false;
        Path targetPath = getUserPath(uuid).resolve(relativePath).resolve(name);
        try {
            Files.delete(targetPath);
            return true;
        } catch (DirectoryNotEmptyException e) {
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    public static String readFile(UUID uuid, String relativePath, String name) {
        if (!isPathValid(uuid, relativePath)) return null;
        Path filePath = getUserPath(uuid).resolve(relativePath).resolve(name);
        if (!Files.exists(filePath) || Files.isDirectory(filePath)) return null;
        try {
            return Files.readString(filePath);
        } catch (IOException e) {
            return null;
        }
    }

    public static boolean directoryExists(UUID uuid, String currentPath, String targetPath) {
        if (targetPath.isEmpty() || targetPath.equals(".")) return true;
        String fullPath = normalizePath(currentPath, targetPath);
        Path dirPath = getUserPath(uuid).resolve(fullPath);
        return Files.exists(dirPath) && Files.isDirectory(dirPath);
    }
}
