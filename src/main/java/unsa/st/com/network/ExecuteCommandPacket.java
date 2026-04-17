package unsa.st.com.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import unsa.st.com.ShortcutTerminal;

public record ExecuteCommandPacket(String command) implements CustomPacketPayload {
    public static final Type<ExecuteCommandPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ShortcutTerminal.MODID, "execute_command"));
    public static final StreamCodec<FriendlyByteBuf, ExecuteCommandPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ExecuteCommandPacket::command,
            ExecuteCommandPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
