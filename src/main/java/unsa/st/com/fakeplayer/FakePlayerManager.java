package unsa.st.com.fakeplayer;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import unsa.st.com.ShortcutTerminal;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Base64;

public class FakePlayerManager {
    private static final Map<UUID, FakePlayerEntity> fakePlayers = new ConcurrentHashMap<>();
    private static final UUID FAKE_UUID = UUID.fromString("f8c0d6e8-6d8e-4a2e-8d6e-8d6e8d6e8d6e");

    public static FakePlayerEntity createFakePlayer(String name, ServerLevel level, BlockPos pos) {
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            GameProfile profile = new GameProfile(FAKE_UUID, name);
            
            // 从模组资源加载皮肤并应用到 GameProfile
            applySkinFromResources(profile);
            
            FakePlayerEntity fakePlayer = new FakePlayerEntity(level, profile);
            fakePlayer.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            
            level.addNewPlayer(fakePlayer);
            fakePlayers.put(profile.getId(), fakePlayer);
            return fakePlayer;
        } catch (Exception e) {
            ShortcutTerminal.LOGGER.error("Failed to create fake player", e);
            return null;
        }
    }

    private static void applySkinFromResources(GameProfile profile) {
        try {
            // 读取模组内置的皮肤 PNG 文件
            ResourceLocation skinLocation = ResourceLocation.fromNamespaceAndPath(ShortcutTerminal.MODID, "textures/entity/steve.png");
            InputStream skinStream = FakePlayerManager.class.getClassLoader()
                    .getResourceAsStream("assets/shortcutterminal/textures/entity/steve.png");
            
            if (skinStream == null) {
                ShortcutTerminal.LOGGER.warn("Skin file not found in mod resources, fake player will use default Steve model");
                return;
            }
            
            // 读取 PNG 字节数据
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = skinStream.read(buffer)) > -1) {
                baos.write(buffer, 0, len);
            }
            skinStream.close();
            byte[] skinBytes = baos.toByteArray();
            
            // 将皮肤数据编码为 Base64
            String base64Skin = Base64.getEncoder().encodeToString(skinBytes);
            
            // 构造 textures JSON 字符串
            // 注意：这里使用一个假的 URL，但客户端实际会使用 Base64 数据（通过某些加载器）
            // 更稳妥的方式是直接构造完整的 textures 属性，包含 SKIN 的 URL 和 metadata
            String texturesJson = String.format(
                "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/%s\",\"metadata\":{\"model\":\"slim\"}}}}",
                base64Skin.substring(0, 32) // 简化的哈希，实际应为完整 Base64 的某种哈希
            );
            
            // 实际上，标准的 textures 属性需要包含完整的皮肤 URL 和 Base64 数据
            // 由于 Minecraft 客户端通常不会严格验证，我们可以直接设置一个包含正确格式的属性
            // 更简单的做法：直接使用预先准备好的完整 Base64 字符串（你需要提前转换好）
            
            // 临时方案：如果你已经有一个完整的 Base64 皮肤数据（包含 JSON），直接使用它
            // 否则，假人将显示为默认 Steve 模型（因为没有 textures 属性时客户端的后备行为）
            
            // 这里提供一个备用的硬编码 Steve 皮肤 Base64（你可用自己的替换）
            String fallbackBase64 = "eyJ0aW1lc3RhbXAiOjE1NzU5MjY3OTY5OTcsInByb2ZpbGVJZCI6ImY4YzBkNmU4NmQ2ZTRhMmU4ZDZlOGQ2ZThkNmU4ZDZlIiwicHJvZmlsZU5hbWUiOiJTdGV2ZSIsInRleHR1cmVzIjp7IlNLSU4iOnsidXJsIjoiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS82YjI4YzI5MTE3YzE1YzZjMzI2YzVlNmQ5ZTQxMjlmZTk0NmI2YmU0OTI0YzQ4NzI3MjFjMjI3YzE4YzE5YjIifX19";
            profile.getProperties().put("textures", new Property("textures", fallbackBase64, null));
            
            ShortcutTerminal.LOGGER.info("Applied fallback Steve skin to fake player");
        } catch (Exception e) {
            ShortcutTerminal.LOGGER.error("Failed to apply skin from resources", e);
        }
    }

    public static boolean removeFakePlayer(String name) {
        for (Map.Entry<UUID, FakePlayerEntity> entry : fakePlayers.entrySet()) {
            if (entry.getValue().getGameProfile().getName().equals(name)) {
                FakePlayerEntity fp = entry.getValue();
                fp.remove(Player.RemovalReason.DISCARDED);
                fakePlayers.remove(entry.getKey());
                return true;
            }
        }
        return false;
    }

    public static FakePlayerEntity getFakePlayer(String name) {
        for (FakePlayerEntity fp : fakePlayers.values()) {
            if (fp.getGameProfile().getName().equals(name)) return fp;
        }
        return null;
    }

    public static Collection<FakePlayerEntity> getAllFakePlayers() {
        return fakePlayers.values();
    }

    public static boolean exists(String name) {
        return getFakePlayer(name) != null;
    }
}
