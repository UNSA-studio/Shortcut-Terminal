package unsa.st.com.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import unsa.st.com.ShortcutTerminal;
import unsa.st.com.filesystem.UserFileSystem;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record SyncFileSystemPacket(String uuid, Map<String, String> files) implements CustomPacketPayload {
    public static final Type<SyncFileSystemPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ShortcutTerminal.MODID, "sync_filesystem"));
    
    public static final StreamCodec<FriendlyByteBuf, SyncFileSystemPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SyncFileSystemPacket decode(FriendlyByteBuf buf) {
            String uuid = buf.readUtf();
            int size = buf.readVarInt();
            Map<String, String> files = new HashMap<>();
            for (int i = 0; i < size; i++) {
                files.put(buf.readUtf(), buf.readUtf());
            }
            return new SyncFileSystemPacket(uuid, files);
        }

        @Override
        public void encode(FriendlyByteBuf buf, SyncFileSystemPacket packet) {
            buf.writeUtf(packet.uuid);
            buf.writeVarInt(packet.files.size());
            for (Map.Entry<String, String> entry : packet.files.entrySet()) {
                buf.writeUtf(entry.getKey());
                buf.writeUtf(entry.getValue());
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(final SyncFileSystemPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            UUID uuid = UUID.fromString(packet.uuid());
            ServerPlayer player = context.player().getServer().getPlayerList().getPlayer(uuid);
            if (player == null) return;
            
            int success = 0;
            for (Map.Entry<String, String> entry : packet.files().entrySet()) {
                String fullPath = entry.getKey();
                String content = entry.getValue();
                
                int lastSlash = fullPath.lastIndexOf('/');
                String dirPath = lastSlash > 0 ? fullPath.substring(0, lastSlash + 1) : "/";
                String fileName = fullPath.substring(lastSlash + 1);
                
                // writeFile 返回 void，直接调用无法判断成功与否
                UserFileSystem.writeFile(uuid, dirPath, fileName, content);
                success++;
            }
            
            player.sendSystemMessage(Component.literal("§a[Sync] " + success + " files synced from client to server."));
        });
    }
}