package unsa.st.com.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import unsa.st.com.ShortcutTerminal;

/** 服务端请求客户端截图。客户端逻辑委托给 client 包（dist 隔离）。 */
public record ScreenshotPayload(int angleOfView) implements CustomPacketPayload {
    public static final Type<ScreenshotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ShortcutTerminal.MODID, "screenshot"));

    public static final StreamCodec<FriendlyByteBuf, ScreenshotPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ScreenshotPayload decode(FriendlyByteBuf buf) {
            return new ScreenshotPayload(buf.readVarInt());
        }
        @Override
        public void encode(FriendlyByteBuf buf, ScreenshotPayload payload) {
            buf.writeVarInt(payload.angleOfView);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(final ScreenshotPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> unsa.st.com.client.ClientPayloadHandler.onScreenshot(payload.angleOfView));
    }
}
