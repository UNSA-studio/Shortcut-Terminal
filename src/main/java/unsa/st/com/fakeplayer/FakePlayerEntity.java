package unsa.st.com.fakeplayer;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayer;

public class FakePlayerEntity extends FakePlayer {
    public FakePlayerEntity(ServerLevel level, GameProfile profile) {
        super(level, profile);
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20.0D);
        this.setHealth(20.0F);
        this.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
    }
}
