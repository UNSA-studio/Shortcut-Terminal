package unsa.st.com.plugin;

import net.minecraft.server.MinecraftServer;
import unsa.st.com.ShortcutTerminal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class BinaryPluginManager {
    private static final String PLUGIN_FOLDER = "Binary file";
    private static Path pluginPath = null;
    private static List<String> availablePlugins = new ArrayList<>();

    public static void init() {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) {
            // server.getServerDirectory() 已经返回 Path，无需 toPath()
            pluginPath = server.getServerDirectory().resolve(PLUGIN_FOLDER);
        } else {
            pluginPath = Paths.get(PLUGIN_FOLDER);
        }
        try {
            Files.createDirectories(pluginPath);
        } catch (IOException e) {
            ShortcutTerminal.LOGGER.error("Failed to create Binary file directory", e);
        }
        refreshPlugins();
    }

    public static void refreshPlugins() {
        availablePlugins.clear();
        if (pluginPath == null) init();
        try (var stream = Files.newDirectoryStream(pluginPath, "*.class")) {
            for (Path entry : stream) {
                availablePlugins.add(entry.getFileName().toString());
            }
        } catch (IOException e) {
            ShortcutTerminal.LOGGER.error("Failed to scan plugins", e);
        }
    }

    public static List<String> getAvailablePlugins() {
        return availablePlugins;
    }

    public static int getPluginCount() {
        return availablePlugins.size();
    }

    public static boolean hasPlugin(String name) {
        return availablePlugins.contains(name);
    }
}
