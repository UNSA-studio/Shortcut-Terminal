package unsa.st.com.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import unsa.st.com.ShortcutTerminal;
import unsa.st.com.client.ClientVirtualFileSystem;
import unsa.st.com.gui.TerminalScreen;

import java.util.Map;
import java.util.UUID;

public record TriggerSyncPayload(boolean toServer) implements CustomPacketPayload {
    public static final Type<TriggerSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ShortcutTerminal.MODID, "trigger_sync"));
    
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
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            
            if (payload.toServer) {
                // 显示同步开始消息
                mc.player.displayClientMessage(Component.literal("§e[Sync] File sync started, please do not shut down the game."), false);
                
                // 执行本地→服务端同步
                UUID uuid = mc.player.getUUID();
                Map<String, String> snapshot = ClientVirtualFileSystem.getFileSystemSnapshot(uuid.toString());
                PacketDistributor.sendToServer(new SyncFileSystemPacket(uuid.toString(), snapshot));
                
                // 显示同步完成消息
                mc.player.displayClientMessage(Component.literal("§a[Sync] File synchronization completed."), false);
                
                // 同时向终端面板输出（如果打开）
                if (TerminalScreen.getInstance() != null) {
                    TerminalScreen.getInstance().addOutputLine("§a[Sync] Auto-synced local files to server.");
                }
            } else {
                // 触发服务端→本地同步（由 TerminalScreen 处理）
                if (TerminalScreen.getInstance() != null) {
                    TerminalScreen.getInstance().requestSyncFromServer();
                }
            }
        });
    }
}