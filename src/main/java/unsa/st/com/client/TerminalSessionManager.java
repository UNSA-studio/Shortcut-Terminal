package unsa.st.com.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import unsa.st.com.ShortcutTerminal;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Persist sessions atomically. Creating a session adds it exactly ONCE. */
public final class TerminalSessionManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, List<SessionData>> playerSessions = new ConcurrentHashMap<>();
    private TerminalSessionManager() {}
    public static List<SessionData> getSessions(String name) {
        return playerSessions.computeIfAbsent(name, k -> {
            List<SessionData> loaded = load(name);
            if (loaded.isEmpty()) loaded.add(new SessionData(name));
            reindex(loaded);
            return loaded;
        });
    }
    public static SessionData createSession(String name) {
        List<SessionData> list = getSessions(name);
        SessionData data = new SessionData(name);
        list.add(data); reindex(list);
        return data;
    }
    public static void reindex(List<SessionData> list) {
        for (int i = 0; i < list.size(); i++) list.get(i).index = i;
    }
    private static Path file(String name) {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("terminal_sessions").resolve(name + "_sessions.json");
    }
    private static List<SessionData> load(String name) {
        Path file = file(name);
        if (!Files.isRegularFile(file)) return new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(file)) {
            List<SessionData> result = GSON.fromJson(reader, new TypeToken<List<SessionData>>(){}.getType());
            if (result == null) return new ArrayList<>();
            result.removeIf(Objects::isNull);
            for (SessionData data : result) {
                data.playerName = name;
                if (data.currentPath == null) data.currentPath = "/";
                if (data.outputLines == null) data.outputLines = new ArrayList<>();
                if (data.commandHistory == null) data.commandHistory = new ArrayList<>();
                data.outputLines.removeIf(Objects::isNull); data.commandHistory.removeIf(Objects::isNull);
                if (data.draft == null) data.draft = "";
                if (!data.outputLines.isEmpty()) data.booted = true;
            }
            return result;
        } catch (IOException | RuntimeException e) {
            ShortcutTerminal.LOGGER.error("Failed to load terminal sessions; keeping a recovery copy", e);
            try { Files.copy(file, file.resolveSibling(file.getFileName() + ".broken-" + System.currentTimeMillis())); }
            catch (IOException copyError) { ShortcutTerminal.LOGGER.warn("Could not back up sessions", copyError); }
            return new ArrayList<>();
        }
    }
    public static void saveCurrentSessions(String name, List<SessionData> sessions) {
        playerSessions.put(name, sessions);
        Path file = file(name), temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(temp)) { GSON.toJson(sessions, writer); }
            try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException e) { ShortcutTerminal.LOGGER.error("Failed to save terminal sessions", e); }
    }
    public static class SessionData {
        public transient int index;
        public String playerName, currentPath = "/", draft = "";
        public List<String> outputLines = new ArrayList<>(), commandHistory = new ArrayList<>();
        public boolean booted;
        public transient boolean busy;
        public transient ClientCommandExecutor executor;
        public transient List<String> bootLines;
        public transient int bootIndex;
        public transient long nextBootMs;
        public SessionData() {}
        public SessionData(String name) { playerName = name; }
        public ClientCommandExecutor createExecutor() {
            if (executor == null) {
                executor = new ClientCommandExecutor(playerName);
                executor.setCurrentPath(currentPath); executor.setCommandHistory(commandHistory);
            }
            return executor;
        }
        public void updateFromExecutor(ClientCommandExecutor value, List<String> lines) {
            currentPath = value.getCurrentPath(); commandHistory = new ArrayList<>(value.getCommandHistory());
            if (lines != outputLines) outputLines = new ArrayList<>(lines);
        }
    }
}
