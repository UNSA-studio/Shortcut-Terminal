package unsa.st.com.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import unsa.st.com.ShortcutTerminal;

public class ModPackets {
    private static final String PROTOCOL_VERSION = "1";

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(ShortcutTerminal.MODID)
                .versioned(PROTOCOL_VERSION);
        
        // 客户端 -> 服务端：发送命令
        registrar.playToServer(
                ExecuteCommandPayload.TYPE,
                ExecuteCommandPayload.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        (payload, context) -> context.enqueueWork(() -> {
                            ServerPlayer player = (ServerPlayer) context.player();
                            // 在服务端执行命令，并获取结果
                            String result = unsa.st.com.util.CommandExecutor.executeFromGUI(player, payload.command());
                            // 将结果返回给客户端
                            PacketDistributor.sendToPlayer(player, new CommandResultPayload(result));
                        }),
                        (payload, context) -> {
                            // 服务端处理，此处留空
                        }
                )
        );

        // 服务端 -> 客户端：返回结果
        registrar.playToClient(
                CommandResultPayload.TYPE,
                CommandResultPayload.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        (payload, context) -> context.enqueueWork(() -> {
                            // 客户端处理结果，更新GUI
                            TerminalScreen.receiveCommandResult(payload.result());
                        }),
                        (payload, context) -> {
                            // 客户端处理，此处留空
                        }
                )
        );
    }
}
