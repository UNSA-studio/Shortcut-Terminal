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
            // 释放所有之前按下的键
            releaseAll();
            // 获取当前要按的字符
            if (stepIndex >= currentOperate.length()) stepIndex = 0;
            char c = currentOperate.charAt(stepIndex);
            stepIndex++;
            // 模拟按下
            pressKey(mc, c);
            // 短暂保持后释放（模拟按键点击）
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            releaseKey(mc, c);
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

    private static void pressKey(Minecraft mc, char c) {
        long window = mc.getWindow().getWindow();
        int key = charToGlfw(c);
        if (key != GLFW.GLFW_KEY_UNKNOWN) {
            // 通过 GLFW 直接发送按键事件
            GLFW.glfwPostEmptyEvent();
            // 使用 Minecraft 的键盘处理器
            KeyMapping.set(key, true);
        }
    }

    private static void releaseKey(Minecraft mc, char c) {
        int key = charToGlfw(c);
        if (key != GLFW.GLFW_KEY_UNKNOWN) {
            KeyMapping.set(key, false);
        }
    }

    private static void releaseAll() {
        for (char c : new char[]{'w','a','s','d',' '}) {
            int key = charToGlfw(c);
            if (key != GLFW.GLFW_KEY_UNKNOWN) {
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
