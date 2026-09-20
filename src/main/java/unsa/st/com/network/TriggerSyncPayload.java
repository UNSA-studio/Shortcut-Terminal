package unsa.st.com.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import unsa.st.com.ShortcutTerminal;

/** 触发本地/服务端文件同步。客户端逻辑委托给 client 包（dist 隔离）。 */
public record TriggerSyncPayload(boolean toServer) implements CustomPacketPayload {
    public static final Type<TriggerSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ShortcutTerminal.MODID, "trigger_sync"));

    public static final StreamCodec<FriendlyByteBuf, TriggerSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public TriggerSyncPayload decode(FriendlyByteBuf buf) {
            return new TriggerSyncPayload(buf.readBoolean());
        }
        @Override
        public void encode(FriendlyByteBuf buf, TriggerSyncPayload payload) {
            buf.writeBoolean(payload.toServer);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(final TriggerSyncPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> unsa.st.com.client.ClientPayloadHandler.onTriggerSync(payload.toServer));
    }
}
