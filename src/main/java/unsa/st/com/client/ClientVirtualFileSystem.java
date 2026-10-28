package unsa.st.com.client;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端内存文件层：玩家在 GUI 终端里的工作副本（ConcurrentHashMap 存储）。
 *
 * <p>这不是独立文件系统——它是服务端真实文件（UserFileSystem 写到服务器磁盘的
 * "Terminal File/&lt;uuid&gt;/" 目录）的内存镜像。通过 {@code run synchrony -local/-server}
 * 与服务端真实文件双向同步；终端会话的历史与路径另存为 JSON
 * （gameDir/terminal_sessions/&lt;player&gt;_sessions.json）。</p>
 */
public class ClientVirtualFileSystem {
    private static final Map<String, Map<String, VirtualFile>> playerFileSystems = new ConcurrentHashMap<>();

    private static Map<String, VirtualFile> getFsForPlayer(String uuid) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null && uuid.equals(player.getName().getString())) uuid = player.getUUID().toString();
        return playerFileSystems.computeIfAbsent(uuid, k -> {
            Map<String, VirtualFile> fs = new ConcurrentHashMap<>();
            fs.put("/", new VirtualFile("/", true, true));
            return fs;
        });
    }

    public static List<String> listDirectory(String uuid, String path) {
        Map<String, VirtualFile> fs = getFsForPlayer(uuid);
        String np = normalizePath(path);
        if (!fs.containsKey(np) || !fs.get(np).isDirectory) return null;
        List<String> res = new ArrayList<>();
        String prefix = np.equals("/") ? "/" : np + "/";
        for (String k : fs.keySet()) {
            if (k.startsWith(prefix) && !k.equals(prefix)) {
                String rel = k.substring(prefix.length());
                if (!rel.contains("/")) res.add(fs.get(k).name + (fs.get(k).isDirectory ? "/" : ""));
                else { String d = rel.substring(0, rel.indexOf('/')); if (!res.contains(d + "/")) res.add(d + "/"); }
            }
        }
        java.util.Collections.sort(res);
        return res;
    }

    public static boolean createDirectory(String uuid, String path, String name) {
        Map<String, VirtualFile> fs = getFsForPlayer(uuid);
        String np = normalizePath(path);
        String full = normalizePath(np, name);
        if (fs.containsKey(full)) return false;
        int slash = full.lastIndexOf('/');
        VirtualFile parent = fs.get(slash == 0 ? "/" : full.substring(0, slash));
        if (parent == null || !parent.isDirectory) return false;
        fs.put(full, new VirtualFile(full, true, true));
        return true;
    }

    public static boolean createFile(String uuid, String path, String name) {
        Map<String, VirtualFile> fs = getFsForPlayer(uuid);
        String np = normalizePath(path);
        String full = normalizePath(np, name);
        if (fs.containsKey(full)) return false;
        int slash = full.lastIndexOf('/');
        VirtualFile parent = fs.get(slash == 0 ? "/" : full.substring(0, slash));
        if (parent == null || !parent.isDirectory) return false;
        fs.put(full, new VirtualFile(full, false, false));
        return true;
    }

    public static boolean delete(String uuid, String path, String name, boolean recursive) {
        Map<String, VirtualFile> fs = getFsForPlayer(uuid);
        String np = normalizePath(path);
        String full = normalizePath(np, name);
        VirtualFile vf = fs.get(full);
        if (vf == null || full.equals("/")) return false;
        if (vf.isDirectory) {
            String prefix = full + "/";
            boolean hasChildren = fs.keySet().stream().anyMatch(k -> k.startsWith(prefix));
            if (hasChildren && !recursive) return false;
            fs.keySet().removeIf(k -> k.startsWith(prefix));
        }
        fs.remove(full);
        return true;
    }

    public static String readFile(String uuid, String path, String name) {
        Map<String, VirtualFile> fs = getFsForPlayer(uuid);
        String np = normalizePath(path);
        String full = normalizePath(np, name);
        VirtualFile vf = fs.get(full);
        return (vf == null || vf.isDirectory) ? null : vf.content == null ? "" : vf.content;
    }

    /**
     * 写入文件；超出 SSD 存储配额时拒绝并返回 false。
     * （配额读取当前客户端玩家的面板；读取失败则放行，不影响旧行为）
     */
    public static boolean writeFile(String uuid, String path, String name, String content) {
        Map<String, VirtualFile> fs = getFsForPlayer(uuid);
        String np = normalizePath(path);
        String full = normalizePath(np, name);
        VirtualFile vf = fs.get(full);
        if (full.equals("/") || content == null || vf != null && vf.isDirectory || !ensureParents(fs, full)) return false;
        long oldLen = (vf != null && !vf.isDirectory && vf.content != null) ? vf.content.length() : 0;
        try {
            long quota = ClientHardware.storageQuotaChars();
            long used = totalChars(uuid);
            if (used - oldLen + content.length() > quota) {
                return false; // no space left on device
            }
        } catch (Throwable ignored) {
            // 配额系统不可用时放行
        }
        if (vf == null) { vf = new VirtualFile(full, false, false); fs.put(full, vf); }
        vf.content = content;
        return true;
    }

    /** 玩家 VFS 全部文件字符总数（存储配额用）。 */
    public static long totalChars(String uuid) {
        Map<String, VirtualFile> fs = getFsForPlayer(uuid);
        long n = 0;
        for (VirtualFile v : fs.values()) {
            if (v.isDirectory || v.content == null) continue;
            n += v.content.length();
        }
        return n;
    }

    public static void writeFileFromStream(String uuid, String path, String name, InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader r = new BufferedReader(new InputStreamReader(in));
        String l; while ((l = r.readLine()) != null) sb.append(l).append("\n");
        writeFile(uuid, path, name, sb.toString());
    }

    public static boolean setExecutable(String uuid, String path, String name, boolean exec) {
        Map<String, VirtualFile> fs = getFsForPlayer(uuid);
        String np = normalizePath(path);
        String full = normalizePath(np, name);
        VirtualFile vf = fs.get(full);
        if (vf == null) return false;
        vf.executable = exec;
        return true;
    }

    public static boolean directoryExists(String uuid, String path) {
        Map<String, VirtualFile> fs = getFsForPlayer(uuid);
        VirtualFile vf = fs.get(normalizePath(path));
        return vf != null && vf.isDirectory;
    }

    public static String normalizePath(String current, String target) {
        if (current == null || current.isEmpty()) current = "/";
        if (target == null || target.isEmpty()) target = ".";
        String combined = target.startsWith("/") ? target : current + "/" + target;
        java.util.Deque<String> parts = new java.util.ArrayDeque<>();
        for (String part : combined.split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) { if (!parts.isEmpty()) parts.removeLast(); }
            else parts.addLast(part);
        }
        return "/" + String.join("/", parts);
    }
    private static String normalizePath(String path) { return normalizePath("/", path); }
    private static boolean ensureParents(Map<String, VirtualFile> fs, String path) {
        int slash = path.lastIndexOf('/');
        if (slash <= 0) return true;
        String parent = path.substring(0, slash);
        VirtualFile entry = fs.get(parent);
        if (entry != null) return entry.isDirectory;
        if (!ensureParents(fs, parent)) return false;
        fs.put(parent, new VirtualFile(parent, true, true)); return true;
    }

    public static boolean copy(String uuid, String path, String src, String dst, boolean recursive) {
        Map<String, VirtualFile> fs = getFsForPlayer(uuid);
        String np = normalizePath(path);
        String srcPath = normalizePath(np, src);
        String dstPath = normalizePath(np, dst);
        VirtualFile sv = fs.get(srcPath);
        if (sv == null || (sv.isDirectory && !recursive)) return false;
        if (srcPath.equals("/") || srcPath.equals(dstPath) || dstPath.startsWith(srcPath + "/")) return false;
        Map<String, VirtualFile> additions = new HashMap<>();
        for (var entry : new ArrayList<>(fs.entrySet())) {
            String key = entry.getKey();
            if (!key.equals(srcPath) && !key.startsWith(srcPath + "/")) continue;
            VirtualFile old = entry.getValue();
            String target = dstPath + key.substring(srcPath.length());
            VirtualFile copy = new VirtualFile(target, old.isDirectory, old.executable); copy.content = old.content;
            VirtualFile existing = fs.get(target);
            if (existing != null && existing.isDirectory != copy.isDirectory) return false;
            additions.put(target, copy);
        }
        long newTotal = totalChars(uuid);
        for (var entry : additions.entrySet()) {
            VirtualFile old = fs.get(entry.getKey()), value = entry.getValue();
            if (old != null && old.content != null) newTotal -= old.content.length();
            if (value.content != null) newTotal += value.content.length();
        }
        if (newTotal > ClientHardware.storageQuotaChars() || !ensureParents(fs, dstPath)) return false;
        fs.putAll(additions);
        return true;
    }

    public static boolean move(String uuid, String path, String src, String dst) {
        return copy(uuid, path, src, dst, true) && delete(uuid, path, src, true);
    }

    public static Map<String, String> getFileSystemSnapshot(String uuid) {
        Map<String, VirtualFile> fs = getFsForPlayer(uuid);
        Map<String, String> snap = new HashMap<>();
        fs.forEach((k,v) -> {
            if (!v.isDirectory) snap.put(k, v.content == null ? "" : v.content);
        });
        return snap;
    }

    static class VirtualFile {
        String name; boolean isDirectory; boolean executable; String content;
        VirtualFile(String path, boolean dir, boolean exec) { this.name = path.substring(path.lastIndexOf('/')+1); this.isDirectory = dir; this.executable = exec; }
    }
}
