package unsa.st.com.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import unsa.st.com.ShortcutTerminal;
import unsa.st.com.gui.TerminalScreen;

import java.util.HashMap;
import java.util.Map;

public record ServerSyncDataPayload(Map<String, String> files) implements CustomPacketPayload {
    public static final Type<ServerSyncDataPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ShortcutTerminal.MODID, "server_sync_data"));
    
    public static final StreamCodec<FriendlyByteBuf, ServerSyncDataPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ServerSyncDataPayload decode(FriendlyByteBuf buf) {
            int size = buf.readVarInt();
            Map<String, String> files = new HashMap<>();
            for (int i = 0; i < size; i++) {
                files.put(buf.readUtf(), buf.readUtf());
            }
            return new ServerSyncDataPayload(files);
        }
        @Override
        public void encode(FriendlyByteBuf buf, ServerSyncDataPayload payload) {
            buf.writeVarInt(payload.files.size());
            for (Map.Entry<String, String> entry : payload.files.entrySet()) {
                buf.writeUtf(entry.getKey());
                buf.writeUtf(entry.getValue());
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(final ServerSyncDataPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(Component.literal("§a[Sync] File synchronization completed."), false);
            }
            TerminalScreen.receiveServerSyncData("", payload.files());
        });
    }
}