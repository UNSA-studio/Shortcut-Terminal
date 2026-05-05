package unsa.st.com.cloud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import unsa.st.com.ShortcutTerminal;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CloudStorageManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, CloudRepository> repositories = new ConcurrentHashMap<>();
    private static Path cloudBasePath;

    public static void init() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            cloudBasePath = server.getServerDirectory().resolve("Cloud Storage");
            try { Files.createDirectories(cloudBasePath); } catch (IOException e) {
                ShortcutTerminal.LOGGER.error("Failed to create cloud storage directory", e);
            }
        }
    }

    public static void createRepository(UUID uuid) {
        if (cloudBasePath == null) init();
        Path repoFile = cloudBasePath.resolve(uuid.toString() + ".stcr");
        CloudRepository repo = new CloudRepository();
        repo.ownerUUID = uuid;
        repo.createdAt = System.currentTimeMillis();
        repo.items = new ArrayList<>();
        repo.whitelist = new ArrayList<>();
        repositories.put(uuid, repo);
        saveRepository(uuid);
    }

    public static boolean hasRepository(UUID uuid) {
        if (cloudBasePath == null) init();
        Path repoFile = cloudBasePath.resolve(uuid.toString() + ".stcr");
        return Files.exists(repoFile);
    }

    public static CloudRepository getRepository(UUID uuid) {
        if (repositories.containsKey(uuid)) {
            return repositories.get(uuid);
        }
        if (hasRepository(uuid)) {
            loadRepository(uuid);
            return repositories.get(uuid);
        }
        return null;
    }

    public static void addItem(UUID uuid, ItemStack stack) {
        CloudRepository repo = getRepository(uuid);
        if (repo == null) return;
        
        String itemId = stack.getItem().getDescriptionId();
        Optional<CloudItem> existing = repo.items.stream()
                .filter(i -> i.itemId.equals(itemId))
                .findFirst();
        
        if (existing.isPresent()) {
            existing.get().count += stack.getCount();
        } else {
            CloudItem newItem = new CloudItem();
            newItem.itemId = itemId;
            newItem.displayName = stack.getDisplayName().getString();
            newItem.count = stack.getCount();
            repo.items.add(newItem);
        }
        saveRepository(uuid);
    }

    public static void removeItem(UUID uuid, String itemId, int count) {
        CloudRepository repo = getRepository(uuid);
        if (repo == null) return;
        
        repo.items.removeIf(item -> {
            if (item.itemId.equals(itemId)) {
                item.count -= count;
                return item.count <= 0;
            }
            return false;
        });
        saveRepository(uuid);
    }

    public static void addToWhitelist(UUID ownerUUID, UUID friendUUID) {
        CloudRepository repo = getRepository(ownerUUID);
        if (repo == null) return;
        if (!repo.whitelist.contains(friendUUID)) {
            repo.whitelist.add(friendUUID);
            saveRepository(ownerUUID);
        }
    }

    public static void removeFromWhitelist(UUID ownerUUID, UUID friendUUID) {
        CloudRepository repo = getRepository(ownerUUID);
        if (repo == null) return;
        repo.whitelist.remove(friendUUID);
        saveRepository(ownerUUID);
    }

    public static boolean isWhitelisted(UUID ownerUUID, UUID friendUUID) {
        CloudRepository repo = getRepository(ownerUUID);
        if (repo == null) return false;
        return ownerUUID.equals(friendUUID) || repo.whitelist.contains(friendUUID);
    }

    public static void saveRepository(UUID uuid) {
        if (cloudBasePath == null) init();
        CloudRepository repo = repositories.get(uuid);
        if (repo == null) return;
        try {
            Path repoFile = cloudBasePath.resolve(uuid.toString() + ".stcr");
            Files.writeString(repoFile, GSON.toJson(repo));
        } catch (IOException e) {
            ShortcutTerminal.LOGGER.error("Failed to save cloud repository", e);
        }
    }

    public static void loadRepository(UUID uuid) {
        if (cloudBasePath == null) init();
        Path repoFile = cloudBasePath.resolve(uuid.toString() + ".stcr");
        if (!Files.exists(repoFile)) return;
        try {
            String json = Files.readString(repoFile);
            CloudRepository repo = GSON.fromJson(json, CloudRepository.class);
            if (repo != null) {
                repositories.put(uuid, repo);
            }
        } catch (IOException e) {
            ShortcutTerminal.LOGGER.error("Failed to load cloud repository", e);
        }
    }

    public static class CloudRepository {
        public UUID ownerUUID;
        public long createdAt;
        public List<CloudItem> items;
        public List<UUID> whitelist;
    }

    public static class CloudItem {
        public String itemId;
        public String displayName;
        public long count;
    }
}
