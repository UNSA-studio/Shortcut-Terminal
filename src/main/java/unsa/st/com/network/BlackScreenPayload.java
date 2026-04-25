package unsa.st.com.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import unsa.st.com.ShortcutTerminal;
import unsa.st.com.client.BlackScreenHandler;

public record BlackScreenPayload(boolean enable) implements CustomPacketPayload {
    public static final Type<BlackScreenPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ShortcutTerminal.MODID, "black_screen"));
    
    public static final StreamCodec<FriendlyByteBuf, BlackScreenPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BlackScreenPayload decode(FriendlyByteBuf buf) {
            return new BlackScreenPayload(buf.readBoolean());
        }
        @Override
        public void encode(FriendlyByteBuf buf, BlackScreenPayload payload) {
            buf.writeBoolean(payload.enable);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleClient(final BlackScreenPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            BlackScreenHandler.setEnabled(payload.enable);
        });
    }
}