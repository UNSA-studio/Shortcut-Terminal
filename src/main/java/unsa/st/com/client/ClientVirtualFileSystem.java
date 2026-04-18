package unsa.st.com.client;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClientVirtualFileSystem {
    private static final Map<String, Map<String, VirtualFile>> playerFileSystems = new ConcurrentHashMap<>();

    private static Map<String, VirtualFile> getFsForPlayer(String uuid) {
        return playerFileSystems.computeIfAbsent(uuid, k -> {
            Map<String, VirtualFile> fs = new ConcurrentHashMap<>();
            fs.put("/", new VirtualFile("/", true, true));
            return fs;
        });
    }

    public static List<String> listDirectory(String uuid, String path) {
        Map<String, VirtualFile> fs = getFsForPlayer(uuid);
        String np = normalizePath(path);
        if (!fs.containsKey(np)) return null;
        List<String> res = new ArrayList<>();
        String prefix = np.equals("/") ? "/" : np + "/";
        for (String k : fs.keySet()) {
            if (k.startsWith(prefix) && !k.equals(prefix)) {
                String rel = k.substring(prefix.length());
                if (!rel.contains("/")) res.add(fs.get(k).name + (fs.get(k).isDirectory ? "/" : ""));
                else { String d = rel.substring(0, rel.indexOf('/')); if (!res.contains(d + "/")) res.add(d + "/"); }
            }
        }
        return res;
    }

    public static boolean createDirectory(String uuid, String path, String name) {
        Map<String, VirtualFile> fs = getFsForPlayer(uuid);
        String np = normalizePath(path);
        String full = np.equals("/") ? "/" + name : np + "/" + name;
        if (fs.containsKey(full)) return false;
        fs.put(full, new VirtualFile(full, true, true));
        return true;
    }

    public static boolean createFile(String uuid, String path, String name) {
        Map<String, VirtualFile> fs = getFsForPlayer(uuid);
        String np = normalizePath(path);
        String full = np.equals("/") ? "/" + name : np + "/" + name;
        if (fs.containsKey(full)) return false;
        fs.put(full, new VirtualFile(full, false, false));
        return true;
    }

    public static boolean delete(String uuid, String path, String name, boolean recursive) {
        Map<String, VirtualFile> fs = getFsForPlayer(uuid);
        String np = normalizePath(path);
        String full = np.equals("/") ? "/" + name : np + "/" + name;
        VirtualFile vf = fs.get(full);
        if (vf == null) return false;
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
        String full = np.equals("/") ? "/" + name : np + "/" + name;
        VirtualFile vf = fs.get(full);
        return (vf == null || vf.isDirectory) ? null : vf.content == null ? "" : vf.content;
    }

    public static void writeFile(String uuid, String path, String name, String content) {
        Map<String, VirtualFile> fs = getFsForPlayer(uuid);
        String np = normalizePath(path);
        String full = np.equals("/") ? "/" + name : np + "/" + name;
        VirtualFile vf = fs.get(full);
        if (vf == null) { vf = new VirtualFile(full, false, false); fs.put(full, vf); }
        vf.content = content;
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
        String full = np.equals("/") ? "/" + name : np + "/" + name;
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
        if (target.isEmpty() || target.equals(".")) return current;
        if (target.equals("..")) { int i = current.lastIndexOf('/'); return i > 0 ? current.substring(0, i) : "/"; }
        if (target.startsWith("/")) return target;
        return current.equals("/") ? "/" + target : current + "/" + target;
    }
    private static String normalizePath(String p) { return p.isEmpty() ? "/" : (p.startsWith("/") ? p : "/" + p); }

    public static boolean copy(String uuid, String path, String src, String dst, boolean recursive) {
        Map<String, VirtualFile> fs = getFsForPlayer(uuid);
        String np = normalizePath(path);
        String srcPath = np.equals("/") ? "/" + src : np + "/" + src;
        String dstPath = np.equals("/") ? "/" + dst : np + "/" + dst;
        VirtualFile sv = fs.get(srcPath);
        if (sv == null || (sv.isDirectory && !recursive)) return false;
        deepCopy(fs, srcPath, dstPath);
        return true;
    }
    private static void deepCopy(Map<String, VirtualFile> fs, String src, String dst) {
        VirtualFile sv = fs.get(src);
        if (sv == null) return;
        VirtualFile dv = new VirtualFile(dst, sv.isDirectory, sv.executable);
        dv.content = sv.content;
        fs.put(dst, dv);
        if (sv.isDirectory) {
            String prefix = src + "/";
            for (String k : fs.keySet()) if (k.startsWith(prefix)) deepCopy(fs, k, dst + k.substring(src.length()));
        }
    }

    public static boolean move(String uuid, String path, String src, String dst) {
        return copy(uuid, path, src, dst, true) && delete(uuid, path, src, true);
    }

    public static Map<String, String> getFileSystemSnapshot(String uuid) {
        Map<String, VirtualFile> fs = getFsForPlayer(uuid);
        Map<String, String> snap = new HashMap<>();
        fs.forEach((k,v) -> snap.put(k, v.isDirectory ? "<DIR>" : v.content));
        return snap;
    }

    static class VirtualFile {
        String name; boolean isDirectory; boolean executable; String content;
        VirtualFile(String path, boolean dir, boolean exec) { this.name = path.substring(path.lastIndexOf('/')+1); this.isDirectory = dir; this.executable = exec; }
    }
}
