package unsa.st.com.kernel;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import static unsa.st.com.kernel.TerminalKernel.klog;

/**
 * 内核事件泵：把 NeoForge 服务器事件转译为内核状态变化 + dmesg 日志。
 * 由主类注册到 NeoForge.EVENT_BUS。
 */
@Mod.EventBusSubscriber(modid = "shortcutterminal")
public final class KernelEventHandler {

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        TerminalKernel.onServerStart();
        klog("kernel: server '" + event.getServer().getMotd() + "' ready, port " + event.getServer().getPort());
    }

    @SubscribeEvent
    public static void onServerTickPre(ServerTickEvent.Pre event) {
        TerminalKernel.onTickStart();
    }

    @SubscribeEvent
    public static void onServerTickPost(ServerTickEvent.Post event) {
        TerminalKernel.onTickEnd();
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            klog("netd: player '" + p.getName().getString() + "' connected from "
                    + anonymizeIp(p.getIpAddress()) + " (ping " + p.connection.latency() + "ms)");
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            klog("netd: player '" + p.getName().getString() + "' disconnected");
        }
    }

    /** 日志中脱敏 IP（只保留前两段）。 */
    private static String anonymizeIp(String ip) {
        if (ip == null) return "unknown";
        String addr = ip.split(":")[0];
        String[] parts = addr.split("\\.");
        if (parts.length == 4) return parts[0] + "." + parts[1] + ".x.x";
        if (addr.length() > 16) return addr.substring(0, 16) + "…";
        return addr;
    }

    private KernelEventHandler() {}
}