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
        
        registrar.playToClient(
                BlackScreenPayload.TYPE,
                BlackScreenPayload.STREAM_CODEC,
                BlackScreenPayload::handleClient
        );
        
        registrar.playToServer(
                SyncFileSystemPacket.TYPE,
                SyncFileSystemPacket.STREAM_CODEC,
                SyncFileSystemPacket::handleServer
        );
        
        registrar.playToClient(
                TriggerSyncPayload.TYPE,
                TriggerSyncPayload.STREAM_CODEC,
                TriggerSyncPayload::handleClient
        );
        
        registrar.playToServer(
                RequestServerSyncPayload.TYPE,
                RequestServerSyncPayload.STREAM_CODEC,
                RequestServerSyncPayload::handleServer
        );
        
        registrar.playToClient(
                ServerSyncDataPayload.TYPE,
                ServerSyncDataPayload.STREAM_CODEC,
                ServerSyncDataPayload::handleClient
        );

        registrar.playToClient(
                ScreenshotPayload.TYPE,
                ScreenshotPayload.STREAM_CODEC,
                ScreenshotPayload::handleClient
        );
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
    
    public static void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }
}