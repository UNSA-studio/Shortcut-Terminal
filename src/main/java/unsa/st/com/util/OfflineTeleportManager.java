package unsa.st.com.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.*;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class OfflineTeleportManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Map<UUID, TeleportEntry> pendingTeleports = new HashMap<>();
    private static Path dataFile;

    public static void init() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            dataFile = server.getServerDirectory().resolve("st_offline_tp.json");
            load();
        }
    }

    public static void saveHomeTeleport(UUID uuid) {
        pendingTeleports.put(uuid, new TeleportEntry(true, 0, 0, 0));
        save();
    }

    public static void saveCoordTeleport(UUID uuid, double x, double y, double z) {
        pendingTeleports.put(uuid, new TeleportEntry(false, x, y, z));
        save();
    }

    private static void load() {
        if (dataFile == null) init();
        try (Reader reader = new FileReader(dataFile.toFile())) {
            Map<UUID, TeleportEntry> loaded = GSON.fromJson(reader, new TypeToken<Map<UUID, TeleportEntry>>(){}.getType());
            if (loaded != null) pendingTeleports = loaded;
        } catch (IOException ignored) {}
    }

    private static void save() {
        if (dataFile == null) return;
        try (Writer writer = new FileWriter(dataFile.toFile())) {
            GSON.toJson(pendingTeleports, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID uuid = player.getUUID();
            TeleportEntry entry = pendingTeleports.remove(uuid);
            if (entry != null) {
                Vec3 targetPos;
                if (entry.useHome) {
                    BlockPos home = player.getRespawnPosition();
                    if (home == null) home = player.serverLevel().getSharedSpawnPos();
                    targetPos = new Vec3(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
                } else {
                    targetPos = new Vec3(entry.x, entry.y, entry.z);
                }
                // 处理乘客和 leash
                if (player.isPassenger()) {
                    player.stopRiding();
                }
                Entity vehicle = player.getVehicle();
                if (vehicle != null) {
                    vehicle.ejectPassengers();
                }
                player.teleportTo(targetPos.x, targetPos.y, targetPos.z);
                save();
            }
        }
    }

    private static class TeleportEntry {
        boolean useHome;
        double x, y, z;
        TeleportEntry(boolean useHome, double x, double y, double z) {
            this.useHome = useHome;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
