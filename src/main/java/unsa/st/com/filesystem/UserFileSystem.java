package unsa.st.com.filesystem;

import net.minecraft.server.MinecraftServer;
import unsa.st.com.ShortcutTerminal;

import java.io.*;
import java.nio.file.*;
import java.util.*;

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
    public static boolean isPathValid(UUID u, String rel) { return getUserPath(u).resolve(rel).normalize().startsWith(getUserPath(u)); }
    public static String normalizePath(String cur, String tgt) {
        if (tgt.isEmpty() || tgt.equals(".")) return cur;
        if (tgt.equals("..")) { int i = cur.lastIndexOf('/'); return i > 0 ? cur.substring(0, i) : ""; }
        return cur.isEmpty() ? tgt : cur + "/" + tgt;
    }
    public static List<String> listDirectory(UUID u, String rel) {
        Path p = getUserPath(u).resolve(rel);
        if (!Files.isDirectory(p)) return null;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(p)) {
            List<String> l = new ArrayList<>();
            for (Path e : ds) l.add(e.getFileName() + (Files.isDirectory(e) ? "/" : ""));
            return l;
        } catch (IOException e) { return null; }
    }
    public static boolean createDirectory(UUID u, String rel, String name) {
        if (!isPathValid(u, rel)) return false;
        try { Files.createDirectory(getUserPath(u).resolve(rel).resolve(name)); return true; } catch (IOException e) { return false; }
    }
    public static boolean createFile(UUID u, String rel, String name) {
        if (!isPathValid(u, rel)) return false;
        try { Files.createFile(getUserPath(u).resolve(rel).resolve(name)); return true; } catch (IOException e) { return false; }
    }
    public static boolean delete(UUID u, String rel, String name, boolean rec) {
        if (!isPathValid(u, rel)) return false;
        Path t = getUserPath(u).resolve(rel).resolve(name);
        try {
            if (rec && Files.isDirectory(t)) Files.walk(t).sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
            else Files.delete(t);
            return true;
        } catch (IOException e) { return false; }
    }
    public static String readFile(UUID u, String rel, String name) {
        if (!isPathValid(u, rel)) return null;
        Path f = getUserPath(u).resolve(rel).resolve(name);
        if (!Files.isRegularFile(f)) return null;
        try { return Files.readString(f); } catch (IOException e) { return null; }
    }
    public static void writeFile(UUID u, String rel, String name, String content) {
        if (!isPathValid(u, rel)) return;
        try { Files.writeString(getUserPath(u).resolve(rel).resolve(name), content); } catch (IOException e) {}
    }
    public static void writeFileFromStream(UUID u, String rel, String name, InputStream in) throws IOException {
        if (!isPathValid(u, rel)) return;
        Files.copy(in, getUserPath(u).resolve(rel).resolve(name), StandardCopyOption.REPLACE_EXISTING);
    }
    public static boolean setExecutable(UUID u, String rel, String name, boolean exec) {
        if (!isPathValid(u, rel)) return false;
        return getUserPath(u).resolve(rel).resolve(name).toFile().setExecutable(exec);
    }
    public static boolean directoryExists(UUID u, String cur, String tgt) {
        if (tgt.isEmpty() || tgt.equals(".")) return true;
        Path p = getUserPath(u).resolve(normalizePath(cur, tgt));
        return Files.isDirectory(p);
    }
    public static boolean copy(UUID u, String cur, String src, String dst, boolean rec) {
        if (!isPathValid(u, cur)) return false;
        Path s = getUserPath(u).resolve(cur).resolve(src), d = getUserPath(u).resolve(cur).resolve(dst);
        try {
            if (rec && Files.isDirectory(s)) Files.walk(s).forEach(p -> {
                try { Path t = d.resolve(s.relativize(p)); if (Files.isDirectory(p)) Files.createDirectories(t); else Files.copy(p, t, StandardCopyOption.REPLACE_EXISTING); } catch (IOException ignored) {}
            });
            else Files.copy(s, d, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) { return false; }
    }
    public static boolean move(UUID u, String cur, String src, String dst) {
        return copy(u, cur, src, dst, true) && delete(u, cur, src, true);
    }
    public static Path resolvePath(UUID u, String cur, String tgt) { return getUserPath(u).resolve(normalizePath(cur, tgt)); }
    public static Map<String, String> getFileSystemSnapshot(UUID uuid) {
    Map<String, String> snapshot = new HashMap<>();
    Path userRoot = getUserPath(uuid);
    if (!Files.exists(userRoot)) return snapshot;
    
    try {
        Files.walk(userRoot)
                .filter(Files::isRegularFile)
                .forEach(file -> {
                    try {
                        String relativePath = "/" + userRoot.relativize(file).toString().replace('\\', '/');
                        String content = Files.readString(file);
                        snapshot.put(relativePath, content);
                    } catch (IOException ignored) {}
                });
    } catch (IOException ignored) {}
    return snapshot;
}
}
