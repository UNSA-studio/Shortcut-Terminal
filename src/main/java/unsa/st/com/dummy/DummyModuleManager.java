package unsa.st.com.dummy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class DummyModuleManager {
    private static ScheduledExecutorService scheduler;
    private static ScheduledFuture<?> currentTask;
    private static String currentOperate = "";
    private static long intervalMs = 3000;

    public static void startDummy(String name, boolean interactive, String statusId, String operate, long interval) {
        stopDummy();
        currentOperate = operate;
        intervalMs = interval;
        scheduler = Executors.newSingleThreadScheduledExecutor();
        currentTask = scheduler.scheduleAtFixedRate(() -> {
            if (Minecraft.getInstance().player == null) return;
            performActions(operate);
        }, 0, intervalMs, TimeUnit.MILLISECONDS);
        Minecraft.getInstance().player.sendSystemMessage(Component.literal("Dummy module started. Name: " + name));
    }

    public static void stopDummy() {
        if (currentTask != null) {
            currentTask.cancel(false);
            currentTask = null;
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    private static void performActions(String operate) {
        if (operate.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        long window = mc.getWindow().getWindow();
        // 简单的按键序列解析
        for (char c : operate.toCharArray()) {
            switch (c) {
                case 'w': simulateKey(window, GLFW.GLFW_KEY_W); break;
                case 'a': simulateKey(window, GLFW.GLFW_KEY_A); break;
                case 's': simulateKey(window, GLFW.GLFW_KEY_S); break;
                case 'd': simulateKey(window, GLFW.GLFW_KEY_D); break;
                case ' ': simulateKey(window, GLFW.GLFW_KEY_SPACE); break;
            }
        }
    }

    private static void simulateKey(long window, int key) {
        GLFW.glfwPostEmptyEvent();
        // 实际按键模拟需要使用 Minecraft 的键盘处理，此处仅示意
        // 完整实现需要通过 Mixin 或反射调用 KeyBinding
    }

    public static boolean isRunning() { return currentTask != null && !currentTask.isDone(); }
}
