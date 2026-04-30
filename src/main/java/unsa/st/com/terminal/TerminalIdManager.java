package unsa.st.com.terminal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import unsa.st.com.ShortcutTerminal;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.*;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.*;

public class TerminalIdManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, TerminalEntry> terminals = new HashMap<>();
    private static final Set<String> usedIDs = new HashSet<>();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static Path dataFile;
    private static boolean initialized = false;

    public static void init() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            dataFile = server.getServerDirectory().resolve("st_terminals.json");
            load();
            initialized = true;
        }
    }

    public static String generateTID() {
        String tid;
        do {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                if (i > 0) sb.append('-');
                for (int j = 0; j < 4; j++) {
                    sb.append(Integer.toHexString(RANDOM.nextInt(16)));
                }
            }
            tid = sb.toString();
        } while (usedIDs.contains(tid));
        usedIDs.add(tid);
        return tid;
    }

    public static void registerTerminal(String tid, String ownerName, UUID ownerUUID) {
        TerminalEntry entry = new TerminalEntry(tid, ownerName, ownerUUID.toString());
        terminals.put(tid, entry);
        save();
    }

    public static void unregisterTerminal(String tid) {
        terminals.remove(tid);
        usedIDs.remove(tid);
        save();
    }

    public static String listAllTerminals() {
        if (terminals.isEmpty()) return "No terminals registered.";

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-20s %-20s\n", "TID", "Owner"));
        sb.append("-".repeat(50)).append("\n");
        for (TerminalEntry t : terminals.values()) {
            sb.append(String.format("%-20s %-20s\n", t.tid, t.ownerName));
        }
        sb.append("\nTotal: ").append(terminals.size()).append(" terminals");
        return sb.toString();
    }

    private static void load() {
        if (dataFile == null || !dataFile.toFile().exists()) return;
        try (Reader reader = new FileReader(dataFile.toFile())) {
            Map<String, TerminalEntry> loaded = GSON.fromJson(reader, new TypeToken<Map<String, TerminalEntry>>(){}.getType());
            if (loaded != null) {
                terminals.putAll(loaded);
                usedIDs.addAll(loaded.keySet());
            }
        } catch (IOException e) {
            ShortcutTerminal.LOGGER.error("Failed to load terminal IDs", e);
        }
    }

    private static void save() {
        if (dataFile == null) return;
        try (Writer writer = new FileWriter(dataFile.toFile())) {
            GSON.toJson(terminals, writer);
        } catch (IOException e) {
            ShortcutTerminal.LOGGER.error("Failed to save terminal IDs", e);
        }
    }

    static class TerminalEntry {
        String tid;
        String ownerName;
        String ownerUUID;

        TerminalEntry(String tid, String ownerName, String ownerUUID) {
            this.tid = tid;
            this.ownerName = ownerName;
            this.ownerUUID = ownerUUID;
        }
    }
}
