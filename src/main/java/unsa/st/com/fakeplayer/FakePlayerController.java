package unsa.st.com.fakeplayer;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class FakePlayerController {
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private static ScheduledFuture<?> moveTask;

    public static void moveForward(FakePlayerEntity fp, double speed) {
        Vec3 look = fp.getLookAngle();
        fp.move(MoverType.SELF, new Vec3(look.x * speed, 0, look.z * speed));
    }

    public static void jump(FakePlayerEntity fp) {
        if (fp.onGround()) {
            fp.jumpFromGround();
        }
    }

    public static void attack(FakePlayerEntity fp) {
        fp.attack(fp.getTarget());
    }

    public static void lookAt(FakePlayerEntity fp, BlockPos target) {
        Vec3 pos = fp.position();
        Vec3 dir = new Vec3(target.getX() - pos.x, target.getY() - pos.y, target.getZ() - pos.z).normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float pitch = (float) Math.toDegrees(-Math.asin(dir.y));
        fp.setRot(yaw, pitch);
    }

    public static void startAutoWalk(FakePlayerEntity fp, double speed) {
        stopAutoWalk();
        moveTask = scheduler.scheduleAtFixedRate(() -> {
            if (!fp.isAlive() || !fp.isActive()) {
                stopAutoWalk();
                return;
            }
            moveForward(fp, speed);
        }, 0, 50, TimeUnit.MILLISECONDS);
    }

    public static void stopAutoWalk() {
        if (moveTask != null) {
            moveTask.cancel(false);
            moveTask = null;
        }
    }

    public static void setTarget(FakePlayerEntity fp, LivingEntity target) {
        fp.setTarget(target);
    }

    public static void clearTarget(FakePlayerEntity fp) {
        fp.setTarget(null);
    }
}
