package unsa.st.com.chunk;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkRequestManager {
    private static final Map<UUID, PendingChunkRequest> requests = new ConcurrentHashMap<>();

    public static void addRequest(PendingChunkRequest request) {
        requests.put(request.getId(), request);
    }

    public static PendingChunkRequest getRequest(UUID id) {
        return requests.get(id);
    }

    public static void removeRequest(UUID id) {
        requests.remove(id);
    }

    public static class PendingChunkRequest {
        private final UUID id;
        private final UUID playerUuid;
        private final String playerName;
        private final ChunkPos chunkPos;
        private final ResourceKey<Level> dimension;

        public PendingChunkRequest(UUID playerUuid, String playerName, ChunkPos chunkPos, ResourceKey<Level> dimension) {
            this.id = UUID.randomUUID();
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.chunkPos = chunkPos;
            this.dimension = dimension;
        }

        public UUID getId() { return id; }
        public UUID getPlayerUuid() { return playerUuid; }
        public String getPlayerName() { return playerName; }
        public ChunkPos getChunkPos() { return chunkPos; }
        public ResourceKey<Level> getDimension() { return dimension; }
    }
}
