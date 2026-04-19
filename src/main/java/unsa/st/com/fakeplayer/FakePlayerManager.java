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

    public static FakePlayerEntity createFakePlayer(String name, ServerLevel level, BlockPos pos) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        UUID uuid = UUID.nameUUIDFromBytes(("fake_" + name).getBytes());
        GameProfile profile = new GameProfile(uuid, name);
        
        FakePlayerEntity fakePlayer = new FakePlayerEntity(level, profile);
        fakePlayer.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        
        // 关键：先加入玩家列表，再加入世界
        server.getPlayerList().getPlayers().add(fakePlayer);
        level.addNewPlayer(fakePlayer);
        
        fakePlayers.put(uuid, fakePlayer);
        return fakePlayer;
    }

    public static boolean removeFakePlayer(String name) {
        for (Map.Entry<UUID, FakePlayerEntity> entry : fakePlayers.entrySet()) {
            if (entry.getValue().getGameProfile().getName().equals(name)) {
                FakePlayerEntity fp = entry.getValue();
                fp.remove(Player.RemovalReason.DISCARDED);
                fp.getServer().getPlayerList().getPlayers().remove(fp);
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
