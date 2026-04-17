package unsa.st.com.client;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClientVirtualFileSystem {
    private static final Map<String, Map<String, String>> playerFileSystems = new ConcurrentHashMap<>();
    
    private static Map<String, String> getFsForPlayer(String uuid) {
        return playerFileSystems.computeIfAbsent(uuid, k -> {
            Map<String, String> fs = new ConcurrentHashMap<>();
            fs.put("/", "<DIR>");
            return fs;
        });
    }
    
    public static List<String> listDirectory(String uuid, String path) {
        Map<String, String> fs = getFsForPlayer(uuid);
        String normalizedPath = normalizePath(path);
        List<String> result = new ArrayList<>();
        String prefix = normalizedPath.equals("/") ? "/" : normalizedPath + "/";
        for (String key : fs.keySet()) {
            if (key.startsWith(prefix) && !key.equals(prefix)) {
                String relative = key.substring(prefix.length());
                if (!relative.contains("/")) {
                    result.add(relative);
                } else {
                    String dirName = relative.substring(0, relative.indexOf('/'));
                    if (!result.contains(dirName + "/")) {
                        result.add(dirName + "/");
                    }
                }
            }
        }
        return result;
    }
    
    public static boolean createDirectory(String uuid, String path, String name) {
        Map<String, String> fs = getFsForPlayer(uuid);
        String normalizedPath = normalizePath(path);
        String fullPath = normalizedPath.equals("/") ? "/" + name : normalizedPath + "/" + name;
        if (fs.containsKey(fullPath + "/") || fs.containsKey(fullPath)) {
            return false;
        }
        fs.put(fullPath + "/", "<DIR>");
        return true;
    }
    
    public static boolean createFile(String uuid, String path, String name) {
        Map<String, String> fs = getFsForPlayer(uuid);
        String normalizedPath = normalizePath(path);
        String fullPath = normalizedPath.equals("/") ? "/" + name : normalizedPath + "/" + name;
        if (fs.containsKey(fullPath + "/") || fs.containsKey(fullPath)) {
            return false;
        }
        fs.put(fullPath, "");
        return true;
    }
    
    public static boolean delete(String uuid, String path, String name) {
        Map<String, String> fs = getFsForPlayer(uuid);
        String normalizedPath = normalizePath(path);
        String fullPath = normalizedPath.equals("/") ? "/" + name : normalizedPath + "/" + name;
        if (fs.containsKey(fullPath + "/")) {
            String prefix = fullPath + "/";
            boolean hasChildren = fs.keySet().stream().anyMatch(k -> k.startsWith(prefix) && !k.equals(prefix));
            if (hasChildren) return false;
            fs.remove(fullPath + "/");
            return true;
        } else if (fs.containsKey(fullPath)) {
            fs.remove(fullPath);
            return true;
        }
        return false;
    }
    
    public static String readFile(String uuid, String path, String name) {
        Map<String, String> fs = getFsForPlayer(uuid);
        String normalizedPath = normalizePath(path);
        String fullPath = normalizedPath.equals("/") ? "/" + name : normalizedPath + "/" + name;
        return fs.getOrDefault(fullPath, null);
    }
    
    public static void writeFile(String uuid, String path, String name, String content) {
        Map<String, String> fs = getFsForPlayer(uuid);
        String normalizedPath = normalizePath(path);
        String fullPath = normalizedPath.equals("/") ? "/" + name : normalizedPath + "/" + name;
        fs.put(fullPath, content);
    }
    
    public static boolean directoryExists(String uuid, String path) {
        Map<String, String> fs = getFsForPlayer(uuid);
        String normalized = normalizePath(path);
        return fs.containsKey(normalized.endsWith("/") ? normalized : normalized + "/");
    }
    
    public static String normalizePath(String current, String target) {
        if (target.isEmpty() || target.equals(".")) return current;
        if (target.equals("..")) {
            int lastSlash = current.lastIndexOf('/');
            return lastSlash > 0 ? current.substring(0, lastSlash) : "/";
        }
        if (target.startsWith("/")) return target;
        return current.equals("/") ? "/" + target : current + "/" + target;
    }
    
    private static String normalizePath(String path) {
        if (path.isEmpty()) return "/";
        return path.startsWith("/") ? path : "/" + path;
    }
    
    public static Map<String, String> getFileSystemSnapshot(String uuid) {
        return new HashMap<>(getFsForPlayer(uuid));
    }
}
