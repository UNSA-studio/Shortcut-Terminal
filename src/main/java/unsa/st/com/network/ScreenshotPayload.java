package unsa.st.com.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import unsa.st.com.ShortcutTerminal;
import unsa.st.com.client.ClientVirtualFileSystem;

import java.io.File;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;

public record ScreenshotPayload(int angleOfView) implements CustomPacketPayload {
    public static final Type<ScreenshotPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ShortcutTerminal.MODID, "screenshot"));
    
    public static final StreamCodec<FriendlyByteBuf, ScreenshotPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ScreenshotPayload decode(FriendlyByteBuf buf) {
            return new ScreenshotPayload(buf.readVarInt());
        }
        @Override
        public void encode(FriendlyByteBuf buf, ScreenshotPayload payload) {
            buf.writeVarInt(payload.angleOfView);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleClient(final ScreenshotPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            
            // 切换视角
            int aov = Math.max(1, Math.min(4, payload.angleOfView));
            switch (aov) {
                case 1: mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON); break;
                case 2: mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); break;
                case 3: mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT); break;
                case 4: mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON); break; // 自由视角需要额外处理
            }
            
            // 截屏
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String fileName = "screenshot_" + timestamp + ".png";
            
            // 保存到本地 Program/screenshots/
            Path screenshotDir = mc.gameDirectory.toPath().resolve("Program").resolve("screenshots");
            try {
                java.nio.file.Files.createDirectories(screenshotDir);
                File screenshotFile = screenshotDir.resolve(fileName).toFile();
                mc.grabPanoramixScreenshot(screenshotDir.toFile(), 1024, 1024);
                mc.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§a[Screenshot] Saved as " + fileName), false);
            } catch (Exception e) {
                ShortcutTerminal.LOGGER.error("Screenshot failed", e);
            }
        });
    }
}
