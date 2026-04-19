package unsa.st.com.fakeplayer;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayer;

public class FakePlayerEntity extends FakePlayer {
    private final ServerLevel level;
    private boolean isActive = true;

    public FakePlayerEntity(ServerLevel level, GameProfile profile) {
        super(level, profile);
        this.level = level;
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20.0D);
        this.setHealth(20.0F);
        this.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        // 不设置 connection，保持为 null
    }

    @Override
    public void tick() {
        super.tick();
        if (!isActive) return;
    }

    public void setActive(boolean active) { this.isActive = active; }
    public boolean isActive() { return isActive; }
}
