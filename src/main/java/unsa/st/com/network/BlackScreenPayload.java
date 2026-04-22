package unsa.st.com.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import unsa.st.com.ShortcutTerminal;

public record BlackScreenPayload(boolean enable) implements CustomPacketPayload {
    public static final Type<BlackScreenPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ShortcutTerminal.MODID, "black_screen"));
    public static final StreamCodec<FriendlyByteBuf, BlackScreenPayload> STREAM_CODEC = StreamCodec.composite(
            FriendlyByteBuf::readBoolean,
            BlackScreenPayload::enable,
            BlackScreenPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleClient(final BlackScreenPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            // 客户端设置黑屏渲染标志
            net.minecraft.client.Minecraft.getInstance().execute(() -> {
                BlackScreenHandler.setEnabled(payload.enable);
            });
        });
    }
}
