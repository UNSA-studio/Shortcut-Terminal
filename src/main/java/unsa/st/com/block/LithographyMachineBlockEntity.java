package unsa.st.com.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import unsa.st.com.compute.ProcessorCapability;
import unsa.st.com.menu.LithographyMachineMenu;
import unsa.st.com.registry.ModBlockEntities;
import unsa.st.com.registry.ModItems;
import java.util.Random;

/** A wafer + a permanent mask => ten graded processors. One coil powers two batches. */
public class LithographyMachineBlockEntity extends BaseContainerBlockEntity {
    public static final int PROCESS_TICKS = 400, SIZE = 6, DATA_SIZE = 5;
    private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private final Random random = new Random();
    private int targetLevel, progress, fuelBatches;
    private boolean working;
    public final ContainerData data = new ContainerData() {
        @Override public int get(int i) { return switch (i) {
            case 0 -> progress; case 1 -> targetLevel; case 2 -> fuelBatches;
            case 3 -> isFormed() ? 1 : 0; case 4 -> working ? 1 : 0; default -> 0;
        }; }
        @Override public void set(int i, int value) { /* server-owned; client uses SimpleContainerData */ }
        @Override public int getCount() { return DATA_SIZE; }
    };
    public LithographyMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LITHOGRAPHY_MACHINE.get(), pos, state);
    }
    public static int maskLevelFromItem(ItemStack stack) {
        for (int i = 1; i <= 9; i++) if (stack.is(ModItems.LITHO_MASKS.get(i).get())) return i;
        return 0;
    }
    public boolean isFormed() {
        if (level == null) return false;
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            if (x == 0 && z == 0) continue;
            if (!(level.getBlockState(worldPosition.offset(x, 0, z)).getBlock() instanceof MachineCasingBlock)) return false;
        }
        return true;
    }
    private boolean outputsEmpty() {
        for (int i = 3; i < SIZE; i++) if (!items.get(i).isEmpty()) return false;
        return true;
    }
    private boolean validInputs() {
        return items.get(0).is(ModItems.LOGIC_WAFER.get()) && maskLevelFromItem(items.get(1)) > 0;
    }
    public void serverTick(Level lvl, BlockPos pos) {
        if (!isFormed() || !validInputs()) {
            if (working || progress != 0) { working = false; progress = 0; setChanged(); }
            return;
        }
        if (!working) {
            if (!outputsEmpty()) return;
            if (fuelBatches == 0) {
                if (!items.get(2).is(ModItems.POWER_COIL.get())) return;
                items.get(2).shrink(1);
                fuelBatches = 2;
            }
            targetLevel = maskLevelFromItem(items.get(1));
            working = true;
            progress = 0;
            setChanged();
        }
        if (maskLevelFromItem(items.get(1)) != targetLevel) { working = false; progress = 0; setChanged(); return; }
        progress++;
        if (progress >= PROCESS_TICKS && outputsEmpty()) {
            var batch = ProcessorCapability.produceBatch(targetLevel, random);
            // The yield table has at most three distinct grades. No world-item spill or output loss.
            for (int i = 0; i < batch.size(); i++) items.set(3 + i, batch.get(i));
            items.get(0).shrink(1);
            fuelBatches--;
            working = false;
            progress = 0;
        }
        setChanged();
    }
    public boolean isWorking() { return working; }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        if (working) return false;
        return switch (slot) {
            case 0 -> stack.is(ModItems.LOGIC_WAFER.get());
            case 1 -> maskLevelFromItem(stack) > 0;
            case 2 -> stack.is(ModItems.POWER_COIL.get());
            default -> false;
        };
    }
    @Override public int getContainerSize() { return SIZE; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int amount) {
        ItemStack result = net.minecraft.world.ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) setChanged();
        return result;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { return net.minecraft.world.ContainerHelper.takeItem(items, slot); }
    @Override public void setItem(int slot, ItemStack stack) { items.set(slot, stack); setChanged(); }
    @Override public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5) <= 64;
    }
    @Override public void clearContent() { items.clear(); setChanged(); }
    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> value) {
        for (int i = 0; i < SIZE; i++) items.set(i, i < value.size() ? value.get(i) : ItemStack.EMPTY);
    }
    @Override protected Component getDefaultName() { return Component.translatable("block.shortcutterminal.lithography_machine"); }
    @Override protected AbstractContainerMenu createMenu(int id, Inventory inv) {
        return new LithographyMachineMenu(id, inv, this, data, worldPosition);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        net.minecraft.world.ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("Progress", progress); tag.putInt("TargetLevel", targetLevel);
        tag.putInt("FuelBatches", fuelBatches); tag.putBoolean("Working", working);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        net.minecraft.world.ContainerHelper.loadAllItems(tag, items, registries);
        progress = Math.max(0, Math.min(PROCESS_TICKS, tag.getInt("Progress")));
        targetLevel = Math.max(0, Math.min(9, tag.getInt("TargetLevel")));
        fuelBatches = Math.max(0, Math.min(2, tag.getInt("FuelBatches")));
        working = tag.getBoolean("Working") && fuelBatches > 0;
    }
}
