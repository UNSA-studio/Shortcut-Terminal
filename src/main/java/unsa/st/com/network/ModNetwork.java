package unsa.st.com.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import unsa.st.com.ShortcutTerminal;

public class ModNetwork {
    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(ShortcutTerminal.MODID);
        
        // 黑屏网络包（服务端→客户端）
        registrar.playToClient(
                BlackScreenPayload.TYPE,
                BlackScreenPayload.STREAM_CODEC,
                BlackScreenPayload::handleClient
        );
        
        // 文件同步包（客户端→服务端）
        registrar.playToServer(
                SyncFileSystemPacket.TYPE,
                SyncFileSystemPacket.STREAM_CODEC,
                SyncFileSystemPacket::handleServer
        );
        
        // 触发同步请求（服务端→客户端）
        registrar.playToClient(
                TriggerSyncPayload.TYPE,
                TriggerSyncPayload.STREAM_CODEC,
                TriggerSyncPayload::handleClient
        );
        
        // 请求服务端同步（客户端→服务端）
        registrar.playToServer(
                RequestServerSyncPayload.TYPE,
                RequestServerSyncPayload.STREAM_CODEC,
                RequestServerSyncPayload::handleServer
        );
        
        // 服务端同步数据（服务端→客户端）
        registrar.playToClient(
                ServerSyncDataPayload.TYPE,
                ServerSyncDataPayload.STREAM_CODEC,
                ServerSyncDataPayload::handleClient
        );
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
}