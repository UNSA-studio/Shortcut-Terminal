package unsa.st.com.client;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端本地虚拟文件系统，用于终端离线操作
 * 结构：Map<路径, 文件内容或目录标记>
 * 目录以 "/" 结尾，文件内容为字符串
 */
public class ClientVirtualFileSystem {
    // 玩家UUID -> 文件系统映射
    private static final Map<String, Map<String, String>> playerFileSystems = new ConcurrentHashMap<>();
    
    private static Map<String, String> getFsForPlayer(String uuid) {
        return playerFileSystems.computeIfAbsent(uuid, k -> {
            Map<String, String> fs = new ConcurrentHashMap<>();
            // 根目录标记
            fs.put("/", "<DIR>");
            return fs;
        });
    }
    
    /**
     * 列出目录内容
     */
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
    
    /**
     * 创建目录
     */
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
    
    /**
     * 创建文件
     */
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
    
    /**
     * 删除文件或空目录
     */
    public static boolean delete(String uuid, String path, String name) {
        Map<String, String> fs = getFsForPlayer(uuid);
        String normalizedPath = normalizePath(path);
        String fullPath = normalizedPath.equals("/") ? "/" + name : normalizedPath + "/" + name;
        if (fs.containsKey(fullPath + "/")) {
            // 检查目录是否为空
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
    
    /**
     * 读取文件内容
     */
    public static String readFile(String uuid, String path, String name) {
        Map<String, String> fs = getFsForPlayer(uuid);
        String normalizedPath = normalizePath(path);
        String fullPath = normalizedPath.equals("/") ? "/" + name : normalizedPath + "/" + name;
        return fs.getOrDefault(fullPath, null);
    }
    
    /**
     * 写入文件内容
     */
    public static void writeFile(String uuid, String path, String name, String content) {
        Map<String, String> fs = getFsForPlayer(uuid);
        String normalizedPath = normalizePath(path);
        String fullPath = normalizedPath.equals("/") ? "/" + name : normalizedPath + "/" + name;
        fs.put(fullPath, content);
    }
    
    /**
     * 检查目录是否存在
     */
    public static boolean directoryExists(String uuid, String path, String target) {
        Map<String, String> fs = getFsForPlayer(uuid);
        String normalizedPath = normalizePath(path);
        String fullPath;
        if (target.isEmpty() || target.equals(".")) {
            fullPath = normalizedPath;
        } else {
            fullPath = normalizedPath.equals("/") ? "/" + target : normalizedPath + "/" + target;
        }
        return fs.containsKey(fullPath.endsWith("/") ? fullPath : fullPath + "/");
    }
    
    /**
     * 路径规范化
     */
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
    
    /**
     * 获取整个文件系统快照（用于同步）
     */
    public static Map<String, String> getFileSystemSnapshot(String uuid) {
        return new HashMap<>(getFsForPlayer(uuid));
    }
}
