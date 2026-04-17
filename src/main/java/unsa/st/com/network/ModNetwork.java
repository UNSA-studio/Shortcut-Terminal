package unsa.st.com.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import unsa.st.com.ShortcutTerminal;
import unsa.st.com.gui.TerminalScreen;
import unsa.st.com.util.CommandExecutor;

public class ModNetwork {
    private static final String PROTOCOL_VERSION = "1";

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(ShortcutTerminal.MODID).versioned(PROTOCOL_VERSION);
        
        // 客户端 -> 服务端：发送命令
        registrar.playToServer(
                ExecuteCommandPacket.TYPE,
                ExecuteCommandPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        (payload, context) -> context.enqueueWork(() -> {
                            ServerPlayer player = (ServerPlayer) context.player();
                            String result = CommandExecutor.executeFromGUI(player, payload.command());
                            PacketDistributor.sendToPlayer(player, new CommandResultPacket(result));
                        }),
                        (payload, context) -> {}
                )
        );

        // 服务端 -> 客户端：返回结果
        registrar.playToClient(
                CommandResultPacket.TYPE,
                CommandResultPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        (payload, context) -> context.enqueueWork(() -> TerminalScreen.receiveCommandResult(payload.result())),
                        (payload, context) -> {}
                )
        );
    }
}
