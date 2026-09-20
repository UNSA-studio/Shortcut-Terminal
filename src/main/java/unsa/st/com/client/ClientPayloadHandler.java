package unsa.st.com.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import unsa.st.com.ShortcutTerminal;
import unsa.st.com.gui.TerminalScreen;
import unsa.st.com.network.SyncFileSystemPacket;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * 客户端专属的数据包处理逻辑。
 *
 * <p>dist 隔离：网络 payload 类（common）只做参数转发，所有 net.minecraft.client 访问
 * 都集中在这个客户端类里。专用服务器不会加载本类，因此不会触发 NeoForge 的 dist 校验。</p>
 */
public final class ClientPayloadHandler {
    private ClientPayloadHandler() {}

    /** TriggerSyncPayload：本地 ↔ 服务端文件同步。 */
    public static void onTriggerSync(boolean toServer) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (toServer) {
            mc.player.displayClientMessage(Component.literal("§e[Sync] File sync started, please do not shut down the game."), false);
            UUID uuid = mc.player.getUUID();
            Map<String, String> snapshot = ClientVirtualFileSystem.getFileSystemSnapshot(uuid.toString());
            PacketDistributor.sendToServer(new SyncFileSystemPacket(uuid.toString(), snapshot));
            mc.player.displayClientMessage(Component.literal("§a[Sync] File synchronization completed."), false);
            if (TerminalScreen.getInstance() != null) {
                TerminalScreen.getInstance().addOutputLine("§a[Sync] Auto-synced local files to server.");
            }
        } else {
            if (TerminalScreen.getInstance() != null) {
                TerminalScreen.getInstance().requestSyncFromServer();
            }
        }
    }

    /** ServerSyncDataPayload：收到服务端文件列表并写入本地。 */
    public static void onServerSyncData(Map<String, String> files) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.literal("§a[Sync] File synchronization completed."), false);
        }
        TerminalScreen.receiveServerSyncData("", files);
    }

    /** CommandResultPayload：把服务端命令结果写进终端。 */
    public static void onCommandResult(String result) {
        TerminalScreen.receiveCommandResult(result);
    }

    /** ScreenshotPayload：切换视角并截取全景图。 */
    public static void onScreenshot(int angleOfView) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int aov = Math.max(1, Math.min(4, angleOfView));
        switch (aov) {
            case 1: mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON); break;
            case 2: mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); break;
            case 3: mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT); break;
            default: mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON); break;
        }
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        Path screenshotDir = mc.gameDirectory.toPath().resolve("Program").resolve("screenshots");
        try {
            Files.createDirectories(screenshotDir);
            mc.grabPanoramixScreenshot(screenshotDir.toFile(), 1024, 1024);
            mc.player.displayClientMessage(Component.literal(
                    "§a[Screenshot] Saved panorama files in Program/screenshots (session " + timestamp + ")"), false);
        } catch (Exception e) {
            ShortcutTerminal.LOGGER.error("Screenshot failed", e);
        }
    }
}
