package unsa.st.com.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import unsa.st.com.ShortcutTerminal;
import unsa.st.com.network.TriggerSyncPayload;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public class PlayerJoinHandler {
    // 帕秋莉模组 ID 和我们的指南书 ID
    private static final String PATCHOULI_MOD_ID = "patchouli";
    private static final ResourceLocation OUR_BOOK_ID = ResourceLocation.fromNamespaceAndPath(ShortcutTerminal.MODID, "st_guide");
    private static final String BOOK_GIVEN_TAG = "shortcutterminal_book_given";

    // 定时同步相关
    private static final ScheduledExecutorService syncScheduler = Executors.newScheduledThreadPool(1);
    private static final Map<UUID, ScheduledFuture<?>> playerSyncTasks = new ConcurrentHashMap<>();
    private static final long SYNC_INTERVAL_MINUTES = 5;

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 1. 发放帕秋莉指南书（终身只发一本）
            giveGuideBook(player);

            // 2. 启动定时同步（每 5 分钟本地→服务端）
            ScheduledFuture<?> task = syncScheduler.scheduleAtFixedRate(() -> {
                PacketDistributor.sendToPlayer(player, new TriggerSyncPayload(true));
            }, SYNC_INTERVAL_MINUTES, SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES);

            playerSyncTasks.put(player.getUUID(), task);
        }
    }

    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ScheduledFuture<?> task = playerSyncTasks.remove(player.getUUID());
            if (task != null) task.cancel(false);
        }
    }

    // ========== 指南书发放逻辑 ==========

    private void giveGuideBook(ServerPlayer player) {
        // 检测帕秋莉是否已加载
        if (!ModList.get().isLoaded(PATCHOULI_MOD_ID)) return;

        // 如果玩家已经被标记过"已发放"，跳过
        if (hasReceivedBook(player)) return;

        // 检查玩家身上是否已经有一本我们的指南书
        boolean hasOurBookInInventory = player.getInventory().items.stream().anyMatch(stack -> {
            var tag = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, 
                    net.minecraft.world.item.component.CustomData.EMPTY);
            return OUR_BOOK_ID.toString().equals(tag.copyTag().getString("patchouli:book"));
        });

        if (!hasOurBookInInventory) {
            // 通过反射安全获取 Patchouli 的书籍物品
            ItemStack book = getPatchouliBookSafely(OUR_BOOK_ID);
            if (!book.isEmpty()) {
                if (!player.getInventory().add(book)) {
                    player.drop(book, false);
                }
            }
        }

        // 标记玩家已领过书
        markBookReceived(player);
    }

    private boolean hasReceivedBook(ServerPlayer player) {
        return player.getPersistentData().contains(BOOK_GIVEN_TAG);
    }

    private void markBookReceived(ServerPlayer player) {
        player.getPersistentData().putBoolean(BOOK_GIVEN_TAG, true);
    }

    private ItemStack getPatchouliBookSafely(ResourceLocation bookId) {
        try {
            Class<?> patchouliAPI = Class.forName("vazkii.patchouli.api.PatchouliAPI");
            Method getBookStack = patchouliAPI.getMethod("getBookStack", ResourceLocation.class);
            Object result = getBookStack.invoke(null, bookId);
            if (result instanceof ItemStack) return (ItemStack) result;
        } catch (Exception e) {
            ShortcutTerminal.LOGGER.error("Failed to create Patchouli book", e);
        }
        return ItemStack.EMPTY;
    }
}