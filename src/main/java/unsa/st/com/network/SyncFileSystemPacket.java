package unsa.st.com.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import unsa.st.com.ShortcutTerminal;

import java.util.HashMap;
import java.util.Map;

public record SyncFileSystemPacket(String uuid, Map<String, String> files) implements CustomPacketPayload {
    public static final Type<SyncFileSystemPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ShortcutTerminal.MODID, "sync_filesystem"));
    
    public static final StreamCodec<FriendlyByteBuf, SyncFileSystemPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(FriendlyByteBuf buf, SyncFileSystemPacket packet) {
            buf.writeUtf(packet.uuid);
            buf.writeMap(packet.files, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeUtf);
        }
        
        @Override
        public SyncFileSystemPacket decode(FriendlyByteBuf buf) {
            String uuid = buf.readUtf();
            Map<String, String> files = buf.readMap(FriendlyByteBuf::readUtf, FriendlyByteBuf::readUtf);
            return new SyncFileSystemPacket(uuid, files);
        }
    };
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
