package unsa.st.com.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import unsa.st.com.ShortcutTerminal;

public record ExecuteCommandPayload(String command) implements CustomPacketPayload {
    public static final Type<ExecuteCommandPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ShortcutTerminal.MODID, "execute_command"));
    public static final StreamCodec<FriendlyByteBuf, ExecuteCommandPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ExecuteCommandPayload::command,
            ExecuteCommandPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
