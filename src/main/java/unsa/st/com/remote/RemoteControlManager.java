package unsa.st.com.remote;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import unsa.st.com.ShortcutTerminal;
import unsa.st.com.core.CoreCommandExecutor;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RemoteControlManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Base64.Encoder B64_ENCODER = Base64.getEncoder();
    private static final Base64.Decoder B64_DECODER = Base64.getDecoder();
    private static final String DATA_FILE_NAME = "Remote control data.bef";

    private static String currentRID = null;
    private static final Set<String> usedRCIDs = new HashSet<>();
    private static String accountName = "Admin";
    private static String accountPassword = "12345678";
    private static Path dataFile;
    private static HttpServer httpServer;
    private static final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();
    private static final int HTTP_PORT = 8080;

    static class SessionInfo {
        String username;
        long expiresAt;
        SessionInfo(String username, long expiresAt) {
            this.username = username;
            this.expiresAt = expiresAt;
        }
    }

    public static void init() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isDedicatedServer()) {
            ShortcutTerminal.LOGGER.info("Remote control is only available on dedicated servers.");
            return;
        }
        dataFile = server.getServerDirectory().resolve(DATA_FILE_NAME);
        loadData();
        generateRID(server);
        startHttpServer();
    }

    private static void generateRID(MinecraftServer server) {
        try {
            String ip = InetAddress.getLocalHost().getHostAddress();
            String hostname = InetAddress.getLocalHost().getHostName();
            String baseInfo = ip + "|" + hostname;
            String base64 = B64_ENCODER.encodeToString(baseInfo.getBytes(StandardCharsets.UTF_8));
            String random = generateRandomString(5);
            currentRID = base64 + "-" + random;
            ShortcutTerminal.LOGGER.info("Remote ID: {}", currentRID);
        } catch (Exception e) {
            currentRID = "ERROR-" + generateRandomString(5);
        }
    }

    public static String getRID() {
        return currentRID != null ? currentRID : "RID not available. Remote control is disabled in singleplayer.";
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
        return "Account updated. Data saved to " + DATA_FILE_NAME;
    }

    public static String executeRemoteCommand(String command) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return "Server not available.";
        ServerPlayer player = null;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            player = p;
            break;
        }
        if (player == null) return "No player online.";
        CoreCommandExecutor executor = new CoreCommandExecutor(false);
        executor.setPlayer(player);
        String[] parts = command.trim().split("\\s+");
        String cmd = parts[0];
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);
        return executor.execute(cmd, args);
    }

    private static void startHttpServer() {
        try {
            httpServer = HttpServer.create(new InetSocketAddress(HTTP_PORT), 0);
            httpServer.createContext("/st/rid", new RidHandler());
            httpServer.createContext("/st/rcid", new RcidHandler());
            httpServer.createContext("/st/auth", new AuthHandler());
            httpServer.createContext("/st/execute", new ExecuteHandler());
            httpServer.setExecutor(null);
            httpServer.start();
            // Periodic cleanup of expired sessions to prevent memory leak
            scheduler.scheduleAtFixedRate(() -> {
                long now = System.currentTimeMillis();
                sessions.entrySet().removeIf(e -> e.getValue().expiresAt < now);
            }, 10, 10, TimeUnit.MINUTES);
            ShortcutTerminal.LOGGER.info("Remote HTTP server started on port {}", HTTP_PORT);
        } catch (IOException e) {
            ShortcutTerminal.LOGGER.error("Failed to start HTTP server", e);
        }
    }

    private static final java.util.concurrent.ScheduledExecutorService scheduler =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ST-RemoteControl-Cleanup");
                t.setDaemon(true);
                return t;
            });

    private static String generateRandomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom rng = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) sb.append(chars.charAt(rng.nextInt(chars.length())));
        return sb.toString();
    }

    private static void loadData() {
        if (dataFile == null || !Files.exists(dataFile)) return;
        try {
            String json = Files.readString(dataFile);
            BefData data = GSON.fromJson(json, BefData.class);
            if (data != null) {
                usedRCIDs.addAll(data.usedRCIDs);
                if (data.accountName != null && !data.accountName.isEmpty()) {
                    accountName = new String(B64_DECODER.decode(data.accountName), StandardCharsets.UTF_8);
                }
                if (data.accountPassword != null && !data.accountPassword.isEmpty()) {
                    accountPassword = new String(B64_DECODER.decode(data.accountPassword), StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            ShortcutTerminal.LOGGER.warn("Failed to load remote data, using defaults", e);
        }
    }

    private static void saveData() {
        if (dataFile == null) return;
        try {
            BefData data = new BefData();
            data.usedRCIDs = new ArrayList<>(usedRCIDs);
            data.accountName = B64_ENCODER.encodeToString(accountName.getBytes(StandardCharsets.UTF_8));
            data.accountPassword = B64_ENCODER.encodeToString(accountPassword.getBytes(StandardCharsets.UTF_8));
            Files.writeString(dataFile, GSON.toJson(data));
        } catch (IOException e) {
            ShortcutTerminal.LOGGER.error("Failed to save remote data", e);
        }
    }

    static class BefData {
        List<String> usedRCIDs = new ArrayList<>();
        String accountName = "";
        String accountPassword = "";
    }

    static class RidHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            addCors(exchange);
            String response = getRID();
            byte[] bytes = response.getBytes();
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }

    static class RcidHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            addCors(exchange);
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            String body = new String(exchange.getRequestBody().readAllBytes());
            Map<String, String> mapData = GSON.fromJson(body, Map.class);
            String rcid = mapData.get("rcid");
            String result = authenticateRCID(rcid);
            byte[] bytes = result.getBytes();
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }

    static class AuthHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            addCors(exchange);
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            String body = new String(exchange.getRequestBody().readAllBytes());
            Map<String, String> mapData = GSON.fromJson(body, Map.class);
            String username = mapData.get("username");
            String password = mapData.get("password");
            if (verifyAccount(username, password)) {
                String token = generateRandomString(32);
                sessions.put(token, new SessionInfo(username, System.currentTimeMillis() + 86400000L));
                String resp = GSON.toJson(Map.of("session_token", token));
                byte[] bytes = resp.getBytes();
                exchange.sendResponseHeaders(200, bytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(bytes);
                os.close();
            } else {
                exchange.sendResponseHeaders(401, -1);
            }
        }
    }

    static class ExecuteHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            addCors(exchange);
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            String body = new String(exchange.getRequestBody().readAllBytes());
            Map<String, String> mapData = GSON.fromJson(body, Map.class);
            String token = mapData.get("session_token");
            String command = mapData.get("command");
            SessionInfo session = sessions.get(token);
            if (session == null || session.expiresAt < System.currentTimeMillis()) {
                exchange.sendResponseHeaders(403, -1);
                return;
            }
            String result = executeRemoteCommand(command);
            String resp = GSON.toJson(Map.of("result", result));
            byte[] bytes = resp.getBytes();
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }

    private static void addCors(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }
}