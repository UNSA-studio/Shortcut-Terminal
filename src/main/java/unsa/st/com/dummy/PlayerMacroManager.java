package unsa.st.com.dummy;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class PlayerMacroManager {
    private static ScheduledExecutorService scheduler;
    private static ScheduledFuture<?> currentTask;
    private static String currentOperate = "";
    private static long intervalMs = 3000;
    private static int stepIndex = 0;

    public static void startMacro(String operate, long interval) {
        stopMacro();
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
        Minecraft.getInstance().player.sendSystemMessage(Component.literal("§aPlayer macro started. Operate: " + operate + ", interval: " + interval + "ms"));
    }

    public static void stopMacro() {
        if (currentTask != null) {
            currentTask.cancel(false);
            currentTask = null;
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        releaseAll();
        Minecraft.getInstance().player.sendSystemMessage(Component.literal("§cPlayer macro stopped."));
    }

    private static void pressKey(char c) {
        int keyCode = charToGlfw(c);
        if (keyCode != org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN) {
            InputConstants.Key key = InputConstants.getKey(keyCode, 0);
            KeyMapping.set(key, true);
        }
    }

    private static void releaseKey(char c) {
        int keyCode = charToGlfw(c);
        if (keyCode != org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN) {
            InputConstants.Key key = InputConstants.getKey(keyCode, 0);
            KeyMapping.set(key, false);
        }
    }

    private static void releaseAll() {
        for (char c : new char[]{'w','a','s','d',' '}) {
            int keyCode = charToGlfw(c);
            if (keyCode != org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN) {
                InputConstants.Key key = InputConstants.getKey(keyCode, 0);
                KeyMapping.set(key, false);
            }
        }
    }

    private static int charToGlfw(char c) {
        switch (c) {
            case 'w': return org.lwjgl.glfw.GLFW.GLFW_KEY_W;
            case 'a': return org.lwjgl.glfw.GLFW.GLFW_KEY_A;
            case 's': return org.lwjgl.glfw.GLFW.GLFW_KEY_S;
            case 'd': return org.lwjgl.glfw.GLFW.GLFW_KEY_D;
            case ' ': return org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;
            default: return org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN;
        }
    }

    public static boolean isRunning() { return currentTask != null && !currentTask.isDone(); }
}
