package unsa.st.com.fakeplayer;

import net.minecraft.world.entity.MoverType;
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
}
