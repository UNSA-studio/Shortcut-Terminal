package unsa.st.com.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.minecraft.server.level.ServerPlayer;
import unsa.st.com.ShortcutTerminal;
import unsa.st.com.filesystem.UserFileSystem;

import java.util.Map;

public class ModNetwork {
    private static final String PROTOCOL_VERSION = "1";

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(ShortcutTerminal.MODID).versioned(PROTOCOL_VERSION);
        
        // 客户端 -> 服务端：同步文件系统
        registrar.playToServer(
                SyncFileSystemPacket.TYPE,
                SyncFileSystemPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        (payload, context) -> context.enqueueWork(() -> {
                            ServerPlayer player = (ServerPlayer) context.player();
                            applyFileSystemSync(player, payload);
                        }),
                        (payload, context) -> {}
                )
        );
        
        // 客户端 -> 服务端：执行需要服务端权限的命令（如refresh bf）
        registrar.playToServer(
                ExecuteCommandPacket.TYPE,
                ExecuteCommandPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        (payload, context) -> context.enqueueWork(() -> {
                            ServerPlayer player = (ServerPlayer) context.player();
                            unsa.st.com.util.CommandExecutor.executeFromGUI(player, payload.command());
                        }),
                        (payload, context) -> {}
                )
        );
    }
    
    private static void applyFileSystemSync(ServerPlayer player, SyncFileSystemPacket packet) {
        UserFileSystem.createUserDirectory(player.getUUID());
        for (Map.Entry<String, String> entry : packet.files().entrySet()) {
            String path = entry.getKey();
            String content = entry.getValue();
            if (path.endsWith("/")) {
                // 目录：去掉末尾的斜杠
                String dirName = path.substring(0, path.length() - 1);
                if (!dirName.isEmpty()) {
                    UserFileSystem.createDirectory(player.getUUID(), "", dirName);
                }
            } else {
                // 文件
                UserFileSystem.createFile(player.getUUID(), "", path);
                if (!content.isEmpty()) {
                    UserFileSystem.writeFile(player.getUUID(), "", path, content);
                }
            }
        }
    }
}
