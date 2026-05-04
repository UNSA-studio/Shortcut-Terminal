package unsa.st.com.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.nbt.CompoundTag;

public class DiggingEffect extends MobEffect {
    public DiggingEffect() {
        super(MobEffectCategory.NEUTRAL, 0xAA6600);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity instanceof Player player) {
            // 每秒下降5格
            player.setDeltaMovement(player.getDeltaMovement().x, -0.25, player.getDeltaMovement().z);
            player.noPhysics = true;
            player.hurtMarked = true;

            CompoundTag data = player.getPersistentData();
            if (data.contains("digTargetY")) {
                double targetY = data.getDouble("digTargetY");
                if (player.getY() <= targetY) {
                    player.removeEffect(this);
                    player.noPhysics = false;
                    data.remove("digTargetY");
                }
            }
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }
}
