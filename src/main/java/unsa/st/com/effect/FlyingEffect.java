package unsa.st.com.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.nbt.CompoundTag;

public class FlyingEffect extends MobEffect {
    public FlyingEffect() {
        super(MobEffectCategory.NEUTRAL, 0x55FFFF);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity instanceof Player player) {
            // 每秒上升5格 = 每 tick 上升 5/20 = 0.25 格
            player.setDeltaMovement(player.getDeltaMovement().x, 0.25, player.getDeltaMovement().z);
            player.noPhysics = true; // 移除碰撞箱
            player.hurtMarked = true; // 标记需要更新

            // 检查是否到达目标高度（目标高度存储在 PersistentData 中）
            CompoundTag data = player.getPersistentData();
            if (data.contains("flyTargetY")) {
                double targetY = data.getDouble("flyTargetY");
                if (player.getY() >= targetY) {
                    player.removeEffect(this);
                    player.noPhysics = false;
                    data.remove("flyTargetY");
                }
            }
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true; // 每 tick 都执行
    }
}
