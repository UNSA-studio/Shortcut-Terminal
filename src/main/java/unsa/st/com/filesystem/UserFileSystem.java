package unsa.st.com.filesystem;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import unsa.st.com.ShortcutTerminal;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * 服务端真实文件系统：玩家终端文件的磁盘存储。
 * 根目录为服务器目录下的 "Terminal File/&lt;uuid&gt;/"，所有读写都是真实文件 IO，
 * 服务器重启后依然存在。含路径穿越防护（safeResolve 白名单式校验）。
 * 客户端 GUI 终端持有的是其内存副本（ClientVirtualFileSystem），经 synchrony 同步。
 */
public class UserFileSystem {
    private static final String BASE_FOLDER = "Terminal File";
    private static Path basePath;

    private static Path getBasePath() {
        if (basePath == null) {
            MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
            basePath = (s != null ? s.getServerDirectory() : Paths.get(BASE_FOLDER)).resolve(BASE_FOLDER);
            try { Files.createDirectories(basePath); } catch (IOException e) {}
        }
        return basePath;
    }
    public static Path getUserPath(UUID u) { return getBasePath().resolve(u.toString()); }
    public static void createUserDirectory(UUID u) { try { Files.createDirectories(getUserPath(u)); } catch (IOException e) {} }

    public static boolean isNameSafe(String name) {
        if (name == null || name.isEmpty()) return false;
        if (name.contains("/") || name.contains("\\") || name.contains("..") || name.contains("\0")) return false;
        if (name.equals(".") || name.equals("~")) return false;
        return true;
    }

    private static boolean isRelSafe(String rel) {
        if (rel == null) return true;
        for (String seg : rel.split("/")) {
            if (!seg.isEmpty() && !isNameSafe(seg)) return false;
        }
        return true;
    }

    private static Path safeResolve(UUID u, String rel, String name) {
        if (!isNameSafe(name)) return null;
        if (!isRelSafe(rel)) return null;
        Path root = getUserPath(u).toAbsolutePath().normalize();
        try { Files.createDirectories(root); } catch (IOException e) { return null; }
        Path target = root;
        if (rel != null && !rel.isEmpty()) {
            for (String seg : rel.split("/")) {
                if (seg.isEmpty()) continue;
                target = target.resolve(seg);
            }
        }
        target = target.resolve(name).normalize();
        if (!target.startsWith(root)) return null;
        return target;
    }

    private static Path safeResolveDirOnly(UUID u, String rel) {
        if (!isRelSafe(rel)) return null;
        Path root = getUserPath(u).toAbsolutePath().normalize();
        try { Files.createDirectories(root); } catch (IOException e) { return null; }
        Path target = root;
        if (rel != null && !rel.isEmpty()) {
            for (String seg : rel.split("/")) {
                if (seg.isEmpty()) continue;
                target = target.resolve(seg);
            }
        }
        target = target.normalize();
        if (!target.startsWith(root)) return null;
        return target;
    }

    public static boolean isPathValid(UUID u, String rel) { return safeResolveDirOnly(u, rel) != null; }

    public static String normalizePath(String cur, String tgt) {
        if (tgt.isEmpty() || tgt.equals(".")) return cur;
        if (tgt.equals("..")) { int i = cur.lastIndexOf('/'); return i > 0 ? cur.substring(0, i) : ""; }
        return cur.isEmpty() ? tgt : cur + "/" + tgt;
    }
    public static List<String> listDirectory(UUID u, String rel) {
        Path p = safeResolveDirOnly(u, rel);
        if (p == null || !Files.isDirectory(p)) return null;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(p)) {
            List<String> l = new ArrayList<>();
            for (Path e : ds) l.add(e.getFileName() + (Files.isDirectory(e) ? "/" : ""));
            return l;
        } catch (IOException e) { return null; }
    }
    public static boolean createDirectory(UUID u, String rel, String name) {
        Path t = safeResolve(u, rel, name);
        if (t == null) return false;
        try { Files.createDirectories(t); return true; } catch (IOException e) { return false; }
    }
    public static boolean createFile(UUID u, String rel, String name) {
        Path t = safeResolve(u, rel, name);
        if (t == null) return false;
        try { if (Files.exists(t)) return false; Files.createFile(t); return true; } catch (IOException e) { return false; }
    }
    public static boolean delete(UUID u, String rel, String name, boolean rec) {
        Path t = safeResolve(u, rel, name);
        if (t == null) return false;
        try {
            if (rec && Files.isDirectory(t)) Files.walk(t).sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
            else Files.delete(t);
            return true;
        } catch (IOException e) { return false; }
    }
    public static String readFile(UUID u, String rel, String name) {
        Path f = safeResolve(u, rel, name);
        if (f == null || !Files.isRegularFile(f)) return null;
        try { return Files.readString(f); } catch (IOException e) { return null; }
    }
    public static void writeFile(UUID u, String rel, String name, String content) {
        Path f = safeResolve(u, rel, name);
        if (f == null) return;
        try {
            Files.createDirectories(f.getParent());
            Files.writeString(f, content);
        } catch (IOException e) {}
    }
    public static void writeFileFromStream(UUID u, String rel, String name, InputStream in) throws IOException {
        Path f = safeResolve(u, rel, name);
        if (f == null) return;
        Files.createDirectories(f.getParent());
        Files.copy(in, f, StandardCopyOption.REPLACE_EXISTING);
    }
    public static boolean setExecutable(UUID u, String rel, String name, boolean exec) {
        Path f = safeResolve(u, rel, name);
        if (f == null) return false;
        return f.toFile().setExecutable(exec);
    }
    public static boolean directoryExists(UUID u, String cur, String tgt) {
        if (tgt.isEmpty() || tgt.equals(".")) return true;
        Path p = safeResolveDirOnly(u, normalizePath(cur, tgt));
        return p != null && Files.isDirectory(p);
    }
    public static boolean copy(UUID u, String cur, String src, String dst, boolean rec) {
        Path s = safeResolve(u, cur, src), d = safeResolve(u, cur, dst);
        if (s == null || d == null) return false;
        try {
            if (rec && Files.isDirectory(s)) Files.walk(s).forEach(p -> {
                try { Path t = d.resolve(s.relativize(p)); if (Files.isDirectory(p)) Files.createDirectories(t); else { Files.createDirectories(t.getParent()); Files.copy(p, t, StandardCopyOption.REPLACE_EXISTING); } } catch (IOException ignored) {}
            });
            else { Files.createDirectories(d.getParent()); Files.copy(s, d, StandardCopyOption.REPLACE_EXISTING); }
            return true;
        } catch (IOException e) { return false; }
    }
    public static boolean move(UUID u, String cur, String src, String dst) {
        return copy(u, cur, src, dst, true) && delete(u, cur, src, true);
    }
    public static Path resolvePath(UUID u, String cur, String tgt) {
        Path p = safeResolveDirOnly(u, normalizePath(cur, tgt));
        return p != null ? p : getUserPath(u);
    }
    public static Map<String, String> getFileSystemSnapshot(UUID uuid) {
        Map<String, String> snapshot = new HashMap<>();
        Path userRoot = getUserPath(uuid);
        if (!Files.exists(userRoot)) return snapshot;

        try {
            Files.walk(userRoot)
                    .filter(Files::isRegularFile)
                    .forEach(file -> {
                        try {
                            String relativePath = userRoot.relativize(file).toString().replace('\\', '/');
                            String content = Files.readString(file);
                            snapshot.put(relativePath, content);
                        } catch (IOException ignored) {}
                    });
        } catch (IOException ignored) {}
        return snapshot;
    }
}