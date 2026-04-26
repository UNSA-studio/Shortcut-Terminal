package unsa.st.com.remote;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import unsa.st.com.ShortcutTerminal;
import unsa.st.com.core.CoreCommandExecutor;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RemoteControlManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static String currentRID = null;
    private static final Set<String> usedRCIDs = new HashSet<>();
    private static final Map<String, RemoteSession> activeSessions = new ConcurrentHashMap<>();
    private static String accountName = "Admin";
    private static String accountPassword = "12345678";
    private static ServerSocket serverSocket;
    private static boolean running = false;
    private static Path dataFile;

    public static void init() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            dataFile = server.getServerDirectory().resolve("st_remote.json");
            loadData();
            generateRID(server);
        }
    }

    private static void generateRID(MinecraftServer server) {
        try {
            String ip = InetAddress.getLocalHost().getHostAddress();
            String hostname = InetAddress.getLocalHost().getHostName();
            String baseInfo = ip + "|" + hostname + "|" + server.getPort();
            String base64 = Base64.getEncoder().encodeToString(baseInfo.getBytes());
            String random = generateRandomString(5);
            currentRID = base64 + "-" + random;
            ShortcutTerminal.LOGGER.info("Remote ID generated: {}", currentRID);
        } catch (Exception e) {
            currentRID = "ERROR-" + generateRandomString(5);
        }
    }

    public static String getRID() {
        return currentRID != null ? currentRID : "RID not available. Server not started.";
    }

    public static String authenticateRCID(String rcid) {
        if (rcid == null || rcid.isEmpty()) return "Invalid RCID.";
        if (usedRCIDs.contains(rcid)) return "Expired RCID.";
        usedRCIDs.add(rcid);
        saveData();
        return "RCID authenticated successfully.";
    }

    public static boolean verifyAccount(String name, String password) {
        return accountName.equals(name) && accountPassword.equals(password);
    }

    public static String setAccount(String name, String password) {
        accountName = name;
        accountPassword = password;
        saveData();
        return "Account updated.";
    }

    public static String executeRemoteCommand(UUID playerUUID, String command) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return "Server not available.";

        ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);
        if (player == null) {
            player = server.getPlayerList().getPlayerByName("UNSA");
        }
        if (player == null) return "No player available to execute command.";

        CoreCommandExecutor executor = new CoreCommandExecutor(false);
        executor.setPlayer(player);
        String[] parts = command.trim().split("\\s+");
        String cmd = parts[0];
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);
        return executor.execute(cmd, args);
    }

    private static String generateRandomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        Random rng = new Random();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(rng.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private static void loadData() {
        if (dataFile == null || !Files.exists(dataFile)) return;
        try {
            String json = Files.readString(dataFile);
            RemoteData data = GSON.fromJson(json, RemoteData.class);
            if (data != null) {
                usedRCIDs.addAll(data.usedRCIDs);
                if (data.accountName != null) accountName = data.accountName;
                if (data.accountPassword != null) accountPassword = data.accountPassword;
            }
        } catch (IOException e) {
            ShortcutTerminal.LOGGER.error("Failed to load remote data", e);
        }
    }

    private static void saveData() {
        if (dataFile == null) return;
        try {
            RemoteData data = new RemoteData();
            data.usedRCIDs = new ArrayList<>(usedRCIDs);
            data.accountName = accountName;
            data.accountPassword = accountPassword;
            Files.writeString(dataFile, GSON.toJson(data));
        } catch (IOException e) {
            ShortcutTerminal.LOGGER.error("Failed to save remote data", e);
        }
    }

    static class RemoteData {
        List<String> usedRCIDs = new ArrayList<>();
        String accountName;
        String accountPassword;
    }

    static class RemoteSession {
        UUID playerUUID;
        long connectedAt;
        RemoteSession(UUID uuid) {
            this.playerUUID = uuid;
            this.connectedAt = System.currentTimeMillis();
        }
    }
}
