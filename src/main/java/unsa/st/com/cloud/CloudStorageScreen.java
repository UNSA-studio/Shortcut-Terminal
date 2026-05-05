package unsa.st.com.cloud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

public class CloudStorageScreen extends Screen {
    private final UUID ownerUUID;
    private CloudStorageManager.CloudRepository repository;
    private int sortMode = 0; // 0=默认, 1=名字A-Z, 2=名字Z-A, 3=数量升序, 4=数量降序
    private static final int ITEMS_PER_PAGE = 28;
    private int currentPage = 0;

    public CloudStorageScreen(UUID ownerUUID) {
        super(Component.literal("Cloud Storage Manager"));
        this.ownerUUID = ownerUUID;
        this.repository = CloudStorageManager.getRepository(ownerUUID);
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // 排序按钮
        this.addRenderableWidget(Button.builder(Component.literal("Sort: Default"), btn -> {
            sortMode = (sortMode + 1) % 5;
            btn.setMessage(Component.literal(getSortModeName()));
        }).pos(centerX - 100, 20).size(200, 20).build());

        // 翻页按钮
        if (repository != null && repository.items.size() > ITEMS_PER_PAGE) {
            this.addRenderableWidget(Button.builder(Component.literal("<-"), btn -> {
                if (currentPage > 0) currentPage--;
            }).pos(centerX - 50, this.height - 40).size(40, 20).build());
            
            this.addRenderableWidget(Button.builder(Component.literal("->"), btn -> {
                if ((currentPage + 1) * ITEMS_PER_PAGE < repository.items.size()) currentPage++;
            }).pos(centerX + 10, this.height - 40).size(40, 20).build());
        }
    }

    private String getSortModeName() {
        return switch (sortMode) {
            case 0 -> "Sort: Default";
            case 1 -> "Sort: Name A-Z";
            case 2 -> "Sort: Name Z-A";
            case 3 -> "Sort: Count ↑";
            case 4 -> "Sort: Count ↓";
            default -> "Sort: Default";
        };
    }

    private List<CloudStorageManager.CloudItem> getSortedItems() {
        if (repository == null) return new ArrayList<>();
        List<CloudStorageManager.CloudItem> sorted = new ArrayList<>(repository.items);
        switch (sortMode) {
            case 1 -> sorted.sort(Comparator.comparing(i -> i.displayName));
            case 2 -> sorted.sort((a, b) -> b.displayName.compareTo(a.displayName));
            case 3 -> sorted.sort(Comparator.comparingLong(i -> i.count));
            case 4 -> sorted.sort((a, b) -> Long.compare(b.count, a.count));
        }
        return sorted;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        if (repository == null) {
            guiGraphics.drawCenteredString(this.font, "No repository found.", this.width / 2, this.height / 2, 0xFFFFFF);
            return;
        }

        List<CloudStorageManager.CloudItem> sortedItems = getSortedItems();
        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, sortedItems.size());

        int x = 30, y = 50;
        for (int i = startIndex; i < endIndex; i++) {
            CloudStorageManager.CloudItem item = sortedItems.get(i);
            int row = (i - startIndex) / 7;
            int col = (i - startIndex) % 7;
            int itemX = x + col * 40;
            int itemY = y + row * 40;

            // 绘制物品图标
            ItemStack stack = new ItemStack(Items.AIR);
            try {
                ResourceLocation loc = ResourceLocation.tryParse(item.itemId);
                if (loc != null) {
                    stack = new ItemStack(Items.byBlock(null));
                }
            } catch (Exception ignored) {}

            guiGraphics.renderItem(stack, itemX, itemY);
            guiGraphics.drawString(this.font, "64", itemX + 12, itemY + 12, 0xAAAAAA, true);

            // 鼠标悬停显示实际数量
            if (mouseX >= itemX && mouseX <= itemX + 16 && mouseY >= itemY && mouseY <= itemY + 16) {
                guiGraphics.renderTooltip(this.font, Component.literal(item.displayName + " x" + item.count), mouseX, mouseY);
            }
        }

        String pageInfo = "Page " + (currentPage + 1) + " / " + (Math.max(1, (sortedItems.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE));
        guiGraphics.drawCenteredString(this.font, pageInfo, this.width / 2, this.height - 60, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
