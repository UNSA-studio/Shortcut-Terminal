package unsa.st.com.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import unsa.st.com.ShortcutTerminal;

import java.util.HashMap;
import java.util.Map;

public record SyncFileSystemPacket(String uuid, Map<String, String> files) implements CustomPacketPayload {
    public static final Type<SyncFileSystemPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ShortcutTerminal.MODID, "sync_filesystem"));
    
    public static final StreamCodec<FriendlyByteBuf, SyncFileSystemPacket> STREAM_CODEC = StreamCodec.of(
        (buf, packet) -> {
            buf.writeUtf(packet.uuid);
            buf.writeInt(packet.files.size());
            for (Map.Entry<String, String> entry : packet.files.entrySet()) {
                buf.writeUtf(entry.getKey());
                buf.writeUtf(entry.getValue());
            }
        },
        buf -> {
            String uuid = buf.readUtf();
            int size = buf.readInt();
            Map<String, String> files = new HashMap<>();
            for (int i = 0; i < size; i++) {
                String key = buf.readUtf();
                String value = buf.readUtf();
                files.put(key, value);
            }
            return new SyncFileSystemPacket(uuid, files);
        }
    );
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
