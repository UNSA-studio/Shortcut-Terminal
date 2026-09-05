package unsa.st.com.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import unsa.st.com.api.ShortcutTerminalAPI;
import unsa.st.com.compute.ComputePolicy;
import unsa.st.com.core.CoreToolCommands;
import unsa.st.com.gui.TerminalScreen;
import unsa.st.com.pkg.PkgManager;
import unsa.st.com.ShortcutTerminal;
import unsa.st.com.network.ModNetwork;
import unsa.st.com.network.BlackScreenPayload;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ClientCommandExecutor {
    private final String playerName;
    private UUID playerUuid;
    private String currentPath = "/";
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final List<String> outputBuffer = new ArrayList<>();
    private List<String> commandHistory = new ArrayList<>();
    private boolean pendingChanges = false;
    private volatile boolean wingetRunning = false;

    public ClientCommandExecutor(String playerName) {
        this.playerName = playerName;
        if (Minecraft.getInstance().player != null) {
            this.playerUuid = Minecraft.getInstance().player.getUUID();
        }
    }

    public String getCurrentPath() { return currentPath; }
    public void setCurrentPath(String path) { this.currentPath = path; }
    public List<String> getCommandHistory() { return commandHistory; }
    public void setCommandHistory(List<String> history) { this.commandHistory = new ArrayList<>(history); }
    public void addCommandToHistory(String cmd) { commandHistory.add(cmd); pendingChanges = true; }
    public boolean hasPendingChanges() { return pendingChanges; }
    public void clearPendingChanges() { pendingChanges = false; }
    public UUID getPlayerUuid() { return playerUuid; }
    public String getPlayerName() { return playerName; }

    public String execute(String command, String[] args) {
        // ===== STOS 算力门槛：面板等级不足时拒绝并冷却 =====
        int installedLevel = stosLevel();
        if (ComputePolicy.isGated(command, installedLevel)) {
            long now = System.currentTimeMillis();
            if (now - lastGateRejectMs < ComputePolicy.GATE_COOLDOWN_MS) {
                return "STOS: compute unit cooling down...";
            }
            lastGateRejectMs = now;
            String panic = ComputePolicy.maybePanic(installedLevel, command, new java.util.Random());
            String gate = "STOS: compute insufficient (needs L" + ComputePolicy.requiredLevel(command)
                    + ", installed " + (installedLevel <= 0 ? "none" : "L" + installedLevel) + ")";
            return panic != null ? panic + "\n" + gate : gate;
        }

        String result = executeBuiltInCommand(command, args);
        if (result != null) return result;
        // 附属命令兜底：处理器抛异常不能拖垮终端
        String addon;
        try {
            addon = ShortcutTerminalAPI.dispatchCommand(command, args);
        } catch (Throwable t) {
            ShortcutTerminal.LOGGER.error("Addon command '{}' threw", command, t);
            addon = "Error: addon command '" + command + "' threw " + t.getClass().getSimpleName();
        }
        if (addon != null) return addon;
        Path ext = findExecutableInPath(command);
        if (ext != null) return executeExternalProgram(ext, args);
        return "Error: Unknown command. Type 'help' for available commands.";
    }

    /** 当前面板的处理器等级（从打开终端的玩家主手/副手查找面板）。 */
    private int stosLevel() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        for (net.minecraft.world.item.ItemStack held : mc.player.getHandSlots()) {
            if (held.getItem() instanceof unsa.st.com.item.TerminalPanelItem) {
                return unsa.st.com.compute.ProcessorCapability.getInstalledLevel(held);
            }
        }
        // 也扫描主背包 36 格（面板可能没拿在手上）
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.items.size(); i++) {
            net.minecraft.world.item.ItemStack s = inv.items.get(i);
            if (s.getItem() instanceof unsa.st.com.item.TerminalPanelItem) {
                return unsa.st.com.compute.ProcessorCapability.getInstalledLevel(s);
            }
        }
        return 0;
    }

    private long lastGateRejectMs = 0;

    private String executeBuiltInCommand(String command, String[] args) {
        switch (command.toLowerCase(Locale.ROOT)) {
            case "help": return getHelp();
            case "ls": return executeLs();
            case "mkdir": return executeMkdir(args);
            case "touch": return executeTouch(args);
            case "rm": return executeRm(args);
            case "cat": return executeCat(args);
            case "echo": return executeEcho(args);
            case "cd": return executeCd(args);
            case "pwd": return executePwd();
            case "clear": return "";
            case "pkg": return executePkg(args);
            case "winget": return executeWinget(args);
            case "addons": return CoreToolCommands.addons();
            case "run": return executeRun(args);
            default: return null;
        }
    }

    private Path findExecutableInPath(String command) {
        Path pathFile = PkgManager.getPathFile(true);
        if (!Files.exists(pathFile)) return null;
        try {
            List<String> lines = Files.readAllLines(pathFile);
            for (String line : lines) {
                String[] parts = line.split(" - ");
                if (parts.length == 2 && parts[0].equals(command)) {
                    return Paths.get(parts[1]);
                }
            }
        } catch (IOException e) { ShortcutTerminal.LOGGER.error("PATH read error", e); }
        return null;
    }

    private String executeExternalProgram(Path programPath, String[] args) {
        return "External execution not fully implemented yet.";
    }

    private String getHelp() {
        StringBuilder sb = new StringBuilder("Available: ls, mkdir, touch, rm, cat, echo, cd, pwd, clear, pkg, winget, addons, run spoof");
        Map<String, String> addon = ShortcutTerminalAPI.commandInfoSnapshot();
        if (!addon.isEmpty()) {
            sb.append("\nAddon commands:");
            for (Map.Entry<String, String> e : addon.entrySet()) {
                sb.append("\n  ").append(e.getKey()).append(e.getValue().isEmpty() ? "" : " - " + e.getValue());
            }
        }
        return sb.toString();
    }

    private String executeLs() {
        List<String> files = ClientVirtualFileSystem.listDirectory(playerName, currentPath);
        if (files == null) return "Error: Directory not found.";
        return String.join("  ", files);
    }

    private String executeMkdir(String[] args) {
        if (args.length == 0) return "Usage: mkdir <directory>";
        boolean ok = ClientVirtualFileSystem.createDirectory(playerName, currentPath, args[0]);
        return ok ? "Directory created." : "Error: Failed to create directory.";
    }

    private String executeTouch(String[] args) {
        if (args.length == 0) return "Usage: touch <file>";
        boolean ok = ClientVirtualFileSystem.createFile(playerName, currentPath, args[0]);
        return ok ? "File created." : "Error: Failed to create file.";
    }

    private String executeRm(String[] args) {
        if (args.length == 0) return "Usage: rm [-r] <name>";
        boolean recursive = args[0].equals("-r");
        String target = recursive ? (args.length > 1 ? args[1] : "") : args[0];
        if (target.isEmpty()) return "Invalid target.";
        boolean ok = ClientVirtualFileSystem.delete(playerName, currentPath, target, recursive);
        return ok ? "Deleted." : "Error: Failed to delete.";
    }

    private String executeCat(String[] args) {
        if (args.length == 0) return "Usage: cat <file>";
        String content = ClientVirtualFileSystem.readFile(playerName, currentPath, args[0]);
        return content != null ? content : "Error: File not found.";
    }

    private String executeEcho(String[] args) { return String.join(" ", args); }

    private String executeCd(String[] args) {
        // No argument (or bare "cd ~") → go home. Any real path must win over this.
        if (args.length == 0 || args[0].trim().isEmpty() || args[0].trim().equals("~")) {
            currentPath = "/";
            return "Changed directory to: /";
        }
        String newPath = ClientVirtualFileSystem.normalizePath(currentPath, args[0]);
        if (ClientVirtualFileSystem.listDirectory(playerName, newPath) != null) {
            currentPath = newPath;
            return "Changed directory to: " + (currentPath.isEmpty() ? "/" : currentPath);
        }
        return "Error: Directory not found.";
    }

    private String executePwd() { return currentPath.isEmpty() ? "/" : currentPath; }

    private String executePkg(String[] args) {
        if (args.length == 0) return PkgManager.getHelp();
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "update": return PkgManager.updateIndex();
            case "search": return args.length > 1 ? String.join("\n", PkgManager.search(args[1])) : "Usage: pkg search <keyword>";
            case "install": return args.length > 1 ? PkgManager.install(args[1], true) : "Usage: pkg install <package>";
            case "remove": return args.length > 1 ? PkgManager.remove(args[1], true) : "Usage: pkg remove <package>";
            case "list": return String.join("\n", PkgManager.listInstalled(true));
            case "show": return args.length > 1 ? PkgManager.showInfo(args[1]) : "Usage: pkg show <package>";
            default: return "Unknown pkg command.";
        }
    }

    // ========== WINGET (real winget.exe passthrough) ==========
    private String executeWinget(String[] args) {
        if (!isRealWindows()) {
            return "Error: winget is not available on this host (" + detectHostOS() + ").\n" +
                   "winget passthrough directly invokes the host's winget.exe (Windows 10 1809+ / Windows 11 with App Installer).";
        }
        if (wingetRunning) return "Error: another winget process is already running.";
        if (args.length == 0) {
            return "Usage: winget <command> [args...]\n" +
                   "Passthrough to the host's real winget.exe. Examples:\n" +
                   "  winget search <query>    - search for apps\n" +
                   "  winget install <pkg>     - install an app\n" +
                   "  winget uninstall <pkg>   - uninstall an app\n" +
                   "  winget list              - list installed apps\n" +
                   "  winget show <pkg>        - show app details\n" +
                   "  winget upgrade [pkg]     - upgrade app(s)\n" +
                   "  winget --info            - winget info";
        }
        // Do not block the render thread: winget install can run for minutes.
        wingetRunning = true;
        final List<String> cmd = buildWingetCommand(args);
        Thread worker = new Thread(() -> {
            String output;
            try {
                output = runWingetProcess(cmd);
            } catch (Exception e) {
                ShortcutTerminal.LOGGER.error("winget passthrough failed", e);
                output = "Error: " + e.getMessage();
            }
            final String result = output;
            wingetRunning = false;
            Minecraft.getInstance().execute(() -> {
                TerminalScreen screen = TerminalScreen.getInstance();
                if (screen != null) screen.addOutputLine(result);
            });
        }, "ShortcutTerminal-Winget");
        worker.setDaemon(true);
        worker.start();
        return "winget is running in background (" + cmd.get(1) + ")... output will appear here when finished.";
    }

    private boolean isRealWindows() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win");
    }

    private String detectHostOS() {
        return System.getProperty("os.name", "unknown") + " " + System.getProperty("os.arch", "");
    }

    private List<String> buildWingetCommand(String[] args) {
        List<String> cmd = new ArrayList<>();
        cmd.add("winget.exe");
        for (String a : args) cmd.add(a);
        // Non-interactive install/upgrade/uninstall: auto-accept agreements so the
        // passthrough never blocks on the agreement prompt (stdin is closed in a GUI env).
        String sub = args[0].toLowerCase(Locale.ROOT);
        boolean mutating = sub.equals("install") || sub.equals("upgrade") || sub.equals("uninstall") || sub.equals("add");
        if (mutating) {
            boolean hasAccept = false, hasSrcAccept = false;
            for (String a : args) {
                String l = a.toLowerCase(Locale.ROOT);
                if (l.startsWith("--accept-package-agreements")) hasAccept = true;
                if (l.startsWith("--accept-source-agreements")) hasSrcAccept = true;
            }
            if (!hasAccept) cmd.add("--accept-package-agreements");
            if (!hasSrcAccept) cmd.add("--accept-source-agreements");
        }
        return cmd;
    }

    private String runWingetProcess(List<String> cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        try { proc.getOutputStream().close(); } catch (IOException ignored) {} // stdin EOF: no interactive prompt hang

        // Charset: winget pipes out in the host console codepage (GBK on zh-CN Windows).
        // Override with -Dst.winget.charset=UTF-8 if needed.
        Charset cs;
        String override = System.getProperty("st.winget.charset");
        if (override != null && !override.isEmpty()) {
            try { cs = Charset.forName(override); } catch (Exception e) { cs = Charset.defaultCharset(); }
        } else {
            cs = Charset.defaultCharset();
        }

        // Watchdog: if winget hangs (e.g. blocked on a UAC prompt), the reader thread
        // would block on readLine() forever and the wingetRunning flag would deadlock
        // every future winget call. Kill the process after the timeout so readLine()
        // gets EOF and the worker always terminates. (proc is effectively final,
        // so the watchdog lambda can capture it directly.)
        final java.util.concurrent.atomic.AtomicBoolean timedOut = new java.util.concurrent.atomic.AtomicBoolean(false);
        Thread watchdog = new Thread(() -> {
            try {
                if (!proc.waitFor(10, TimeUnit.MINUTES)) {
                    proc.destroyForcibly();
                    timedOut.set(true);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }, "ShortcutTerminal-Winget-Watchdog");
        watchdog.setDaemon(true);
        watchdog.start();

        List<String> lines = new ArrayList<>();
        String pendingProgress = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), cs))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (isProgressNoise(line)) {
                    pendingProgress = line; // collapse progress-bar spam, keep only the last frame
                    continue;
                }
                if (pendingProgress != null) { lines.add(pendingProgress); pendingProgress = null; }
                lines.add(line);
                if (lines.size() > 800) { lines.add("...[output truncated]"); break; }
            }
        }
        if (pendingProgress != null) lines.add(pendingProgress);

        // Read EOF reached: process exited (or was killed by the watchdog). Use
        // waitFor() instead of exitValue() - exitValue() throws
        // IllegalThreadStateException if the process is somehow not yet dead.
        proc.waitFor();
        int code = proc.exitValue();
        StringBuilder out = new StringBuilder();
        if (timedOut.get()) out.append("Error: winget timed out after 10 minutes and was killed.\n");
        out.append("[winget exit code: ").append(code).append(']');
        for (String l : lines) out.append('\n').append(l);
        return out.toString();
    }

    /** Progress-bar frames like "\  ▒▒▒░░ 45%" — charset is only box/percent/spinner chars. */
    private boolean isProgressNoise(String line) {
        if (line == null || line.isEmpty()) return false;
        boolean hasProgressChar = false;
        for (char c : line.toCharArray()) {
            if (Character.isWhitespace(c)) continue;
            if ("-\\|/─━═0123456789%.: ".indexOf(c) >= 0 || c >= 0x2580 && c <= 0x259F || c >= 0x2596 && c <= 0x259F) {
                hasProgressChar = true;
                continue;
            }
            return false; // any real letter/CJK char means it's a content line
        }
        return hasProgressChar;
    }

    // ========== RUN ==========
    private String executeRun(String[] args) {
        if (args.length == 0) return "Usage: run <module> [args...]";
        String module = args[0].toLowerCase(Locale.ROOT);
        String[] moduleArgs = Arrays.copyOfRange(args, 1, args.length);
        switch (module) {
            case "spoof": return executeSpoof(moduleArgs);
            default: {
                // 附属 run 模块兜底
                String r;
                try {
                    r = ShortcutTerminalAPI.dispatchModule(module, moduleArgs);
                } catch (Throwable t) {
                    ShortcutTerminal.LOGGER.error("Addon module '{}' threw", module, t);
                    r = "Error: addon module '" + module + "' threw " + t.getClass().getSimpleName();
                }
                return r != null ? r : "Unknown run module: " + module;
            }
        }
    }

    // ========== SPOOF ==========
    private String executeSpoof(String[] args) {
        if (args.length == 0) return "Usage: run spoof <action> [player] [parameters...]";
        String action = args[0].toLowerCase(Locale.ROOT);
        String targetPlayer = args.length > 1 && !args[1].contains("-") ? args[1] : playerName;
        String[] params = targetPlayer.equals(playerName) && args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : (args.length > 2 ? Arrays.copyOfRange(args, 2, args.length) : new String[0]);
        ServerPlayer target = getServerPlayer(targetPlayer);
        if (target == null) return "Player not found: " + targetPlayer;
        Map<String, String> paramMap = parseParams(params);
        switch (action) {
            case "ray": return spoofRay(target, paramMap);
            case "creeper": return spoofCreeper(target, paramMap);
            case "flyup": return spoofFlyup(target, paramMap);
            case "evasiveground": return spoofEvasiveGround(target, paramMap);
            case "stop": return spoofStop(target, paramMap);
            case "quickly": return spoofQuickly(target, paramMap);
            case "tortoise": return spoofTortoise(target, paramMap);
            case "blackscreen": return spoofBlackscreen(target, paramMap);
            default: return "Unknown spoof action: " + action;
        }
    }

    private Map<String, String> parseParams(String[] args) {
        Map<String, String> map = new HashMap<>();
        for (String a : args) { int d = a.indexOf('-'); if (d>0) map.put(a.substring(0,d).toLowerCase(), a.substring(d+1)); }
        return map;
    }

    private int getIntParam(Map<String, String> p, String k, int def) {
        try { return Integer.parseInt(p.getOrDefault(k, String.valueOf(def))); } catch (NumberFormatException e) { return def; }
    }

    private float getFloatParam(Map<String, String> p, String k, float def) {
        try { return Float.parseFloat(p.getOrDefault(k, String.valueOf(def))); } catch (NumberFormatException e) { return def; }
    }

    private long parseTimeMs(String t, long defSec) {
        if(t==null||t.isEmpty()) return defSec*1000;
        t=t.toLowerCase();
        try {
            if(t.endsWith("ms")) return Long.parseLong(t.replace("ms",""));
            if(t.endsWith("s")) return Long.parseLong(t.replace("s",""))*1000;
            if(t.endsWith("m")) return Long.parseLong(t.replace("m",""))*60000;
            if(t.endsWith("h")) return Long.parseLong(t.replace("h",""))*3600000;
            return Long.parseLong(t)*1000;
        } catch (NumberFormatException e) { return defSec*1000; }
    }

    private ServerPlayer getServerPlayer(String name) {
        if (Minecraft.getInstance().hasSingleplayerServer()) {
            return Minecraft.getInstance().getSingleplayerServer().getPlayerList().getPlayerByName(name);
        }
        return null;
    }

    private String spoofRay(ServerPlayer target, Map<String, String> p) {
        String fi = p.get("fi");
        if (fi != null) {
            String[] parts = fi.split("-");
            if (parts.length == 2) {
                int count = Integer.parseInt(parts[0]);
                long interval = parseTimeMs(parts[1], 0);
                float dmg = getFloatParam(p, "injure", 5);
                for (int i = 0; i < count; i++) {
                    scheduler.schedule(() -> {
                        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(target.level());
                        if (bolt != null) {
                            bolt.setPos(Vec3.atBottomCenterOf(target.blockPosition()));
                            bolt.setCause(target);
                            target.level().addFreshEntity(bolt);
                            target.hurt(target.damageSources().lightningBolt(), dmg);
                        }
                    }, i * interval, TimeUnit.MILLISECONDS);
                }
                return "Scheduled " + count + " lightning strikes.";
            }
            return "Invalid fi format.";
        }
        int q = getIntParam(p, "quantity", 1);
        float dmg = getFloatParam(p, "injure", 5);
        Level lvl = target.level();
        BlockPos pos = target.blockPosition();
        for (int i = 0; i < q; i++) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(lvl);
            if (bolt != null) {
                bolt.setPos(Vec3.atBottomCenterOf(pos));
                bolt.setCause(target);
                lvl.addFreshEntity(bolt);
            }
        }
        target.hurt(target.damageSources().lightningBolt(), dmg);
        return "Ray done.";
    }

    private String spoofCreeper(ServerPlayer target, Map<String, String> p) {
        int q = Math.min(getIntParam(p, "quantity", 1), 64);
        boolean charged = "lightning".equalsIgnoreCase(p.get("morphology"));
        String timeStr = p.get("time");
        Level lvl = target.level();
        BlockPos pos = target.blockPosition();
        for (int i = 0; i < q; i++) {
            Creeper creeper = EntityType.CREEPER.create(lvl);
            if (creeper != null) {
                creeper.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                if (charged) { try { Creeper.class.getMethod("setPowered", boolean.class).invoke(creeper, true); } catch (Exception ignored) {} }
                lvl.addFreshEntity(creeper);
                if ("moment".equalsIgnoreCase(timeStr)) { creeper.ignite(); }
                else if (timeStr != null && !timeStr.isEmpty()) { scheduler.schedule(creeper::ignite, parseTimeMs(timeStr, 0), TimeUnit.MILLISECONDS); }
            }
        }
        return "Creeper done.";
    }

    private String spoofFlyup(ServerPlayer target, Map<String, String> p) {
        Vec3 dest;
        if (p.containsKey("coordinates")) { String[] parts = p.get("coordinates").split(","); dest = new Vec3(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2])); }
        else { double height = 100;
        for (String key : p.keySet()) {
            if (key.matches("\\d+\\.?\\d*")) {
                try { height = Double.parseDouble(key); break; } catch (NumberFormatException ignored) {}
            }
        }
        if (p.containsKey("height")) try { height = Double.parseDouble(p.get("height")); } catch (NumberFormatException ignored) {}
        dest = target.position().add(0, height, 0); }
        target.teleportTo(dest.x, dest.y, dest.z);
        if ("no".equalsIgnoreCase(p.get("injure"))) target.fallDistance = 0;
        return "Teleported " + target.getName().getString();
    }

    private String spoofEvasiveGround(ServerPlayer target, Map<String, String> p) {
        Vec3 dest;
        if (p.containsKey("coordinates")) { String[] parts = p.get("coordinates").split(","); dest = new Vec3(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2])); }
        else { double depth = 10;
        for (String key : p.keySet()) {
            if (key.matches("\\d+\\.?\\d*")) {
                try { depth = Double.parseDouble(key); break; } catch (NumberFormatException ignored) {}
            }
        }
        if (p.containsKey("depth")) try { depth = Double.parseDouble(p.get("depth")); } catch (NumberFormatException ignored) {}
        dest = target.position().add(0, -depth, 0); }
        target.teleportTo(dest.x, dest.y, dest.z);
        if ("yes".equalsIgnoreCase(p.get("injure"))) target.hurt(target.damageSources().inWall(), 2.0f);
        return "Burrowed " + target.getName().getString();
    }

    private String spoofStop(ServerPlayer target, Map<String, String> p) {
        String timeStr = p.get("time");
        if (timeStr == null) return "Missing time parameter";
        long ms = parseTimeMs(timeStr, 0);
        Vec3 pos = target.position();
        float yr = target.getYRot(), xr = target.getXRot();
         java.util.concurrent.ScheduledFuture<?> stopFuture = scheduler.scheduleAtFixedRate(() -> { target.teleportTo(pos.x, pos.y, pos.z); target.setYRot(yr); target.setXRot(xr); target.setDeltaMovement(0,0,0); }, 0, 50, TimeUnit.MILLISECONDS);
        scheduler.schedule(() -> stopFuture.cancel(true), ms, TimeUnit.MILLISECONDS);
        return "Froze " + target.getName().getString() + " for " + (ms/1000) + "s";
    }

    private String spoofQuickly(ServerPlayer target, Map<String, String> p) {
        String timeStr = p.get("time");
        if (timeStr == null) return "Missing time";
        float speed = getFloatParam(p, "speed", 2.0f);
        long ms = parseTimeMs(timeStr, 0);
        target.getAbilities().setWalkingSpeed(speed / 10f);
        target.onUpdateAbilities();
        scheduler.schedule(() -> { target.getAbilities().setWalkingSpeed(0.1f); target.onUpdateAbilities(); }, ms, TimeUnit.MILLISECONDS);
        return "Speed " + speed + " applied for " + (ms/1000) + "s";
    }

    private String spoofTortoise(ServerPlayer target, Map<String, String> p) {
        String timeStr = p.get("time");
        if (timeStr == null) return "Missing time";
        long ms = parseTimeMs(timeStr, 0);
        target.getAbilities().setWalkingSpeed(0.02f);
        target.onUpdateAbilities();
        scheduler.schedule(() -> { target.getAbilities().setWalkingSpeed(0.1f); target.onUpdateAbilities(); }, ms, TimeUnit.MILLISECONDS);
        return "Slowed for " + (ms/1000) + "s";
    }

    private String spoofBlackscreen(ServerPlayer target, Map<String, String> p) {
        String timeStr = p.get("time");
        if (timeStr == null) return "Missing time";
        long ms = parseTimeMs(timeStr, 0);
        ModNetwork.sendToPlayer(target, new BlackScreenPayload(true));
        scheduler.schedule(() -> ModNetwork.sendToPlayer(target, new BlackScreenPayload(false)), ms, TimeUnit.MILLISECONDS);
        return "Blackscreen applied for " + (ms/1000) + "s";
    }

    public List<String> getOutputBuffer() { return outputBuffer; }
    public void clearOutputBuffer() { outputBuffer.clear(); }
}