package unsa.st.com.winget;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import unsa.st.com.ShortcutTerminal;
import unsa.st.com.pkg.PkgManager;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 内置 winget —— STOS 软件包管理器。
 *
 * <p>行为与真实 winget 一致：查询源清单 → 下载安装包 → 运行安装程序（内置安装引擎）
 * → 软件安装到 {@code Program/WindowsApps/<软件目录>/} → 写入已安装注册表；
 * 卸载时按注册表清理。安装 ≠ 解压：由"安装引擎"按安装清单执行完整安装流程。</p>
 *
 * <p>安装包（.stpkg）内部结构：{@code install.json}（安装清单）+ {@code files/}（程序文件与资源）。</p>
 */
public class WingetManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String USER_AGENT = "ShortcutTerminal/1.0";

    // ==================== 数据模型 ====================

    /** 软件源（镜像）。 */
    public static class Source {
        public String id;
        public String label;
        public String baseUrl;
        public Source() {}
        public Source(String id, String label, String baseUrl) {
            this.id = id; this.label = label; this.baseUrl = baseUrl;
        }
    }

    /** 源清单中的软件条目。 */
    public static class AppEntry {
        public String id;
        public String name;
        public String version;
        public String description;
        public String installer;   // 安装包文件名（相对源路径）
        public String sha256;      // 可选校验
        public long size;
    }

    /** 已安装软件记录。 */
    public static class InstalledApp {
        public String id;
        public String name;
        public String version;
        public String path;        // 安装位置（相对游戏目录，如 Program/WindowsApps/StosFetch）
        public long installedAt;
    }

    private static class SourceConfig {
        List<Source> sources = new ArrayList<>();
        String active = "jsdelivr";
    }

    // ==================== 内置源（国内优先） ====================

    private static final List<Source> DEFAULT_SOURCES = List.of(
            new Source("jsdelivr", "jsDelivr 国内CDN", "https://cdn.jsdelivr.net/gh/UNSA-studio/Shortcut-Terminal@main/winget"),
            new Source("ghproxy", "GHProxy 国内加速", "https://ghproxy.net/https://raw.githubusercontent.com/UNSA-studio/Shortcut-Terminal/main/winget"),
            new Source("gh-proxy", "GH-Proxy 国内加速", "https://gh-proxy.com/https://raw.githubusercontent.com/UNSA-studio/Shortcut-Terminal/main/winget"),
            new Source("github", "GitHub 直连", "https://raw.githubusercontent.com/UNSA-studio/Shortcut-Terminal/main/winget")
    );

    // ==================== 路径 ====================

    /** Linux 应用目录（Program）下的 WindowsApps 文件夹。 */
    private static Path getWindowsAppsDir(boolean isClient) {
        return PkgManager.getGameDir(isClient).resolve("Program").resolve("WindowsApps");
    }

    private static Path getRegistryFile(boolean isClient) {
        return getWindowsAppsDir(isClient).resolve(".registry.json");
    }

    private static Path getSourceConfigFile(boolean isClient) {
        return getWindowsAppsDir(isClient).resolve(".sources.json");
    }

    private static Path getIndexCacheFile(boolean isClient) {
        return getWindowsAppsDir(isClient).resolve(".index-cache.json");
    }

    // ==================== 命令入口 ====================

    /**
     * 命令入口：按运行环境自动分流（与 pkg 的检测方案同款思路）——
     * ・安卓（AARCH 沙箱）→ 警告 + 内置模拟安装引擎
     * ・桌面系统且有系统 winget（Windows）→ 直通调用真实 winget.exe
     * ・其余桌面平台（无 winget）→ 提示 + 内置模拟安装引擎
     */
    public static String dispatch(String[] args, boolean isClient, java.util.function.Consumer<String> asyncOutput) {
        if (isRealAndroid()) {
            String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
            boolean installish = sub.equals("install") || sub.equals("uninstall") || sub.equals("remove")
                    || sub.equals("upgrade") || sub.equals("add");
            if (installish) {
                return "[!] Android environment detected: real system installers cannot be executed here.\n"
                     + "    Using the built-in installer (files are placed under Program/WindowsApps).\n"
                     + builtinDispatch(args, isClient);
            }
            return builtinDispatch(args, isClient);
        }
        if (hasHostWinget()) {
            return hostDispatch(args, asyncOutput);
        }
        String os = System.getProperty("os.name", "unknown");
        return "[!] No system winget found on this platform (" + os + ") - using the built-in installer.\n"
             + builtinDispatch(args, isClient);
    }

    /** 内置安装引擎的命令分发（安卓及无宿主 winget 的平台使用）。 */
    private static String builtinDispatch(String[] args, boolean isClient) {
        if (args.length == 0) return getHelp();
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "install":   return install(args, isClient);
            case "uninstall":
            case "remove":    return args.length > 1 ? uninstall(args[1], isClient) : "Usage: winget uninstall <id>";
            case "list":      return listInstalled(isClient);
            case "search":    return args.length > 1 ? search(args[1], isClient) : "Usage: winget search <keyword>";
            case "show":      return args.length > 1 ? show(args[1], isClient) : "Usage: winget show <id>";
            case "source":    return sourceCommand(args, isClient);
            case "update":    return refreshIndex(isClient, true);
            case "help":      return getHelp();
            default:          return "Unknown winget command: " + args[0] + "\n" + getHelp();
        }
    }

    // ==================== 系统 winget 直通（桌面平台） ====================

    /** 安卓环境检测（与 PkgManager 同款特征组合，避免误判 Linux 桌面）。 */
    private static boolean isRealAndroid() {
        String javaVendor = System.getProperty("java.vendor", "").toLowerCase(Locale.ROOT);
        String javaVmName = System.getProperty("java.vm.name", "").toLowerCase(Locale.ROOT);
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String androidRoot = System.getenv("ANDROID_ROOT");
        String androidData = System.getenv("ANDROID_DATA");
        return javaVendor.contains("android") || javaVmName.contains("dalvik") || osName.contains("android")
                || (androidRoot != null && !androidRoot.isEmpty())
                || (androidData != null && !androidData.isEmpty());
    }

    /** 系统 winget 探测结果缓存（null=未检测）。 */
    private static volatile Boolean hostWingetAvailable = null;

    /** 当前平台的 winget 命令名。 */
    private static List<String> hostWingetCmd() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<String> cmd = new ArrayList<>();
        cmd.add(os.contains("win") ? "winget.exe" : "winget");
        return cmd;
    }

    /** 探测系统是否装有真实 winget。 */
    private static synchronized boolean hasHostWinget() {
        if (hostWingetAvailable != null) return hostWingetAvailable;
        boolean found = false;
        try {
            List<String> probe = hostWingetCmd();
            probe.add("--version");
            Process p = new ProcessBuilder(probe).redirectErrorStream(true).start();
            try { p.getOutputStream().close(); } catch (IOException ignored) {}
            boolean done = p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            if (!done) {
                p.destroyForcibly();
            } else {
                found = p.exitValue() == 0;
            }
        } catch (Exception ignored) {}
        hostWingetAvailable = found;
        return found;
    }

    /** 直通执行系统 winget：快命令同步返回；安装类命令后台执行、完成后经回调送回输出。 */
    private static String hostDispatch(String[] args, java.util.function.Consumer<String> asyncOutput) {
        if (args.length == 0) {
            return "Microsoft winget (system passthrough)\n" +
                   "winget search <kw> | install <id> [--location <dir>] | list | uninstall <id> | upgrade [--all]";
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        List<String> cmd = hostWingetCmd();
        for (String a : args) cmd.add(a);

        // 安装类命令补齐无交互参数（自动化，无需点击）：
        //   --accept-package-agreements / --accept-source-agreements / --disable-interactivity
        boolean mutating = sub.equals("install") || sub.equals("upgrade")
                || sub.equals("uninstall") || sub.equals("add");
        if (mutating) {
            boolean hasAccept = false, hasSrc = false, hasNoInteract = false;
            for (String a : args) {
                String l = a.toLowerCase(Locale.ROOT);
                if (l.startsWith("--accept-package-agreements")) hasAccept = true;
                if (l.startsWith("--accept-source-agreements")) hasSrc = true;
                if (l.startsWith("--disable-interactivity")) hasNoInteract = true;
            }
            if (!hasAccept) cmd.add("--accept-package-agreements");
            if (!hasSrc) cmd.add("--accept-source-agreements");
            if (!hasNoInteract) cmd.add("--disable-interactivity");
        }

        boolean fast = sub.equals("search") || sub.equals("list") || sub.equals("show")
                || sub.equals("source") || sub.equals("--version") || sub.equals("-v")
                || sub.equals("--info") || sub.equals("help") || sub.equals("configure")
                || sub.equals("features");
        if (fast) {
            try {
                return "[system winget]\n" + runHostProcess(new ArrayList<>(cmd), 30);
            } catch (Exception e) {
                return "winget failed: " + e.getMessage();
            }
        }

        final List<String> bgCmd = new ArrayList<>(cmd);
        if (asyncOutput == null) {
            try {
                return runHostProcess(bgCmd, 600);
            } catch (Exception e) {
                return "winget failed: " + e.getMessage();
            }
        }
        Thread worker = new Thread(() -> {
            String out;
            try {
                out = runHostProcess(bgCmd, 600);
            } catch (Exception e) {
                out = "winget failed: " + e.getMessage();
            }
            asyncOutput.accept(out);
        }, "ShortcutTerminal-HostWinget");
        worker.setDaemon(true);
        worker.start();
        return "winget " + args[0] + " is running on the system in the background...\n"
             + "This may take a while; output will appear here when finished.";
    }

    /** 运行宿主进程并收集输出；timeoutSec<=0 表示不限时。 */
    private static String runHostProcess(List<String> cmd, int timeoutSec) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        try { proc.getOutputStream().close(); } catch (IOException ignored) {}

        java.nio.charset.Charset cs = resolveHostCharset();

        final List<String> lines = new ArrayList<>();
        Thread reader = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream(), cs))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (isProgressNoise(line)) continue; // 折叠进度条噪声帧
                    synchronized (lines) {
                        if (lines.size() < 500) lines.add(line);
                    }
                }
            } catch (IOException ignored) {}
        }, "ShortcutTerminal-HostWinget-Reader");
        reader.setDaemon(true);
        reader.start();

        boolean finished;
        if (timeoutSec <= 0) {
            proc.waitFor();
            finished = true;
        } else {
            finished = proc.waitFor(timeoutSec, java.util.concurrent.TimeUnit.SECONDS);
        }
        StringBuilder sb = new StringBuilder();
        if (!finished) {
            proc.destroyForcibly();
            proc.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            sb.append("Error: winget timed out after ").append(timeoutSec).append("s and was killed.\n");
        }
        reader.join(3000);
        int code = -1;
        try { code = proc.exitValue(); } catch (IllegalThreadStateException ignored) {}
        sb.append("[winget exit code: ").append(code).append(']');
        synchronized (lines) {
            for (String l : lines) sb.append('\n').append(l);
        }
        return sb.toString();
    }

    /** 宿主机输出字符集：-Dst.winget.charset 覆盖，否则系统默认。 */
    private static java.nio.charset.Charset resolveHostCharset() {
        String override = System.getProperty("st.winget.charset");
        if (override != null && !override.isEmpty()) {
            try { return java.nio.charset.Charset.forName(override); } catch (Exception ignored) {}
        }
        return java.nio.charset.Charset.defaultCharset();
    }

    /** 进度条噪声帧过滤（仅由块字符/百分比/数字/空白组成的行为噪声）。 */
    private static boolean isProgressNoise(String line) {
        if (line == null || line.isEmpty()) return false;
        boolean hasProgressChar = false;
        for (char c : line.toCharArray()) {
            if (Character.isWhitespace(c)) continue;
            if ("-\\|/─━═0123456789%.: ".indexOf(c) >= 0 || (c >= 0x2580 && c <= 0x259F)) {
                hasProgressChar = true;
                continue;
            }
            return false;
        }
        return hasProgressChar;
    }

    public static String getHelp() {
        return "winget install <id> [options]\n" +
               "    --location <dir> / -l   custom install directory (inside game dir)\n" +
               "    --scope user|machine    install scope (user needs no elevation)\n" +
               "    --force / -f            reinstall even if present\n" +
               "    --silent / --disable-interactivity / --accept-*-agreements\n" +
               "                            unattended install (engine default; no clicks)\n" +
               "winget uninstall <id>  - uninstall an application\n" +
               "winget list            - list installed applications\n" +
               "winget search <kw>     - search the source catalog\n" +
               "winget show <id>       - show application details\n" +
               "winget update          - refresh the source catalog\n" +
               "winget source list     - list mirrors\n" +
               "winget source set <id> - switch mirror\n" +
               "NOTE: on desktop systems with Microsoft winget installed, these commands\n" +
               "      are passed through to the system winget automatically.";
    }

    // ==================== 源管理 ====================

    private static SourceConfig loadSourceConfig(boolean isClient) {
        Path f = getSourceConfigFile(isClient);
        SourceConfig cfg = null;
        if (Files.exists(f)) {
            try {
                cfg = GSON.fromJson(Files.readString(f), SourceConfig.class);
            } catch (Exception ignored) {}
        }
        if (cfg == null) cfg = new SourceConfig();
        if (cfg.sources == null || cfg.sources.isEmpty()) cfg.sources = new ArrayList<>(DEFAULT_SOURCES);
        if (cfg.active == null || cfg.active.isEmpty()) cfg.active = "jsdelivr";
        return cfg;
    }

    private static void saveSourceConfig(boolean isClient, SourceConfig cfg) {
        try {
            Path f = getSourceConfigFile(isClient);
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(cfg));
        } catch (IOException ignored) {}
    }

    private static Source activeSource(SourceConfig cfg) {
        for (Source s : cfg.sources) if (s.id.equals(cfg.active)) return s;
        return cfg.sources.get(0);
    }

    private static String sourceCommand(String[] args, boolean isClient) {
        SourceConfig cfg = loadSourceConfig(isClient);
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "list";
        switch (sub) {
            case "list": {
                StringBuilder sb = new StringBuilder("Software sources:");
                for (Source s : cfg.sources) {
                    sb.append("\n  ").append(s.id.equals(cfg.active) ? "* " : "  ")
                      .append(s.id).append("  -  ").append(s.label)
                      .append("  (").append(s.baseUrl).append(")");
                }
                return sb.toString();
            }
            case "set": {
                if (args.length < 3) return "Usage: winget source set <id>";
                String id = args[2].toLowerCase(Locale.ROOT);
                for (Source s : cfg.sources) {
                    if (s.id.equals(id)) {
                        cfg.active = id;
                        saveSourceConfig(isClient, cfg);
                        return "Source switched to " + s.label + ".";
                    }
                }
                return "Unknown source id: " + id + " (see 'winget source list')";
            }
            default: {
                Source cur = activeSource(cfg);
                return "Active source: " + cur.id + " - " + cur.label + "\n" + cur.baseUrl
                        + "\nCommands: winget source list / set <id>";
            }
        }
    }

    // ==================== 索引（软件清单） ====================

    private static List<AppEntry> fetchCatalog(boolean isClient, boolean force) {
        Path cache = getIndexCacheFile(isClient);
        if (!force && Files.exists(cache)) {
            try {
                List<AppEntry> cached = GSON.fromJson(Files.readString(cache),
                        new TypeToken<List<AppEntry>>(){}.getType());
                if (cached != null && !cached.isEmpty()) return cached;
            } catch (Exception ignored) {}
        }
        SourceConfig cfg = loadSourceConfig(isClient);
        // 当前源优先，其余源兜底
        List<Source> order = new ArrayList<>(cfg.sources);
        Source cur = activeSource(cfg);
        order.remove(cur);
        order.add(0, cur);
        Exception last = null;
        for (Source s : order) {
            try {
                String body = httpGet(s.baseUrl + "/index.json");
                List<AppEntry> list = GSON.fromJson(body, new TypeToken<List<AppEntry>>(){}.getType());
                if (list != null && !list.isEmpty()) {
                    try {
                        Files.createDirectories(cache.getParent());
                        Files.writeString(cache, GSON.toJson(list));
                    } catch (IOException ignored) {}
                    return list;
                }
            } catch (Exception e) {
                last = e;
            }
        }
        ShortcutTerminal.LOGGER.warn("winget: all sources failed: {}", String.valueOf(last));
        return new ArrayList<>();
    }

    private static String refreshIndex(boolean isClient, boolean force) {
        List<AppEntry> list = fetchCatalog(isClient, force);
        if (list.isEmpty()) return "Failed to fetch the catalog from all sources. Check your network and try again.";
        return "Catalog updated: " + list.size() + " application(s) available.";
    }

    // ==================== 安装 ====================

    /**
     * 安装命令，支持 winget 风格参数：
     *   --location/-l <dir>   指定安装目录（须位于游戏目录内）
     *   --scope user|machine  安装范围（machine 模拟提权自动通过）
     *   --force/-f            已安装时强制重装
     *   --silent/-h, --disable-interactivity, --accept-*-agreements
     *                         静默自动化安装（引擎默认行为：全程无点击）
     */
    private static String install(String[] args, boolean isClient) {
        if (args.length < 2) return "Usage: winget install <id> [--location <dir>] [--scope user|machine] [--force]";
        String appId = args[1];
        String location = null;
        String scope = "user";
        boolean force = false;
        for (int i = 2; i < args.length; i++) {
            String a = args[i].toLowerCase(Locale.ROOT);
            switch (a) {
                case "--location": case "-l":
                    if (i + 1 < args.length) location = args[++i];
                    break;
                case "--scope":
                    if (i + 1 < args.length) scope = args[++i].toLowerCase(Locale.ROOT);
                    break;
                case "--force": case "-f":
                    force = true;
                    break;
                case "--silent": case "-h":
                case "--disable-interactivity":
                case "--accept-package-agreements":
                case "--accept-source-agreements":
                    break; // 静默/无交互/自动接受协议：安装引擎默认即此行为
                default:
                    break;
            }
        }

        List<AppEntry> catalog = fetchCatalog(isClient, false);
        if (catalog.isEmpty()) return "No catalog available. Run 'winget update' first.";
        AppEntry entry = null;
        for (AppEntry e : catalog) if (e.id.equalsIgnoreCase(appId)) { entry = e; break; }
        if (entry == null) return "Application not found in catalog: " + appId;

        List<InstalledApp> registry = loadRegistry(isClient);
        if (!force) {
            for (InstalledApp a : registry) {
                if (a.id.equalsIgnoreCase(appId)) {
                    return entry.name + " is already installed (v" + a.version + "). Use --force to reinstall.";
                }
            }
        }

        try {
            StringBuilder out = new StringBuilder();
            out.append("Found ").append(entry.name).append(" [").append(entry.id)
               .append("] v").append(entry.version).append("\n");
            out.append("Downloading ").append(entry.installer)
               .append(entry.size > 0 ? " (" + entry.size + " B)" : "").append(" ... ");
            Path pkg = downloadInstaller(entry, isClient);
            out.append("done\n");
            out.append(runInstaller(pkg, entry, location, scope, isClient));
            return out.toString();
        } catch (Exception e) {
            ShortcutTerminal.LOGGER.error("winget install failed: {}", appId, e);
            return "Installation failed: " + e.getMessage();
        }
    }

    /** 下载安装包到临时文件。 */
    private static Path downloadInstaller(AppEntry entry, boolean isClient) throws IOException {
        SourceConfig cfg = loadSourceConfig(isClient);
        List<Source> order = new ArrayList<>(cfg.sources);
        Source cur = activeSource(cfg);
        order.remove(cur);
        order.add(0, cur);
        Exception last = null;
        for (Source s : order) {
            String url = s.baseUrl + "/" + entry.installer;
            try {
                byte[] data = httpGetBytes(url);
                Path tmp = Files.createTempFile("winget_", ".stpkg");
                Files.write(tmp, data);
                return tmp;
            } catch (Exception e) {
                last = e;
            }
        }
        throw new IOException("all mirrors failed: " + (last != null ? last.getMessage() : "unknown"));
    }

    /**
     * 安装引擎：模拟安装程序（setup.exe）的行为——
     * 展开安装包载荷 → 读取安装清单 → 释放程序文件到目标目录 → 写入已装注册表。
     * 支持 --location（自定义安装目录，限游戏目录内）与 --scope（user/machine）。
     */
    private static String runInstaller(Path pkgFile, AppEntry entry, String location, String scope, boolean isClient) throws IOException {
        Path gameDir = PkgManager.getGameDir(isClient).toAbsolutePath().normalize();
        Path appsDir = getWindowsAppsDir(isClient);
        Files.createDirectories(appsDir);
        Path staged = Files.createTempDirectory("winget_stage_");
        try {
            // [安装程序] 展开载荷
            unzip(pkgFile, staged);
            // [安装程序] 读取安装清单
            Manifest mf = readManifest(staged, entry);
            // [安装程序] 解析安装目标：--location 指定 或 默认 Program/WindowsApps/<dir>
            Path target = (location != null)
                    ? safeResolveLocation(location, gameDir)
                    : appsDir.resolve(mf.dir);
            if (Files.exists(target)) deleteRecursive(target);
            Path payload = staged.resolve("files");
            if (!Files.exists(payload)) throw new IOException("installer payload missing (files/)");
            copyRecursive(payload, target);

            // [安装程序] 注册应用
            List<InstalledApp> registry = loadRegistry(isClient);
            registry.removeIf(a -> a.id.equalsIgnoreCase(mf.id));
            InstalledApp app = new InstalledApp();
            app.id = mf.id;
            app.name = mf.name;
            app.version = mf.version;
            app.path = gameDir.relativize(target.toAbsolutePath().normalize()).toString().replace('\\', '/');
            app.installedAt = System.currentTimeMillis();
            registry.add(app);
            saveRegistry(isClient, registry);

            return "[installer] silent mode | disable-interactivity | accept-agreements\n"
                 + "[installer] scope: " + scope
                 + (scope.equals("machine") ? " (elevation auto-approved, no UAC prompt)" : " (no elevation required)") + "\n"
                 + "[installer] location: " + app.path + "\n"
                 + "[installer] releasing files... done\n"
                 + "[installer] registering application... done\n"
                 + "Successfully installed: " + mf.name + " " + mf.version + "\n"
                 + "Run 'winget list' to see installed applications.";
        } finally {
            deleteRecursive(staged);
            try { Files.deleteIfExists(pkgFile); } catch (IOException ignored) {}
        }
    }

    /** 解析 --location：相对路径基于游戏目录；结果必须位于游戏目录内（防越界）。 */
    private static Path safeResolveLocation(String location, Path gameDir) throws IOException {
        Path p = Paths.get(location);
        if (!p.isAbsolute()) p = gameDir.resolve(location);
        p = p.toAbsolutePath().normalize();
        if (!p.startsWith(gameDir)) {
            throw new IOException("install location must be inside the game directory: " + location);
        }
        return p;
    }

    /** 安装清单模型。 */
    private static class Manifest {
        String id, name, version, dir;
    }

    private static Manifest readManifest(Path stagedDir, AppEntry entry) throws IOException {
        Path mfFile = stagedDir.resolve("install.json");
        Manifest mf = null;
        if (Files.exists(mfFile)) {
            try { mf = GSON.fromJson(Files.readString(mfFile), Manifest.class); } catch (Exception ignored) {}
        }
        if (mf == null) mf = new Manifest();
        if (mf.id == null) mf.id = entry.id;
        if (mf.name == null) mf.name = entry.name;
        if (mf.version == null) mf.version = entry.version;
        if (mf.dir == null) mf.dir = mf.name.replaceAll("[^A-Za-z0-9._-]", "_");
        return mf;
    }

    // ==================== 卸载 ====================

    private static String uninstall(String appId, boolean isClient) {
        List<InstalledApp> registry = loadRegistry(isClient);
        InstalledApp target = null;
        for (InstalledApp a : registry) if (a.id.equalsIgnoreCase(appId)) { target = a; break; }
        if (target == null) return "Not installed: " + appId;
        try {
            Path gameDir = PkgManager.getGameDir(isClient).toAbsolutePath().normalize();
            Path appDir = gameDir.resolve(target.path).normalize();
            // [卸载程序] 清理程序文件
            if (Files.exists(appDir)) deleteRecursive(appDir);
            registry.remove(target);
            saveRegistry(isClient, registry);
            return "Uninstalling " + target.name + "...\n"
                 + " -> removed " + target.path + "/\n"
                 + " -> unregistered application\n"
                 + "Successfully uninstalled: " + target.name;
        } catch (Exception e) {
            return "Uninstall failed: " + e.getMessage();
        }
    }

    // ==================== 查询 ====================

    private static String listInstalled(boolean isClient) {
        List<InstalledApp> registry = loadRegistry(isClient);
        if (registry.isEmpty()) return "No applications installed. Use 'winget search <keyword>' to browse.";
        StringBuilder sb = new StringBuilder(String.format("%-16s %-24s %s", "ID", "NAME", "VERSION"));
        sb.append("\n").append("-".repeat(54));
        for (InstalledApp a : registry) {
            sb.append(String.format("\n%-16s %-24s %s", a.id, clip(a.name, 24), a.version));
        }
        sb.append("\n").append(registry.size()).append(" application(s) installed.");
        return sb.toString();
    }

    private static String search(String keyword, boolean isClient) {
        List<AppEntry> catalog = fetchCatalog(isClient, false);
        if (catalog.isEmpty()) return "No catalog available. Run 'winget update' first.";
        String kw = keyword.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (AppEntry e : catalog) {
            boolean hit = e.id.toLowerCase(Locale.ROOT).contains(kw)
                    || (e.name != null && e.name.toLowerCase(Locale.ROOT).contains(kw))
                    || (e.description != null && e.description.toLowerCase(Locale.ROOT).contains(kw));
            if (!hit) continue;
            if (n == 0) {
                sb.append(String.format("%-16s %-24s %s", "ID", "NAME", "VERSION"))
                  .append("\n").append("-".repeat(54));
            }
            sb.append(String.format("\n%-16s %-24s %s", e.id, clip(e.name, 24), e.version));
            n++;
        }
        if (n == 0) return "No matching applications for: " + keyword;
        sb.append("\n").append(n).append(" result(s). Use 'winget install <id>' to install.");
        return sb.toString();
    }

    private static String show(String appId, boolean isClient) {
        List<AppEntry> catalog = fetchCatalog(isClient, false);
        for (AppEntry e : catalog) {
            if (e.id.equalsIgnoreCase(appId)) {
                return "ID:          " + e.id + "\n"
                     + "Name:        " + e.name + "\n"
                     + "Version:     " + e.version + "\n"
                     + "Description: " + (e.description == null ? "-" : e.description) + "\n"
                     + "Installer:   " + e.installer + "\n"
                     + "Size:        " + (e.size > 0 ? e.size + " B" : "unknown");
            }
        }
        return "Application not found: " + appId;
    }

    // ==================== 注册表 ====================

    private static List<InstalledApp> loadRegistry(boolean isClient) {
        Path f = getRegistryFile(isClient);
        if (Files.exists(f)) {
            try {
                List<InstalledApp> list = GSON.fromJson(Files.readString(f),
                        new TypeToken<List<InstalledApp>>(){}.getType());
                if (list != null) return list;
            } catch (Exception ignored) {}
        }
        return new ArrayList<>();
    }

    private static void saveRegistry(boolean isClient, List<InstalledApp> registry) {
        try {
            Path f = getRegistryFile(isClient);
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(registry));
        } catch (IOException ignored) {}
    }

    // ==================== 工具 ====================

    private static String httpGet(String urlStr) throws IOException {
        return new String(httpGetBytes(urlStr), StandardCharsets.UTF_8);
    }

    private static byte[] httpGetBytes(String urlStr) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(20000);
        int code = conn.getResponseCode();
        if (code != 200) throw new IOException("HTTP " + code + " from " + urlStr);
        try (InputStream in = conn.getInputStream()) {
            return in.readAllBytes();
        }
    }

    /** 展开安装包载荷（含 zip-slip 防护）。 */
    private static void unzip(Path zip, Path dest) throws IOException {
        try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                Path out = dest.resolve(e.getName()).normalize();
                if (!out.startsWith(dest)) continue;
                if (e.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zin, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void copyRecursive(Path src, Path dst) throws IOException {
        Files.walk(src).forEach(s -> {
            try {
                Path d = dst.resolve(src.relativize(s).toString());
                if (Files.isDirectory(s)) {
                    Files.createDirectories(d);
                } else {
                    Files.createDirectories(d.getParent());
                    Files.copy(s, d, StandardCopyOption.REPLACE_EXISTING);
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

    private static String clip(String s, int max) {
        if (s == null) return "-";
        return s.length() > max ? s.substring(0, max - 1) + "..." : s;
    }
}
