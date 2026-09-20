package unsa.st.com.pkg;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.apache.commons.compress.archivers.ar.ArArchiveEntry;
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import unsa.st.com.ShortcutTerminal;

import java.io.*;
import java.lang.reflect.Constructor;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class PkgManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    // 镜像与发行版配置迁移至 PkgSources（支持运行时切换）
    private static Map<String, PackageInfo> remoteIndex = new HashMap<>();
    private static boolean indexLoaded = false;

    private static final String PROGRAM_DIR = "Program";
    private static final String BINARY_DIR = "Binary file";
    private static final String PATH_FILE = "PATH.txt";
    private static final String INSTALLED_DB = "var/lib/dpkg/status";
    private static final String DPKG_INFO_DIR = "var/lib/dpkg/info";
    private static final String INDEX_CACHE = "var/cache/pkg/index.json";

    // 动态查找 XZInputStream 类（处理 jarJar 重定位）
    private static Class<?> xzInputStreamClass;
    private static Constructor<?> xzInputStreamConstructor;
    static {
        String[] candidateClassNames = {
            "unsa.st.com.shortcutterminal.shadow.xz.XZInputStream",
            "org.tukaani.xz.XZInputStream",
            "unsa.st.com.shaded.xz.XZInputStream",
            "unsa.st.com.jarjar.org.tukaani.xz.XZInputStream"
        };
        for (String className : candidateClassNames) {
            try {
                xzInputStreamClass = Class.forName(className);
                xzInputStreamConstructor = xzInputStreamClass.getConstructor(InputStream.class);
                ShortcutTerminal.LOGGER.info("Found XZInputStream: {}", className);
                break;
            } catch (Exception ignored) {}
        }
        if (xzInputStreamConstructor == null) {
            ShortcutTerminal.LOGGER.error("XZInputStream not found in any expected package. XZ support disabled.");
        }
    }

    // ========== Android 真机检测 ==========
    private static boolean isRealAndroid() {
        // 检查特征属性：Android 特有的系统属性或环境变量
        String javaVendor = System.getProperty("java.vendor", "").toLowerCase();
        String javaVmName = System.getProperty("java.vm.name", "").toLowerCase();
        String osName = System.getProperty("os.name", "").toLowerCase();
        String androidRoot = System.getenv("ANDROID_ROOT");
        String androidData = System.getenv("ANDROID_DATA");

        return (javaVendor.contains("android") || javaVmName.contains("dalvik") || osName.contains("android") ||
                (androidRoot != null && !androidRoot.isEmpty()) || (androidData != null && !androidData.isEmpty()));
    }

    public static Path getGameDir(boolean isClient) {
        // FMLPaths.GAMEDIR：客户端 = .minecraft，专用服务器 = 服务器运行目录。
        // 这里绝不能触碰 net.minecraft.client.Minecraft，否则专用服务器会因 dist 校验直接崩溃。
        return net.neoforged.fml.loading.FMLPaths.GAMEDIR.get();
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
            Path programSbin = getProgramPath(isClient).resolve("sbin");
            Path programUsrBin = getProgramPath(isClient).resolve("usr/bin");
            Path programUsrSbin = getProgramPath(isClient).resolve("usr/sbin");
            Path binaryPath = getBinaryPath(isClient);

            Map<String, Path> commands = new LinkedHashMap<>();
            for (Path dir : new Path[]{programBin, programSbin, programUsrBin, programUsrSbin, binaryPath}) {
                if (Files.exists(dir)) {
                    Files.walk(dir, 1)
                        .filter(Files::isRegularFile)
                        .filter(Files::isExecutable)
                        .forEach(file -> {
                            String cmd = file.getFileName().toString();
                            commands.put(cmd, file.toAbsolutePath());
                        });
                }
            }

            List<String> lines = new ArrayList<>();
            for (Map.Entry<String, Path> entry : commands.entrySet()) {
                lines.add(entry.getKey() + " - " + entry.getValue().toString());
            }
            Files.write(pathFile, lines);
        } catch (IOException e) {
            ShortcutTerminal.LOGGER.error("Failed to update PATH", e);
        }
    }

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

    public static String updateIndex(boolean isClient, boolean force) {
        if (indexLoaded && !force) {
            return "Index already loaded. Use 'pkg update force' to refresh.";
        }
        if (!force && loadIndexCache(isClient)) {
            return "Loaded cached index (" + remoteIndex.size() + " packages). Use 'pkg update force' to refresh from network.";
        }

        String arch = System.getProperty("os.arch").toLowerCase().contains("arm") ? "arm64" : "amd64";
        PkgSources.Config cfg = PkgSources.load(isClient);
        List<String> errors = new ArrayList<>();
        for (PkgSources.Mirror mirror : PkgSources.fallbackOrder(cfg)) {
            String base = mirror.baseUrl + "/dists/" + cfg.release + "/main/binary-" + arch;
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
                        if (xzInputStreamConstructor != null) {
                            try {
                                InputStream xzStream = (InputStream) xzInputStreamConstructor.newInstance(is);
                                parsePackagesStream(xzStream);
                            } catch (Exception e) {
                                errors.add(urlStr + " -> " + e.getMessage());
                                continue;
                            }
                        } else {
                            errors.add(urlStr + " -> XZ support unavailable");
                            continue;
                        }
                    }
                    indexLoaded = true;
                    saveIndexCache(isClient);
                    return "Index updated from " + mirror.label + " (" + mirror.baseUrl + ", " + cfg.distro + "/" + cfg.release
                            + ", " + remoteIndex.size() + " packages).";
                } catch (Exception e) {
                    errors.add(urlStr + " -> " + e.getMessage());
                }
            }
        }

        ShortcutTerminal.LOGGER.error("All mirrors failed: {}", String.join("; ", errors));
        fallbackIndex();
        indexLoaded = true;
        return "Network fetch failed, using built-in fallback (1 package). Errors: " + String.join(", ", errors);
    }

    public static String updateIndex() {
        // Detect side: use client dir when running on the physical client
        boolean isClient = FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT;
        return updateIndex(isClient, false);
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
                    if (info.packageName != null) remoteIndex.put(info.packageName, info);
                    block.setLength(0);
                }
            } else {
                block.append(line).append("\n");
            }
        }
        if (block.length() > 0) {
            PackageInfo info = PackageInfo.parse(block.toString());
            if (info.packageName != null) remoteIndex.put(info.packageName, info);
        }
    }

    public static String install(String packageName, boolean isClient) {
        return install(packageName, isClient, false, false);
    }

    /**
     * 完整安装流程（apt 风格）：依赖解析 → 下载 → SHA256 校验 → 文件冲突检测
     * → 释放文件 → 写文件清单 → 注册数据库。
     * @param noDeps 跳过依赖自动安装
     * @param force  覆盖安装（允许文件冲突与重装）
     */
    public static String install(String packageName, boolean isClient, boolean noDeps, boolean force) {
        if (!indexLoaded) updateIndex(isClient, false);
        if (!remoteIndex.containsKey(packageName)) return "Package not found: " + packageName;
        Map<String, PackageInfo> localDb = loadLocalDatabase(isClient);
        if (localDb.containsKey(packageName) && !force) {
            return packageName + " is already installed. Use 'pkg upgrade " + packageName + "' to reinstall.";
        }
        PackageInfo main = remoteIndex.get(packageName);

        StringBuilder out = new StringBuilder();
        out.append("Reading package lists... Done\n");
        out.append("Building dependency tree... Done\n");

        // 依赖解析（不含主包）
        List<PackageInfo> deps = resolveDependencies(packageName, localDb, noDeps);
        List<PackageInfo> toInstall = new ArrayList<>(deps);
        toInstall.add(main);

        if (!deps.isEmpty()) {
            out.append("The following additional packages will be installed:\n");
            for (PackageInfo d : deps) out.append("  ").append(d.packageName);
            out.append('\n');
        }
        long needBytes = 0, installBytes = 0;
        for (PackageInfo p : toInstall) {
            needBytes += Math.max(p.size, 0);
            installBytes += Math.max(p.installedSize, 0);
        }
        out.append("The following NEW packages will be installed:\n  ").append(packageName).append('\n');
        out.append("0 upgraded, ").append(toInstall.size()).append(" newly installed, 0 to remove.\n");
        out.append("Need to get ").append(fmtSize(needBytes)).append(" of archives.\n");
        out.append("After this operation, ").append(fmtSize(installBytes)).append(" of additional disk space will be used.\n");

        // 逐包：下载 → 校验 → 解包安装
        int idx = 1;
        for (PackageInfo p : toInstall) {
            try {
                out.append("Get:").append(idx++).append(" ").append(p.packageName).append(" ").append(p.version)
                        .append(p.size > 0 ? " [" + fmtSize(p.size) + "]" : "").append('\n');
                Path deb = downloadPackage(p, isClient);
                try {
                    verifySha256(deb, p);
                    out.append(unpackAndRegister(deb, p, localDb, isClient, force));
                } finally {
                    try { Files.deleteIfExists(deb); } catch (IOException ignored) {}
                }
            } catch (Exception e) {
                ShortcutTerminal.LOGGER.error("Installation failed for " + p.packageName, e);
                return out.append("Installation failed: ").append(e.getMessage()).toString();
            }
        }

        ensurePath(isClient);

        if (isRealAndroid()) {
            out.append("\nAndroid: The system you are using is not supported.");
        }
        return out.toString();
    }

    private static void setExecutableRecursive(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        Files.walk(dir).filter(Files::isRegularFile).forEach(f -> f.toFile().setExecutable(true));
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

                    try {
                        if (name.endsWith(".gz")) {
                            try (TarArchiveInputStream tarIn = new TarArchiveInputStream(
                                    new GzipCompressorInputStream(Files.newInputStream(outFile)))) {
                                extractTar(tarIn, destDir.resolve("data"));
                            }
                        } else {
                            if (xzInputStreamConstructor == null) {
                                throw new IOException("XZ support unavailable, cannot extract data.tar.xz");
                            }
                            try (InputStream xzStream = (InputStream) xzInputStreamConstructor.newInstance(Files.newInputStream(outFile));
                                 TarArchiveInputStream tarIn = new TarArchiveInputStream(xzStream)) {
                                extractTar(tarIn, destDir.resolve("data"));
                            }
                        }
                    } catch (Exception e) {
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
                    Files.createDirectories(dest.getParent());
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
                Files.walk(path).sorted(Comparator.reverseOrder())
                        .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
            }
        } catch (IOException ignored) {}
    }

    public static String remove(String packageName, boolean isClient) {
        return remove(packageName, isClient, false);
    }

    /** 完整卸载：反向依赖检查 → 按文件清单删除文件 → 清理空目录 → 移除数据库记录。 */
    public static String remove(String packageName, boolean isClient, boolean force) {
        Map<String, PackageInfo> localDb = loadLocalDatabase(isClient);
        PackageInfo rec = localDb.get(packageName);
        if (rec == null) return "Package not installed: " + packageName;

        if (!force) {
            List<String> dependents = findDependents(packageName, localDb);
            if (!dependents.isEmpty()) {
                return "Removing " + packageName + " would break: " + String.join(", ", dependents)
                        + "\nUse 'pkg remove " + packageName + " --force' to remove anyway.";
            }
        }

        StringBuilder out = new StringBuilder();
        out.append("Reading package lists... Done\n");
        out.append("Building dependency tree... Done\n");
        out.append("The following packages will be REMOVED:\n  ").append(packageName).append('\n');
        out.append("0 upgraded, 0 newly installed, 1 to remove.\n");

        // 按文件清单删除（含防越界检查），统计释放空间
        List<String> files = readFileList(isClient, packageName);
        Path programDir = getProgramPath(isClient);
        long freed = 0;
        for (String rel : files) {
            try {
                Path f = programDir.resolve(rel).normalize();
                if (!f.startsWith(programDir)) continue;
                if (Files.isRegularFile(f)) {
                    freed += Files.size(f);
                    Files.deleteIfExists(f);
                }
            } catch (Exception ignored) {}
        }
        // 清理空目录（自底向上）
        try {
            List<Path> dirList = new ArrayList<>();
            for (String rel : files) {
                try {
                    Path f = programDir.resolve(rel).normalize();
                    Path d = f.getParent();
                    if (d != null && d.startsWith(programDir) && Files.isDirectory(d)) dirList.add(d);
                } catch (Exception ignored) {}
            }
            dirList.sort(Comparator.comparingInt((Path d) -> d.getNameCount()).reversed());
            for (Path d : dirList) {
                try { if (Files.isDirectory(d) && isEmptyDir(d)) Files.deleteIfExists(d); } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        out.append("After this operation, ").append(fmtSize(freed)).append(" disk space will be freed.\n");
        out.append("(Reading database ... ").append(localDb.size()).append(" packages installed.)\n");
        out.append("Removing ").append(packageName).append(" (").append(rec.version).append(") ...\n");

        localDb.remove(packageName);
        saveLocalDatabase(isClient, localDb);
        try { Files.deleteIfExists(getInfoDir(isClient).resolve(packageName + ".list")); } catch (IOException ignored) {}
        ensurePath(isClient);
        out.append("Done.");
        return out.toString();
    }

    private static boolean isEmptyDir(Path dir) {
        try (var s = Files.list(dir)) { return s.findAny().isEmpty(); } catch (IOException e) { return false; }
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
            if (Files.exists(pathFile)) return Files.readAllLines(pathFile);
        } catch (IOException ignored) {}
        return new ArrayList<>();
    }

    // ==================== 完整包管理器内部实现 ====================

    private static Path getInfoDir(boolean isClient) {
        return getProgramPath(isClient).resolve(DPKG_INFO_DIR);
    }

    /** 下载 .deb（多镜像回退）到临时文件。 */
    private static Path downloadPackage(PackageInfo pkg, boolean isClient) throws IOException {
        PkgSources.Config cfg = PkgSources.load(isClient);
        Exception lastError = null;
        for (PkgSources.Mirror mirror : PkgSources.fallbackOrder(cfg)) {
            String debUrl = mirror.baseUrl + "/" + pkg.filename;
            try {
                Path tmpDeb = Files.createTempFile("pkg_", ".deb");
                HttpURLConnection conn = (HttpURLConnection) new URL(debUrl).openConnection();
                conn.setRequestProperty("User-Agent", "ShortcutTerminal/1.0");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(30000);
                try (InputStream in = conn.getInputStream()) {
                    Files.copy(in, tmpDeb, StandardCopyOption.REPLACE_EXISTING);
                }
                return tmpDeb;
            } catch (Exception e) {
                lastError = e;
            }
        }
        throw new IOException("all mirrors failed: " + (lastError != null ? lastError.getMessage() : "unknown"));
    }

    /** SHA256 校验（索引提供摘要时）。 */
    private static void verifySha256(Path file, PackageInfo pkg) throws IOException {
        if (pkg.sha256 == null || pkg.sha256.isEmpty()) return;
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : md.digest()) hex.append(String.format("%02x", b));
            if (!hex.toString().equalsIgnoreCase(pkg.sha256.trim())) {
                throw new IOException("SHA256 mismatch for " + pkg.packageName + " (corrupted download, try again)");
            }
        } catch (java.security.NoSuchAlgorithmException ignored) {
            // JVM 不支持 SHA-256 时跳过校验
        }
    }

    /** 解包 → 冲突检测 → 释放文件 → 写文件清单 → 注册数据库（安装引擎核心）。 */
    private static String unpackAndRegister(Path deb, PackageInfo pkg, Map<String, PackageInfo> localDb,
                                            boolean isClient, boolean force) throws IOException {
        Path staging = Files.createTempDirectory("pkg_stage_");
        try {
            extractDeb(deb, staging);
            Path dataDir = staging.resolve("data");
            if (!Files.exists(dataDir)) dataDir = staging;

            List<String> fileList = listFiles(dataDir);

            // 文件冲突检测（非 --force）
            if (!force) {
                List<String> conflicts = checkConflicts(fileList, localDb, isClient);
                if (!conflicts.isEmpty()) {
                    StringBuilder c = new StringBuilder("file conflicts with existing packages:\n");
                    int shown = 0;
                    for (String s : conflicts) {
                        c.append("  ").append(s).append('\n');
                        if (++shown >= 5) break;
                    }
                    if (conflicts.size() > 5) c.append("  ... and ").append(conflicts.size() - 5).append(" more\n");
                    c.append("Use --force to overwrite.");
                    throw new IOException(c.toString());
                }
            }

            Path targetDir = getProgramPath(isClient);
            copyDirectory(dataDir, targetDir);
            try { setExecutableRecursive(targetDir.resolve("bin")); } catch (Exception ignored) {}
            try { setExecutableRecursive(targetDir.resolve("sbin")); } catch (Exception ignored) {}
            try { setExecutableRecursive(targetDir.resolve("usr/bin")); } catch (Exception ignored) {}
            try { setExecutableRecursive(targetDir.resolve("usr/sbin")); } catch (Exception ignored) {}

            // 写文件清单（真实 dpkg 路径：var/lib/dpkg/info/<pkg>.list）
            writeFileList(isClient, pkg.packageName, fileList);

            // 注册数据库
            localDb.put(pkg.packageName, pkg);
            saveLocalDatabase(isClient, localDb);

            String fileName = pkg.filename == null ? (pkg.packageName + ".deb")
                    : pkg.filename.substring(pkg.filename.lastIndexOf('/') + 1);
            return "Selecting previously unselected package " + pkg.packageName + ".\n"
                 + "(Reading database ... " + (localDb.size() - 1) + " packages installed.)\n"
                 + "Preparing to unpack .../" + fileName + " ...\n"
                 + "Unpacking " + pkg.packageName + " (" + pkg.version + ") ...\n"
                 + "Setting up " + pkg.packageName + " (" + pkg.version + ") ...\n";
        } finally {
            deleteRecursive(staging);
        }
    }

    /** 依赖解析：收集未安装的依赖（递归，带环保护），返回安装顺序（依赖在前）。 */
    private static List<PackageInfo> resolveDependencies(String name, Map<String, PackageInfo> localDb, boolean noDeps) {
        List<PackageInfo> out = new ArrayList<>();
        if (noDeps) return out;
        Set<String> visiting = new HashSet<>();
        collectDeps(name, localDb, visiting, out, 0);
        return out;
    }

    private static void collectDeps(String name, Map<String, PackageInfo> localDb, Set<String> visiting,
                                    List<PackageInfo> out, int depth) {
        if (depth > 12 || visiting.contains(name)) return;
        PackageInfo pkg = remoteIndex.get(name);
        if (pkg == null) return;
        visiting.add(name);
        for (String dep : pkg.depends) {
            String dn = depName(dep);
            if (dn.isEmpty() || localDb.containsKey(dn) || remoteIndex.get(dn) == null) continue;
            collectDeps(dn, localDb, visiting, out, depth + 1);
        }
        // 依赖先装：自身放最后（若尚未收集且未安装）
        if (!localDb.containsKey(name)) {
            boolean exists = false;
            for (PackageInfo p : out) if (p.packageName.equals(name)) { exists = true; break; }
            if (!exists) out.add(pkg);
        }
        visiting.remove(name);
    }

    /** "libc6 (>= 2.34) | libc6-udeb" → "libc6" */
    private static String depName(String dep) {
        if (dep == null) return "";
        String s = dep.split("\\|")[0].trim();
        int sp = s.indexOf(' ');
        if (sp > 0) s = s.substring(0, sp);
        int colon = s.indexOf(':');
        if (colon > 0) s = s.substring(0, colon);
        return s.trim();
    }

    /** 列出目录下全部文件的相对路径（"/" 分隔）。 */
    private static List<String> listFiles(Path dir) throws IOException {
        List<String> out = new ArrayList<>();
        Files.walk(dir).filter(Files::isRegularFile).forEach(f ->
                out.add(dir.relativize(f).toString().replace('\\', '/')));
        return out;
    }

    /** 文件冲突检测：与已装包的文件清单求交。 */
    private static List<String> checkConflicts(List<String> newFiles, Map<String, PackageInfo> localDb, boolean isClient) {
        List<String> conflicts = new ArrayList<>();
        Path infoDir = getInfoDir(isClient);
        for (String p : localDb.keySet()) {
            Path listFile = infoDir.resolve(p + ".list");
            if (!Files.exists(listFile)) continue;
            try {
                Set<String> owned = new HashSet<>(Files.readAllLines(listFile));
                for (String f : newFiles) if (owned.contains(f)) conflicts.add(f + " (owned by " + p + ")");
            } catch (IOException ignored) {}
        }
        return conflicts;
    }

    private static void writeFileList(boolean isClient, String pkg, List<String> files) {
        try {
            Path f = getInfoDir(isClient).resolve(pkg + ".list");
            Files.createDirectories(f.getParent());
            Files.write(f, files);
        } catch (IOException ignored) {}
    }

    private static List<String> readFileList(boolean isClient, String pkg) {
        try {
            Path f = getInfoDir(isClient).resolve(pkg + ".list");
            if (Files.exists(f)) return Files.readAllLines(f);
        } catch (IOException ignored) {}
        return new ArrayList<>();
    }

    /** 反向依赖：哪些已装包依赖 name。 */
    private static List<String> findDependents(String name, Map<String, PackageInfo> localDb) {
        List<String> out = new ArrayList<>();
        for (PackageInfo p : localDb.values()) {
            if (p.packageName != null && p.packageName.equals(name)) continue;
            for (String dep : p.depends) {
                if (depName(dep).equals(name)) { out.add(p.packageName); break; }
            }
        }
        return out;
    }

    // ==================== 升级 ====================

    /** 升级/重装单个包。 */
    public static String upgrade(String packageName, boolean isClient) {
        if (!indexLoaded) updateIndex(isClient, false);
        Map<String, PackageInfo> localDb = loadLocalDatabase(isClient);
        if (!localDb.containsKey(packageName)) {
            return packageName + " is not installed. Use 'pkg install " + packageName + "'.";
        }
        PackageInfo idx = remoteIndex.get(packageName);
        if (idx == null) return "Package not found in the index: " + packageName;
        PackageInfo inst = localDb.get(packageName);
        if (idx.version != null && idx.version.equals(inst.version)) {
            return packageName + " is already the newest version (" + inst.version + ").";
        }
        return "Calculating upgrade... Done\n" + install(packageName, isClient, false, true);
    }

    /** 升级全部已安装包。 */
    public static String upgradeAll(boolean isClient) {
        if (!indexLoaded) updateIndex(isClient, false);
        Map<String, PackageInfo> localDb = loadLocalDatabase(isClient);
        if (localDb.isEmpty()) return "No packages installed.";
        StringBuilder out = new StringBuilder();
        int upgraded = 0;
        for (String name : new ArrayList<>(localDb.keySet())) {
            PackageInfo idx = remoteIndex.get(name);
            if (idx == null) continue;
            PackageInfo inst = localDb.get(name);
            if (idx.version != null && !idx.version.equals(inst.version)) {
                out.append("Upgrading ").append(name).append(" ").append(inst.version)
                   .append(" -> ").append(idx.version).append('\n');
                out.append(install(name, isClient, false, true));
                upgraded++;
            }
        }
        if (upgraded == 0) return "0 packages upgraded, all up to date.";
        return out.toString();
    }

    private static String fmtSize(long bytes) {
        if (bytes < 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1048576) return String.format("%.1f kB", bytes / 1024.0);
        if (bytes < 1073741824) return String.format("%.1f MB", bytes / 1048576.0);
        return String.format("%.1f GB", bytes / 1073741824.0);
    }

    public static String getHelp() {
        return "pkg update [force] - refresh package index\n" +
               "pkg search <keyword> - search packages\n" +
               "pkg install <pkg> [--no-deps] [--force] - install (auto-resolves dependencies)\n" +
               "pkg remove <pkg> [--force] - remove (deletes files via file-list)\n" +
               "pkg upgrade <pkg|--all> - upgrade package(s)\n" +
               "pkg list - list installed\n" +
               "pkg show <pkg> - show package details\n" +
               "pkg source - show current source config\n" +
               "pkg source list - list available mirrors\n" +
               "pkg source set <id> - switch mirror\n" +
               "pkg source distro <debian|ubuntu> - switch distribution\n" +
               "pkg source release <name> - switch release (e.g. bookworm)";
    }

    // ==================== 源管理（pkg source ...） ====================

    /** 处理 `pkg source` 系列子命令，返回可直接显示的文本。 */
    public static String sourceCommand(String[] args, boolean isClient) {
        // args: ["source", <sub>, ...]
        PkgSources.Config cfg = PkgSources.load(isClient);
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "show";
        switch (sub) {
            case "list": {
                StringBuilder sb = new StringBuilder("Mirrors for " + cfg.distro + ":");
                for (PkgSources.Mirror m : PkgSources.mirrorsFor(cfg.distro)) {
                    sb.append("\n  ").append(m.id.equals(cfg.mirror) ? "* " : "  ")
                      .append(m.id).append("  -  ").append(m.label)
                      .append("  (").append(m.baseUrl).append(")");
                }
                sb.append("\nReleases: ").append(String.join(", ", PkgSources.releasesFor(cfg.distro)));
                sb.append("\nUse: pkg source set <id> / pkg source distro <name> / pkg source release <name>");
                return sb.toString();
            }
            case "set": {
                if (args.length < 3) return "Usage: pkg source set <id> (see 'pkg source list')";
                String id = args[2].toLowerCase(Locale.ROOT);
                for (PkgSources.Mirror m : PkgSources.mirrorsFor(cfg.distro)) {
                    if (m.id.equals(id)) {
                        cfg.mirror = id;
                        PkgSources.save(isClient, cfg);
                        invalidateIndex();
                        return "Mirror switched to " + m.label + " (" + m.baseUrl + "). Run 'pkg update force' to refresh.";
                    }
                }
                return "Unknown mirror id: " + id + " (see 'pkg source list')";
            }
            case "distro": {
                if (args.length < 3) return "Usage: pkg source distro <debian|ubuntu>";
                String d = args[2].toLowerCase(Locale.ROOT);
                if (!PkgSources.isValidDistro(d)) return "Unknown distro: " + d + " (supported: debian, ubuntu)";
                cfg.distro = d;
                cfg.release = PkgSources.releasesFor(d)[0];
                // 该发行版默认首个镜像（tuna）
                cfg.mirror = PkgSources.mirrorsFor(d).get(0).id;
                PkgSources.save(isClient, cfg);
                invalidateIndex();
                return "Distribution switched to " + d + "/" + cfg.release + ". Run 'pkg update force' to refresh.";
            }
            case "release": {
                if (args.length < 3) return "Usage: pkg source release <name> (available: "
                        + String.join(", ", PkgSources.releasesFor(cfg.distro)) + ")";
                String r = args[2].toLowerCase(Locale.ROOT);
                boolean valid = false;
                for (String cand : PkgSources.releasesFor(cfg.distro)) if (cand.equals(r)) valid = true;
                if (!valid) return "Unknown release: " + r + " (available: "
                        + String.join(", ", PkgSources.releasesFor(cfg.distro)) + ")";
                cfg.release = r;
                PkgSources.save(isClient, cfg);
                invalidateIndex();
                return "Release switched to " + cfg.distro + "/" + r + ". Run 'pkg update force' to refresh.";
            }
            case "show":
            default: {
                PkgSources.Mirror cur = PkgSources.resolve(cfg);
                return "Distribution: " + cfg.distro + "\nRelease: " + cfg.release
                        + "\nMirror: " + cur.id + " - " + cur.label + " (" + cur.baseUrl + ")\n"
                        + "Commands: pkg source list / set <id> / distro <name> / release <name>";
            }
        }
    }

    /** 切换源后使索引失效，下次 update 重新拉取。 */
    private static void invalidateIndex() {
        indexLoaded = false;
    }

    public static List<String> listAvailable() {
        if (!indexLoaded) updateIndex(false, false);
        return new ArrayList<>(remoteIndex.keySet());
    }
}
