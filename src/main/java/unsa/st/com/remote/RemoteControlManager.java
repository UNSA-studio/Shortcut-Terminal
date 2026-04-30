package unsa.st.com.remote;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import unsa.st.com.ShortcutTerminal;
import unsa.st.com.core.CoreCommandExecutor;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RemoteControlManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
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
            // 单人模式下不初始化远程控制
            ShortcutTerminal.LOGGER.info("Remote control is only available on dedicated servers.");
            return;
        }

        dataFile = server.getServerDirectory().resolve("st_remote.json");
        loadData();
        generateRID(server);
        startHttpServer();
    }

    private static void generateRID(MinecraftServer server) {
        try {
            String ip = InetAddress.getLocalHost().getHostAddress();
            String hostname = InetAddress.getLocalHost().getHostName();
            String baseInfo = ip + "|" + hostname + "|" + server.getPort();
            String base64 = Base64.getEncoder().encodeToString(baseInfo.getBytes());
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
        return "Account updated.";
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
            ShortcutTerminal.LOGGER.info("Remote HTTP server started on port {}", HTTP_PORT);
        } catch (IOException e) {
            ShortcutTerminal.LOGGER.error("Failed to start HTTP server", e);
        }
    }

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
            RemoteData data = GSON.fromJson(json, RemoteData.class);
            if (data != null) {
                usedRCIDs.addAll(data.usedRCIDs);
                if (data.accountName != null) accountName = data.accountName;
                if (data.accountPassword != null) accountPassword = data.accountPassword;
            }
        } catch (IOException e) {}
    }

    private static void saveData() {
        if (dataFile == null) return;
        try {
            RemoteData data = new RemoteData();
            data.usedRCIDs = new ArrayList<>(usedRCIDs);
            data.accountName = accountName;
            data.accountPassword = accountPassword;
            Files.writeString(dataFile, GSON.toJson(data));
        } catch (IOException e) {}
    }

    static class RemoteData {
        List<String> usedRCIDs = new ArrayList<>();
        String accountName = "Admin";
        String accountPassword = "12345678";
    }

    // ============ HTTP 处理器 ============
    static class RidHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            addCors(exchange);
            String response = getRID();
            exchange.sendResponseHeaders(200, response.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
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
            Map<String, String> data = GSON.fromJson(body, Map.class);
            String rcid = data.get("rcid");
            String result = authenticateRCID(rcid);
            exchange.sendResponseHeaders(200, result.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(result.getBytes());
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
            Map<String, String> data = GSON.fromJson(body, Map.class);
            String username = data.get("username");
            String password = data.get("password");
            if (verifyAccount(username, password)) {
                String token = generateRandomString(32);
                sessions.put(token, new SessionInfo(username, System.currentTimeMillis() + 86400000L));
                String resp = GSON.toJson(Map.of("session_token", token));
                exchange.sendResponseHeaders(200, resp.getBytes().length);
                OutputStream os = exchange.getResponseBody();
                os.write(resp.getBytes());
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
            Map<String, String> data = GSON.fromJson(body, Map.class);
            String token = data.get("session_token");
            String command = data.get("command");
            SessionInfo session = sessions.get(token);
            if (session == null || session.expiresAt < System.currentTimeMillis()) {
                exchange.sendResponseHeaders(403, -1);
                return;
            }
            String result = executeRemoteCommand(command);
            String resp = GSON.toJson(Map.of("result", result));
            exchange.sendResponseHeaders(200, resp.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(resp.getBytes());
            os.close();
        }
    }

    private static void addCors(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }
}
