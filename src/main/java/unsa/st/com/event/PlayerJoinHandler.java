package unsa.st.com.event;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import unsa.st.com.ShortcutTerminal;
import unsa.st.com.network.TriggerSyncPayload;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

@EventBusSubscriber(modid = ShortcutTerminal.MODID)
public class PlayerJoinHandler {
    private static final ScheduledExecutorService syncScheduler = Executors.newScheduledThreadPool(1);
    private static final Map<UUID, ScheduledFuture<?>> playerSyncTasks = new ConcurrentHashMap<>();

    // 每5分钟执行一次同步
    private static final long SYNC_INTERVAL_MINUTES = 5;

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 开始定时同步
            ScheduledFuture<?> task = syncScheduler.scheduleAtFixedRate(() -> {
                // 向玩家客户端发送同步触发包，执行 local -> server 同步
                PacketDistributor.sendToPlayer(new TriggerSyncPayload(true), player);
                
                // 在服务器日志中记录（可选）
                ShortcutTerminal.LOGGER.debug("Triggered auto file sync for player {}", player.getName().getString());
            }, SYNC_INTERVAL_MINUTES, SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES);
            
            playerSyncTasks.put(player.getUUID(), task);
        }
    }

    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ScheduledFuture<?> task = playerSyncTasks.remove(player.getUUID());
            if (task != null) {
                task.cancel(false);
            }
        }
    }
}