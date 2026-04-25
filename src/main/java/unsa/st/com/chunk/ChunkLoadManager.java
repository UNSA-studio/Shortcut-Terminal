package unsa.st.com.chunk;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

public class ChunkLoadManager {
    public static void forceLoadChunk(ServerLevel level, ChunkPos chunkPos) {
        level.setChunkForced(chunkPos.x, chunkPos.z, true);
    }

    public static void removeForceLoad(ServerLevel level, ChunkPos chunkPos) {
        level.setChunkForced(chunkPos.x, chunkPos.z, false);
    }

    public static boolean isChunkForceLoaded(ServerLevel level, ChunkPos chunkPos) {
        return level.getForcedChunks().contains(chunkPos.toLong());
    }
}
