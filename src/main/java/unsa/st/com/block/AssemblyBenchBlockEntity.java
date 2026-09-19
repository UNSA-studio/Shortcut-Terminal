package unsa.st.com.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import unsa.st.com.compute.HardwareSpec;
import unsa.st.com.menu.AssemblyBenchMenu;
import unsa.st.com.registry.ModBlockEntities;
import unsa.st.com.registry.ModItems;

/**
 * 制造台方块实体：把配件按顺序装进铁壳子，最后涂胶封合。
 *
 * <p>槽位顺序（必须先完成前一步才能放下一步）：
 * 0 铁壳子 → 1 主板 → 2 处理器 → 3 RAM → 4 SSD → 5 显示器 → 6 缝合胶液 → 7 输出。</p>
 */
public class AssemblyBenchBlockEntity extends BaseContainerBlockEntity implements MenuProvider {
    public static final int SLOT_SHELL = 0;
    public static final int SLOT_MOTHERBOARD = 1;
    public static final int SLOT_PROCESSOR = 2;
    public static final int SLOT_RAM = 3;
    public static final int SLOT_SSD = 4;
    public static final int SLOT_DISPLAY = 5;
    public static final int SLOT_GLUE = 6;
    public static final int SLOT_OUTPUT = 7;
    public static final int SIZE = 8;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

    public AssemblyBenchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ASSEMBLY_BENCH.get(), pos, state);
    }

    /** 槽位 stage 是否接受该物品（类型校验）。 */
    public static boolean slotAccepts(int stage, ItemStack s) {
        if (s == null || s.isEmpty()) return false;
        switch (stage) {
            case SLOT_SHELL: return s.is(ModItems.IRON_SHELL.get());
            case SLOT_MOTHERBOARD: return s.is(ModItems.TERMINAL_MOTHERBOARD.get());
            case SLOT_PROCESSOR: return s.getItem().toString().contains("processor_l");
            case SLOT_RAM: return HardwareSpec.ramMbOfItem(s) > 0;
            case SLOT_SSD: return HardwareSpec.ssdGbOfItem(s) > 0;
            case SLOT_DISPLAY: return s.is(ModItems.DISPLAY_SCREEN.get());
            case SLOT_GLUE: return s.is(ModItems.BONDING_GLUE.get());
            default: return false;
        }
    }

    /** 前 stage 个槽是否已填（顺序装配约束）。 */
    public boolean previousStagesFilled(int stage) {
        for (int i = 0; i < stage; i++) if (items.get(i).isEmpty()) return false;
        return true;
    }

    /** 7 个输入槽是否全部按类型合法填满。 */
    public boolean isAssembled() {
        for (int i = 0; i <= SLOT_GLUE; i++) {
            if (!slotAccepts(i, items.get(i))) return false;
        }
        return true;
    }

    /** 输入齐了就产出成品；输入被破坏则清掉成品。 */
    private void refreshOutput() {
        if (isAssembled()) {
            if (items.get(SLOT_OUTPUT).isEmpty()) {
                items.set(SLOT_OUTPUT, new ItemStack(ModItems.TERMINAL_PANEL.get()));
            }
        } else if (items.get(SLOT_OUTPUT).is(ModItems.TERMINAL_PANEL.get())) {
            items.set(SLOT_OUTPUT, ItemStack.EMPTY);
        }
        setChanged();
    }

    /** 取走成品时消耗全部输入各 1。 */
    private void consumeInputs() {
        for (int i = 0; i <= SLOT_GLUE; i++) {
            ItemStack s = items.get(i);
            if (!s.isEmpty()) {
                s.shrink(1);
                if (s.isEmpty()) items.set(i, ItemStack.EMPTY);
            }
        }
    }

    // ==================== Container 实现 ====================

    @Override public int getContainerSize() { return SIZE; }
    @Override public boolean isEmpty() {
        for (ItemStack s : items) if (!s.isEmpty()) return false;
        return true;
    }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }

    @Override public ItemStack removeItem(int slot, int amount) {
        ItemStack taken = net.minecraft.world.ContainerHelper.removeItem(items, slot, amount);
        if (slot == SLOT_OUTPUT && !taken.isEmpty()) consumeInputs();
        if (!taken.isEmpty()) refreshOutput();
        return taken;
    }

    @Override public ItemStack removeItemNoUpdate(int slot) {
        ItemStack taken = net.minecraft.world.ContainerHelper.takeItem(items, slot);
        if (slot == SLOT_OUTPUT && !taken.isEmpty()) consumeInputs();
        refreshOutput();
        return taken;
    }

    @Override public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
        refreshOutput();
    }

    @Override public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override public void clearContent() {
        for (int i = 0; i < SIZE; i++) items.set(i, ItemStack.EMPTY);
        setChanged();
    }

    @Override protected NonNullList<ItemStack> getItems() { return items; }

    @Override protected void setItems(NonNullList<ItemStack> newItems) {
        for (int i = 0; i < SIZE; i++) {
            items.set(i, i < newItems.size() ? newItems.get(i) : ItemStack.EMPTY);
        }
    }

    @Override protected Component getDefaultName() {
        return Component.translatable("block.shortcutterminal.assembly_bench");
    }

    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory playerInv) {
        return new AssemblyBenchMenu(windowId, playerInv, worldPosition);
    }

    // ==================== NBT ====================

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        net.minecraft.world.ContainerHelper.saveAllItems(tag, items, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        net.minecraft.world.ContainerHelper.loadAllItems(tag, items, registries);
    }
}
