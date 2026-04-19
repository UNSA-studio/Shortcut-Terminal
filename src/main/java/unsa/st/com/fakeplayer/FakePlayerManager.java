package unsa.st.com.fakeplayer;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FakePlayerManager {
    private static final Map<UUID, FakePlayerEntity> fakePlayers = new ConcurrentHashMap<>();
    // Alex 的正版 UUID
    private static final UUID ALEX_UUID = UUID.fromString("8c8c0d6e-8d6e-4a2e-8d6e-8d6e8d6e8d6e");

    public static FakePlayerEntity createFakePlayer(String name, ServerLevel level, BlockPos pos) {
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            // 使用 Alex 的 UUID 和名字
            GameProfile profile = new GameProfile(ALEX_UUID, "Alex");
            
            FakePlayerEntity fakePlayer = new FakePlayerEntity(level, profile);
            fakePlayer.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            
            level.addNewPlayer(fakePlayer);
            fakePlayers.put(profile.getId(), fakePlayer);
            return fakePlayer;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static boolean removeFakePlayer(String name) {
        for (Map.Entry<UUID, FakePlayerEntity> entry : fakePlayers.entrySet()) {
            if (entry.getValue().getGameProfile().getName().equals(name)) {
                FakePlayerEntity fp = entry.getValue();
                fp.remove(Player.RemovalReason.DISCARDED);
                fakePlayers.remove(entry.getKey());
                return true;
            }
        }
        return false;
    }

    public static FakePlayerEntity getFakePlayer(String name) {
        for (FakePlayerEntity fp : fakePlayers.values()) {
            if (fp.getGameProfile().getName().equals(name)) return fp;
        }
        return null;
    }

    public static Collection<FakePlayerEntity> getAllFakePlayers() {
        return fakePlayers.values();
    }

    public static boolean exists(String name) {
        return getFakePlayer(name) != null;
    }
}
