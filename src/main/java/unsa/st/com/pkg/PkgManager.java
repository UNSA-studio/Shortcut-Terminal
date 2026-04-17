package unsa.st.com.pkg;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
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
import java.util.zip.GZIPInputStream;

public class PkgManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String[] REPO_URLS = {
        "https://packages.termux.dev/apt/termux-main",
        "https://mirrors.tuna.tsinghua.edu.cn/termux/apt/termux-main"
    };
    private static String activeRepo = REPO_URLS[0];

    private static final String PROGRAM_DIR = "Program";
    private static final String BINARY_DIR = "Binary file";
    private static final String PATH_FILE = "PATH.txt";
    private static final String INSTALLED_DB = "var/lib/dpkg/status";

    private static Map<String, PackageInfo> remoteIndex = new HashMap<>();

    public static void init() {}

    private static Path getGameDir(boolean isClient) {
        if (isClient) {
            return Minecraft.getInstance().gameDirectory.toPath();
        } else {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            return server.getServerDirectory(); // 已返回 Path，无需 toPath()
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

            Path binDir = getProgramPath(isClient).resolve("bin");
            Files.createDirectories(binDir);
            Path dest = binDir.resolve(packageName);
            Files.copy(tmpFile, dest, StandardCopyOption.REPLACE_EXISTING);
            dest.toFile().setExecutable(true);

            localDb.put(packageName, pkg);
            saveLocalDatabase(isClient, localDb);
            ensurePath(isClient);
            return "Package installed: " + packageName + " (" + pkg.version + ")";
        } catch (Exception e) {
            return "Installation failed: " + e.getMessage();
        }
    }

    public static String remove(String packageName, boolean isClient) {
        Map<String, PackageInfo> localDb = loadLocalDatabase(isClient);
        if (!localDb.containsKey(packageName)) return "Package not installed: " + packageName;
        try {
            Path target = getProgramPath(isClient).resolve("bin").resolve(packageName);
            Files.deleteIfExists(target);
            localDb.remove(packageName);
            saveLocalDatabase(isClient, localDb);
            return "Package removed: " + packageName;
        } catch (IOException e) {
            return "Removal failed: " + e.getMessage();
        }
    }

    public static List<String> listInstalled(boolean isClient) {
        Map<String, PackageInfo> localDb = loadLocalDatabase(isClient);
        return new ArrayList<>(localDb.keySet());
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
