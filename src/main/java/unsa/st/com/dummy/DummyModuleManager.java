package unsa.st.com.dummy;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
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
    private static int stepIndex = 0;

    public static void startDummy(String name, boolean interactive, String statusId, String operate, long interval) {
        stopDummy();
        currentOperate = operate;
        intervalMs = interval;
        stepIndex = 0;
        scheduler = Executors.newSingleThreadScheduledExecutor();
        currentTask = scheduler.scheduleAtFixedRate(() -> {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null) return;
            if (currentOperate.isEmpty()) return;
            releaseAll();
            if (stepIndex >= currentOperate.length()) stepIndex = 0;
            char c = currentOperate.charAt(stepIndex);
            stepIndex++;
            pressKey(c);
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            releaseKey(c);
        }, 0, intervalMs, TimeUnit.MILLISECONDS);
        Minecraft.getInstance().player.sendSystemMessage(Component.literal("Dummy module started. Name: " + name + ", operate: " + operate));
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
        releaseAll();
    }

    private static void pressKey(char c) {
        int keyCode = charToGlfw(c);
        if (keyCode != GLFW.GLFW_KEY_UNKNOWN) {
            InputConstants.Key key = InputConstants.getKey(keyCode, 0);
            KeyMapping.set(key, true);
        }
    }

    private static void releaseKey(char c) {
        int keyCode = charToGlfw(c);
        if (keyCode != GLFW.GLFW_KEY_UNKNOWN) {
            InputConstants.Key key = InputConstants.getKey(keyCode, 0);
            KeyMapping.set(key, false);
        }
    }

    private static void releaseAll() {
        for (char c : new char[]{'w','a','s','d',' '}) {
            int keyCode = charToGlfw(c);
            if (keyCode != GLFW.GLFW_KEY_UNKNOWN) {
                InputConstants.Key key = InputConstants.getKey(keyCode, 0);
                KeyMapping.set(key, false);
            }
        }
    }

    private static int charToGlfw(char c) {
        switch (c) {
            case 'w': return GLFW.GLFW_KEY_W;
            case 'a': return GLFW.GLFW_KEY_A;
            case 's': return GLFW.GLFW_KEY_S;
            case 'd': return GLFW.GLFW_KEY_D;
            case ' ': return GLFW.GLFW_KEY_SPACE;
            default: return GLFW.GLFW_KEY_UNKNOWN;
        }
    }

    public static boolean isRunning() { return currentTask != null && !currentTask.isDone(); }
}
