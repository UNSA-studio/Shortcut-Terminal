package unsa.st.com.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import unsa.st.com.pkg.PkgManager;
import unsa.st.com.ShortcutTerminal;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ClientCommandExecutor {
    private final String playerName;
    private String currentPath = "/";
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final List<String> outputBuffer = new ArrayList<>();

    public ClientCommandExecutor(String playerName) {
        this.playerName = playerName;
    }

    public String execute(String command, String[] args) {
        String result = executeBuiltInCommand(command, args);
        if (result != null) return result;
        Path ext = findExecutableInPath(command);
        if (ext != null) return executeExternalProgram(ext, args);
        return "Error: Unknown command. Type 'help' for available commands.";
    }

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
                // 格式：cmd - /path/to/executable
                String[] parts = line.split(" - ");
                if (parts.length == 2 && parts[0].equals(command)) {
                    return Paths.get(parts[1]);
                }
            }
        } catch (IOException e) {
            ShortcutTerminal.LOGGER.error("PATH read error", e);
        }
        return null;
    }

    private String executeExternalProgram(Path programPath, String[] args) {
        StringBuilder output = new StringBuilder();
        try {
            List<String> cmdList = new ArrayList<>();
            cmdList.add(programPath.toAbsolutePath().toString());
            cmdList.addAll(Arrays.asList(args));
            ProcessBuilder pb = new ProcessBuilder(cmdList);
            pb.directory(programPath.getParent().toFile());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) output.append(line).append("\n");
            }
            int exit = p.waitFor();
            if (exit != 0) output.append("\nProcess exited with code ").append(exit);
        } catch (Exception e) {
            return "Error: Execution failed - " + e.getMessage();
        }
        return output.toString().trim();
    }

    private String getHelp() {
        return "Available: ls, mkdir, touch, rm, cat, echo, cd, pwd, clear, pkg, run spoof";
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
        boolean recursive = false;
        String target;
        if (args[0].equals("-r")) {
            if (args.length < 2) return "Usage: rm [-r] <name>";
            recursive = true;
            target = args[1];
        } else {
            target = args[0];
        }
        boolean ok = ClientVirtualFileSystem.delete(playerName, currentPath, target, recursive);
        return ok ? "Deleted." : "Error: Failed to delete.";
    }

    private String executeCat(String[] args) {
        if (args.length == 0) return "Usage: cat <file>";
        String content = ClientVirtualFileSystem.readFile(playerName, currentPath, args[0]);
        return content != null ? content : "Error: File not found.";
    }

    private String executeEcho(String[] args) {
        return String.join(" ", args);
    }

    private String executeCd(String[] args) {
        if (args.length == 0) return "Usage: cd <path>";
        String newPath = ClientVirtualFileSystem.normalizePath(currentPath, args[0]);
        if (ClientVirtualFileSystem.exists(playerName, newPath)) {
            currentPath = newPath;
            return "Changed directory to: " + (currentPath.isEmpty() ? "/" : currentPath);
        }
        return "Error: Directory not found.";
    }

    private String executePwd() {
        return currentPath.isEmpty() ? "/" : currentPath;
    }

    private String executePkg(String[] args) {
        if (args.length == 0) return "Usage: pkg <update|search|install|remove|list|show>";
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "update": return PkgManager.updateIndex();
            case "search":
                if (args.length < 2) return "Usage: pkg search <keyword>";
                List<String> results = PkgManager.search(args[1]);
                return results.isEmpty() ? "No packages found." : String.join("\n", results);
            case "install":
                if (args.length < 2) return "Usage: pkg install <package>";
                return PkgManager.install(args[1], true);
            case "remove":
                if (args.length < 2) return "Usage: pkg remove <package>";
                return PkgManager.remove(args[1], true);
            case "list":
                List<String> installed = PkgManager.listInstalled(true);
                return installed.isEmpty() ? "No packages installed." : String.join("\n", installed);
            case "show":
                if (args.length < 2) return "Usage: pkg show <package>";
                return PkgManager.showInfo(args[1]);
            default:
                return "Unknown pkg command";
        }
    }

    // ==================== RUN SPOOF ====================
    private String executeRun(String[] args) {
        if (args.length == 0) return "Usage: run <module> [args...]";
        String module = args[0].toLowerCase(Locale.ROOT);
        String[] moduleArgs = Arrays.copyOfRange(args, 1, args.length);
        if ("spoof".equals(module)) {
            return executeSpoof(moduleArgs);
        }
        return "Unknown run module: " + module;
    }

    private String executeSpoof(String[] args) {
        if (args.length == 0) return "Usage: run spoof <action> [player] [parameters...]";
        String action = args[0].toLowerCase(Locale.ROOT);
        String targetPlayer = args.length > 1 && !args[1].contains("-") ? args[1] : playerName;
        String[] params = args.length > (targetPlayer.equals(playerName) ? 1 : 2)
                ? Arrays.copyOfRange(args, (targetPlayer.equals(playerName) ? 1 : 2), args.length)
                : new String[0];
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
        for (String arg : args) {
            int dash = arg.indexOf('-');
            if (dash > 0) {
                map.put(arg.substring(0, dash).toLowerCase(), arg.substring(dash + 1));
            }
        }
        return map;
    }

    private int getIntParam(Map<String, String> params, String key, int def) {
        try { return Integer.parseInt(params.getOrDefault(key, String.valueOf(def))); } catch (NumberFormatException e) { return def; }
    }

    private float getFloatParam(Map<String, String> params, String key, float def) {
        try { return Float.parseFloat(params.getOrDefault(key, String.valueOf(def))); } catch (NumberFormatException e) { return def; }
    }

    private long parseTimeMs(String timeStr, long defaultSeconds) {
        if (timeStr == null || timeStr.isEmpty()) return defaultSeconds * 1000;
        timeStr = timeStr.toLowerCase();
        try {
            if (timeStr.endsWith("ms")) return Long.parseLong(timeStr.replace("ms", ""));
            if (timeStr.endsWith("s")) return Long.parseLong(timeStr.replace("s", "")) * 1000;
            if (timeStr.endsWith("m")) return Long.parseLong(timeStr.replace("m", "")) * 60 * 1000;
            if (timeStr.endsWith("h")) return Long.parseLong(timeStr.replace("h", "")) * 3600 * 1000;
            return Long.parseLong(timeStr) * 1000;
        } catch (NumberFormatException e) { return defaultSeconds * 1000; }
    }

    private ServerPlayer getServerPlayer(String name) {
        if (Minecraft.getInstance().hasSingleplayerServer()) {
            return Minecraft.getInstance().getSingleplayerServer().getPlayerList().getPlayerByName(name);
        }
        return null;
    }

    private String spoofRay(ServerPlayer target, Map<String, String> params) {
        int quantity = getIntParam(params, "quantity", 1);
        float damage = getFloatParam(params, "injure", 5.0f);
        BlockPos pos = target.blockPosition();
        Level level = target.level();
        for (int i = 0; i < quantity; i++) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.setPos(Vec3.atBottomCenterOf(pos));
                bolt.setCause(target);
                level.addFreshEntity(bolt);
            }
        }
        target.hurt(target.damageSources().lightningBolt(), damage);
        return "Struck " + target.getName().getString() + " with " + quantity + " lightning bolts, damage: " + damage;
    }

    private String spoofCreeper(ServerPlayer target, Map<String, String> params) {
        int quantity = Math.min(getIntParam(params, "quantity", 1), 64);
        boolean charged = "lightning".equalsIgnoreCase(params.get("morphology"));
        String timeStr = params.get("time");
        Level level = target.level();
        BlockPos pos = target.blockPosition();
        for (int i = 0; i < quantity; i++) {
            Creeper creeper = EntityType.CREEPER.create(level);
            if (creeper != null) {
                creeper.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                if (charged) creeper.setPowered(true);
                level.addFreshEntity(creeper);
                if ("moment".equalsIgnoreCase(timeStr)) {
                    creeper.ignite();
                } else if (timeStr != null && !timeStr.isEmpty()) {
                    long delay = parseTimeMs(timeStr, 0);
                    scheduler.schedule(() -> creeper.ignite(), delay, TimeUnit.MILLISECONDS);
                }
            }
        }
        return "Spawned " + quantity + (charged ? " charged" : "") + " creepers at " + target.getName().getString();
    }

    private String spoofFlyup(ServerPlayer target, Map<String, String> params) {
        String manner = params.getOrDefault("manner", "");
        String direction = params.getOrDefault("direction", "");
        String coordsStr = params.get("coordinates");
        boolean noFallDamage = "no".equalsIgnoreCase(params.get("injure"));
        Vec3 start = target.position();
        Vec3 destination;
        double speed = 20;

        if (!manner.isEmpty()) {
            if ("fast".equals(manner)) {
                speed = Double.MAX_VALUE;
            } else {
                try { speed = Double.parseDouble(manner); } catch (NumberFormatException ignored) {}
            }
        }

        if ("upward".equals(direction)) {
            destination = start.add(0, 100, 0);
        } else if (coordsStr != null) {
            String[] parts = coordsStr.split(",");
            if (parts.length == 3) {
                try {
                    double x = Double.parseDouble(parts[0]);
                    double y = Double.parseDouble(parts[1]);
                    double z = Double.parseDouble(parts[2]);
                    destination = new Vec3(x, y, z);
                } catch (NumberFormatException e) {
                    return "Invalid coordinates format. Use x,y,z";
                }
            } else return "Coordinates must be x,y,z";
        } else return "Must specify direction (upward) or coordinates";

        if (speed >= Double.MAX_VALUE) {
            target.teleportTo(destination.x, destination.y, destination.z);
        } else {
            animateTeleport(target, start, destination, speed);
        }
        if (noFallDamage) target.fallDistance = 0;
        return "Teleported " + target.getName().getString() + " to " + destination;
    }

    private String spoofEvasiveGround(ServerPlayer target, Map<String, String> params) {
        String manner = params.getOrDefault("manner", "");
        String coordsStr = params.get("coordinates");
        boolean suffocate = "yes".equalsIgnoreCase(params.get("injure"));
        Vec3 start = target.position();
        Vec3 destination;
        double speed = 20;

        if (!manner.isEmpty()) {
            if ("fast".equals(manner)) {
                speed = Double.MAX_VALUE;
            } else {
                try { speed = Double.parseDouble(manner); } catch (NumberFormatException ignored) {}
            }
        }

        if (coordsStr != null) {
            String[] parts = coordsStr.split(",");
            if (parts.length == 3) {
                try {
                    double x = Double.parseDouble(parts[0]);
                    double y = Double.parseDouble(parts[1]);
                    double z = Double.parseDouble(parts[2]);
                    destination = new Vec3(x, y, z);
                } catch (NumberFormatException e) {
                    return "Invalid coordinates";
                }
            } else return "Invalid coordinates";
        } else {
            destination = start.add(0, -10, 0);
        }

        if (speed >= Double.MAX_VALUE) {
            target.teleportTo(destination.x, destination.y, destination.z);
        } else {
            animateTeleport(target, start, destination, speed);
        }
        if (suffocate) target.hurt(target.damageSources().inWall(), 2.0f);
        return "Burrowed " + target.getName().getString();
    }

    private void animateTeleport(ServerPlayer player, Vec3 from, Vec3 to, double blocksPerSecond) {
        double distance = from.distanceTo(to);
        long durationMs = (long) (distance / blocksPerSecond * 1000);
        if (durationMs <= 0) {
            player.teleportTo(to.x, to.y, to.z);
            return;
        }
        scheduler.scheduleAtFixedRate(new Runnable() {
            long startTime = System.currentTimeMillis();
            @Override
            public void run() {
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed >= durationMs) {
                    player.teleportTo(to.x, to.y, to.z);
                    throw new RuntimeException("Done");
                }
                double t = (double) elapsed / durationMs;
                double x = from.x + (to.x - from.x) * t;
                double y = from.y + (to.y - from.y) * t;
                double z = from.z + (to.z - from.z) * t;
                player.teleportTo(x, y, z);
            }
        }, 0, 50, TimeUnit.MILLISECONDS);
    }

    private String spoofStop(ServerPlayer target, Map<String, String> params) {
        String timeStr = params.get("time");
        if (timeStr == null) return "Missing required parameter: time";
        long ms = parseTimeMs(timeStr, 0);
        Vec3 pos = target.position();
        float yRot = target.getYRot();
        float xRot = target.getXRot();
        scheduler.scheduleAtFixedRate(() -> {
            target.teleportTo(pos.x, pos.y, pos.z);
            target.setYRot(yRot);
            target.setXRot(xRot);
            target.setDeltaMovement(0, 0, 0);
        }, 0, 50, TimeUnit.MILLISECONDS);
        scheduler.schedule(() -> {}, ms, TimeUnit.MILLISECONDS);
        return "Froze " + target.getName().getString() + " for " + (ms/1000) + " seconds";
    }

    private String spoofQuickly(ServerPlayer target, Map<String, String> params) {
        String timeStr = params.get("time");
        float speed = getFloatParam(params, "speed", 2.0f);
        if (timeStr == null) return "Missing required parameter: time";
        long ms = parseTimeMs(timeStr, 0);
        target.getAbilities().setWalkingSpeed(speed / 10f);
        target.onUpdateAbilities();
        scheduler.schedule(() -> {
            target.getAbilities().setWalkingSpeed(0.1f);
            target.onUpdateAbilities();
        }, ms, TimeUnit.MILLISECONDS);
        return "Applied speed " + speed + " to " + target.getName().getString() + " for " + (ms/1000) + "s";
    }

    private String spoofTortoise(ServerPlayer target, Map<String, String> params) {
        String timeStr = params.get("time");
        if (timeStr == null) return "Missing required parameter: time";
        long ms = parseTimeMs(timeStr, 0);
        target.getAbilities().setWalkingSpeed(0.02f);
        target.onUpdateAbilities();
        scheduler.schedule(() -> {
            target.getAbilities().setWalkingSpeed(0.1f);
            target.onUpdateAbilities();
        }, ms, TimeUnit.MILLISECONDS);
        return "Slowed " + target.getName().getString() + " for " + (ms/1000) + "s";
    }

    private String spoofBlackscreen(ServerPlayer target, Map<String, String> params) {
        // 黑屏功能需要网络包支持，暂未实现
        return "Blackscreen feature not implemented yet. Requires network payload.";
    }

    public List<String> getOutputBuffer() { return outputBuffer; }
    public void clearOutputBuffer() { outputBuffer.clear(); }
}