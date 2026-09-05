package unsa.st.com.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import unsa.st.com.compute.ProcessorCapability;
import unsa.st.com.compute.YieldTable;
import unsa.st.com.menu.LithographyMachineMenu;
import unsa.st.com.registry.ModBlockEntities;
import unsa.st.com.registry.ModItems;

import java.util.List;
import java.util.Random;

/**
 * 光刻机方块实体：3×3 多方块核心。
 * 槽位：0=逻辑晶圆（输入）、1=掩膜（决定目标等级，不消耗）、2=电源线圈（燃料）、3=输出。
 * 加工 20s（400 ticks）一片晶圆 → 一批 10 颗 CPU（良品率掷骰）。
 */
public class LithographyMachineBlockEntity extends BaseContainerBlockEntity implements MenuProvider {
    public static final int PROCESS_TICKS = 400;
    /** 掩膜等级 = 目标等级。 */
    private int targetLevel = 0;
    private int progress = 0;
    private boolean working = false;
    private final Random random = new Random();

    /** 方块实体直接持有 NonNullList（BaseContainerBlockEntity 约定 getItems/setItems 抽象）。 */
    private final net.minecraft.core.NonNullList<ItemStack> items = net.minecraft.core.NonNullList.withSize(4, ItemStack.EMPTY);

    public LithographyMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LITHOGRAPHY_MACHINE.get(), pos, state);
    }

    // ==================== 多方块成型 ====================

    /** 检查 3×3 平放成型：自身以外的 8 格全是外壳。 */
    public boolean isFormed() {
        if (level == null) return false;
        BlockPos origin = getBlockPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockState st = level.getBlockState(origin.offset(dx, 0, dz));
                if (!(st.getBlock() instanceof unsa.st.com.block.MachineCasingBlock)) return false;
            }
        }
        return true;
    }

    // ==================== 服务器 tick ====================

    public void serverTick(Level lvl, BlockPos pos) {
        if (!isFormed()) { working = false; progress = 0; return; }
        if (!working) tryStart();
        if (working) {
            progress++;
            if (progress >= PROCESS_TICKS) finishBatch(lvl, pos);
        }
        // 进度同步给打开的 GUI（低频广播）
        if (progress % 20 == 0) setChanged();
    }

    private void tryStart() {
        if (this.items.get(0).is(ModItems.LOGIC_WAFER.get())
                && this.items.get(2).is(ModItems.POWER_COIL.get())) {
            int maskLevel = maskLevelFromItem(this.items.get(1));
            if (maskLevel >= 1) {
                targetLevel = maskLevel;
                working = true;
                progress = 0;
            }
        }
    }

    private void finishBatch(Level lvl, BlockPos pos) {
        // 消耗 1 晶圆 + 1 线圈（1 线圈 = 2 批能量：偶数批耗线圈）
        this.items.set(0, splitDecrement(this.items.get(0), 1));
        if (progress / PROCESS_TICKS % 2 == 0) this.items.set(2, splitDecrement(this.items.get(2), 1));
        // 产出
        List<ItemStack> batch = ProcessorCapability.produceBatch(targetLevel, random);
        ItemStack out = this.items.get(3);
        for (ItemStack produced : batch) {
            // 输出槽尽量塞
            if (out.isEmpty()) { this.items.set(3, produced); out = this.items.get(3); }
            else if (ItemStack.isSameItemSameComponents(out, produced) && out.getCount() + produced.getCount() <= out.getMaxStackSize()) {
                out.grow(produced.getCount());
            } else {
                lvl.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
                        lvl, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, produced));
            }
        }
        Component summary = Component.literal(YieldTable.batchSummary(targetLevel,
                YieldTable.rollBatch(targetLevel, random)));
        // 通知附近玩家（简化：直接给全员范围内广播）
        Player nearest = lvl.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8.0, false);
        if (nearest != null) nearest.displayClientMessage(summary, true);
        working = false;
        progress = 0;
        setChanged();
    }

    /** 掩膜物品 → 等级（lithography_mask_l3 → 3）。 */
    private int maskLevelFromItem(ItemStack mask) {
        if (mask.isEmpty()) return 0;
        String s = mask.getItem().toString().toLowerCase(java.util.Locale.ROOT);
        for (int l = 9; l >= 1; l--) {
            if (s.endsWith("_l" + l)) return l;
        }
        return 0;
    }

    // ==================== Container 实现 ====================

    @Override public int getContainerSize() { return this.items.size(); }
    @Override public boolean isEmpty() {
        for (ItemStack s : this.items) if (!s.isEmpty()) return false;
        return true;
    }
    @Override public ItemStack getItem(int slot) { return this.items.get(slot); }
    @Override public ItemStack removeItem(int slot, int amount) {
        ItemStack taken = net.minecraft.world.ContainerHelper.removeItem(this.items, slot, amount);
        if (!taken.isEmpty()) this.setChanged();
        return taken;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) {
        return net.minecraft.world.ContainerHelper.takeItem(this.items, slot);
    }
    @Override public void setItem(int slot, ItemStack stack) {
        this.items.set(slot, stack);
        this.setChanged();
    }
    @Override public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0;
    }
    @Override public void clearContent() { fillEmpty(); }

    /** 清空所有槽位。 */
    private void fillEmpty() {
        for (int i = 0; i < this.items.size(); i++) this.items.set(i, ItemStack.EMPTY);
        this.setChanged();
    }

    /** 槽位减 amount（单堆内扣，耗尽返回 EMPTY）。 */
    private static ItemStack splitDecrement(ItemStack stack, int amount) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        stack.shrink(amount);
        return stack.isEmpty() ? ItemStack.EMPTY : stack;
    }

    /** BaseContainerBlockEntity 抽象：暴露内部 NonNullList。 */
    @Override
    protected net.minecraft.core.NonNullList<ItemStack> getItems() { return this.items; }

    /** BaseContainerBlockEntity 抽象：读档时整体替换。 */
    @Override
    protected void setItems(net.minecraft.core.NonNullList<ItemStack> newItems) {
        for (int i = 0; i < this.items.size(); i++) {
            this.items.set(i, i < newItems.size() ? newItems.get(i) : ItemStack.EMPTY);
        }
    }

    @Override protected Component getDefaultName() {
        return Component.translatable("block.shortcutterminal.lithography_machine");
    }

    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory playerInv) {
        return new LithographyMachineMenu(windowId, playerInv, worldPosition);
    }

    // ==================== NBT ====================

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        net.minecraft.world.ContainerHelper.saveAllItems(tag, this.items, registries);
        tag.putInt("Progress", progress);
        tag.putBoolean("Working", working);
        tag.putInt("TargetLevel", targetLevel);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        net.minecraft.world.ContainerHelper.loadAllItems(tag, this.items, registries);
        progress = tag.getInt("Progress");
        working = tag.getBoolean("Working");
        targetLevel = tag.getInt("TargetLevel");
    }

    // ==================== GUI 数据访问 ====================

    public int getProgress() { return progress; }
    public int getProcessTicks() { return PROCESS_TICKS; }
    public int getTargetLevel() { return targetLevel; }
    public net.minecraft.core.NonNullList<ItemStack> getMachineItems() { return this.items; }
}