package unsa.st.com.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import unsa.st.com.ShortcutTerminal;
import unsa.st.com.filesystem.UserFileSystem;

import java.util.Map;
import java.util.UUID;

public record RequestServerSyncPayload(String uuid) implements CustomPacketPayload {
    public static final Type<RequestServerSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ShortcutTerminal.MODID, "request_server_sync"));
    
    public static final StreamCodec<FriendlyByteBuf, RequestServerSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public RequestServerSyncPayload decode(FriendlyByteBuf buf) {
            return new RequestServerSyncPayload(buf.readUtf());
        }
        @Override
        public void encode(FriendlyByteBuf buf, RequestServerSyncPayload payload) {
            buf.writeUtf(payload.uuid);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(final RequestServerSyncPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            UUID uuid = UUID.fromString(payload.uuid());
            ServerPlayer player = context.player().getServer().getPlayerList().getPlayer(uuid);
            if (player == null) return;
            
            Map<String, String> allFiles = UserFileSystem.getFileSystemSnapshot(uuid);
            PacketDistributor.sendToPlayer(new ServerSyncDataPayload(allFiles), player);
        });
    }
}