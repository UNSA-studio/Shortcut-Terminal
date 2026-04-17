package unsa.st.com.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import unsa.st.com.ShortcutTerminal;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理多个终端会话，支持持久化
 */
public class TerminalSessionManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, List<SessionData>> playerSessions = new ConcurrentHashMap<>();
    
    /**
     * 获取玩家名对应的会话列表
     */
    public static List<SessionData> getSessions(String playerName) {
        return playerSessions.computeIfAbsent(playerName, k -> loadSessions(playerName));
    }
    
    /**
     * 为玩家创建一个新会话
     */
    public static SessionData createSession(String playerName) {
        List<SessionData> sessions = getSessions(playerName);
        SessionData session = new SessionData(playerName);
        session.index = sessions.size();
        sessions.add(session);
        saveSessions(playerName, sessions);
        return session;
    }
    
    /**
     * 保存指定玩家的所有会话
     */
    public static void saveSessions(String playerName, List<SessionData> sessions) {
        playerSessions.put(playerName, sessions);
        Path file = getSessionsFile(playerName);
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(sessions, writer);
            }
        } catch (IOException e) {
            ShortcutTerminal.LOGGER.error("Failed to save terminal sessions", e);
        }
    }
    
    /**
     * 从文件加载会话
     */
    private static List<SessionData> loadSessions(String playerName) {
        Path file = getSessionsFile(playerName);
        if (!Files.exists(file)) {
            // 创建默认会话
            List<SessionData> list = new ArrayList<>();
            SessionData defaultSession = new SessionData(playerName);
            defaultSession.index = 0;
            list.add(defaultSession);
            saveSessions(playerName, list);
            return list;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            List<SessionData> loaded = GSON.fromJson(reader, new TypeToken<List<SessionData>>(){}.getType());
            if (loaded == null || loaded.isEmpty()) {
                SessionData defaultSession = new SessionData(playerName);
                defaultSession.index = 0;
                loaded = new ArrayList<>();
                loaded.add(defaultSession);
                saveSessions(playerName, loaded);
            }
            // 重新索引
            for (int i = 0; i < loaded.size(); i++) {
                loaded.get(i).index = i;
            }
            return loaded;
        } catch (IOException e) {
            ShortcutTerminal.LOGGER.error("Failed to load terminal sessions", e);
            List<SessionData> list = new ArrayList<>();
            SessionData defaultSession = new SessionData(playerName);
            defaultSession.index = 0;
            list.add(defaultSession);
            return list;
        }
    }
    
    private static Path getSessionsFile(String playerName) {
        return Paths.get(Minecraft.getInstance().gameDirectory.getAbsolutePath(), 
                "terminal_sessions", playerName + "_sessions.json");
    }
    
    /**
     * 会话数据
     */
    public static class SessionData {
        public transient int index;
        public String playerName;
        public String currentPath = "/";
        public List<String> outputLines = new ArrayList<>();
        public List<String> commandHistory = new ArrayList<>();
        
        public SessionData() {}
        
        public SessionData(String playerName) {
            this.playerName = playerName;
        }
        
        public ClientCommandExecutor createExecutor() {
            ClientCommandExecutor executor = new ClientCommandExecutor(playerName);
            executor.setCurrentPath(currentPath);
            executor.setCommandHistory(new ArrayList<>(commandHistory));
            return executor;
        }
        
        public void updateFromExecutor(ClientCommandExecutor executor, List<String> outputLines) {
            this.currentPath = executor.getCurrentPath();
            this.commandHistory = new ArrayList<>(executor.getCommandHistory());
            this.outputLines = new ArrayList<>(outputLines);
        }
    }
}
