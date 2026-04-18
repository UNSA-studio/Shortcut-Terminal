package unsa.st.com.fakeplayer;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.UUID;

public class FakePlayerEntity extends FakePlayer {
    private final ServerLevel level;
    private boolean isActive = true;
    private String status = "idle";

    public FakePlayerEntity(ServerLevel level, GameProfile profile) {
        super(level, profile);
        this.level = level;
        // 设置基本属性
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20.0D);
        this.setHealth(20.0F);
        this.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
    }

    @Override
    public void tick() {
        super.tick();
        if (!isActive) return;
        // 假人的主动行为可以在这里扩展
    }

    public void setActive(boolean active) { this.isActive = active; }
    public boolean isActive() { return isActive; }
    public void setStatus(String status) { this.status = status; }
    public String getStatus() { return status; }
}
