package unsa.st.com.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import unsa.st.com.compute.HardwareSpec;
import unsa.st.com.compute.ProcessorCapability;
import unsa.st.com.menu.AssemblyBenchMenu;
import unsa.st.com.registry.ModBlockEntities;
import unsa.st.com.registry.ModItems;

/** Server-authoritative assembly. The output is already paid, never an extractable preview. */
public class AssemblyBenchBlockEntity extends BaseContainerBlockEntity {
    public static final int SLOT_SHELL = 0, SLOT_MOTHERBOARD = 1, SLOT_PROCESSOR = 2,
            SLOT_RAM = 3, SLOT_SSD = 4, SLOT_DISPLAY = 5, SLOT_GLUE = 6, SLOT_OUTPUT = 7, SIZE = 8;
    private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    public AssemblyBenchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ASSEMBLY_BENCH.get(), pos, state);
    }
    public static boolean slotAccepts(int slot, ItemStack stack) {
        if (stack.isEmpty()) return false;
        return switch (slot) {
            case SLOT_SHELL -> stack.is(ModItems.IRON_SHELL.get());
            case SLOT_MOTHERBOARD -> stack.is(ModItems.TERMINAL_MOTHERBOARD.get());
            case SLOT_PROCESSOR -> ProcessorCapability.levelOfProcessorItem(stack) > 0;
            case SLOT_RAM -> HardwareSpec.ramMbOfItem(stack) > 0;
            case SLOT_SSD -> HardwareSpec.ssdGbOfItem(stack) > 0;
            case SLOT_DISPLAY -> stack.is(ModItems.DISPLAY_SCREEN.get());
            case SLOT_GLUE -> stack.is(ModItems.BONDING_GLUE.get());
            default -> false;
        };
    }
    public boolean previousStagesFilled(int slot) {
        for (int i = 0; i < slot; i++) if (!slotAccepts(i, items.get(i))) return false;
        return true;
    }
    public boolean canRemoveStage(int slot) {
        if (slot == SLOT_OUTPUT) return true;
        for (int i = slot + 1; i < SLOT_OUTPUT; i++) if (!items.get(i).isEmpty()) return false;
        return true;
    }
    public boolean isAssembled() { return previousStagesFilled(SLOT_OUTPUT); }
    public boolean assemble() {
        if (level == null || level.isClientSide || !items.get(SLOT_OUTPUT).isEmpty() || !isAssembled()) return false;
        ItemStack panel = new ItemStack(ModItems.TERMINAL_PANEL.get());
        ProcessorCapability.install(panel, ProcessorCapability.levelOfProcessorItem(items.get(SLOT_PROCESSOR)));
        HardwareSpec.installRam(panel, HardwareSpec.ramMbOfItem(items.get(SLOT_RAM)));
        HardwareSpec.installSsd(panel, HardwareSpec.ssdGbOfItem(items.get(SLOT_SSD)));
        for (int i = 0; i < SLOT_OUTPUT; i++) items.get(i).shrink(1);
        items.set(SLOT_OUTPUT, panel);
        setChanged();
        return true;
    }
    @Override public int getContainerSize() { return SIZE; }
    @Override public int getMaxStackSize() { return 1; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot < SLOT_OUTPUT && items.get(SLOT_OUTPUT).isEmpty()
                && previousStagesFilled(slot) && slotAccepts(slot, stack);
    }
    @Override public ItemStack removeItem(int slot, int amount) {
        ItemStack result = net.minecraft.world.ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) setChanged();
        return result;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { return net.minecraft.world.ContainerHelper.takeItem(items, slot); }
    @Override public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (!stack.isEmpty()) stack.setCount(Math.min(stack.getCount(), getMaxStackSize()));
        setChanged();
    }
    @Override public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5) <= 64;
    }
    @Override public void clearContent() { items.clear(); setChanged(); }
    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> value) {
        for (int i = 0; i < SIZE; i++) items.set(i, i < value.size() ? value.get(i) : ItemStack.EMPTY);
    }
    @Override protected Component getDefaultName() { return Component.translatable("block.shortcutterminal.assembly_bench"); }
    @Override protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new AssemblyBenchMenu(id, inventory, this, worldPosition);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        net.minecraft.world.ContainerHelper.saveAllItems(tag, items, registries);
        tag.putBoolean("OutputCommitted", true);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        net.minecraft.world.ContainerHelper.loadAllItems(tag, items, registries);
        // Old builds persisted an unpaid preview alongside the inputs. Retain the inputs, not that duplicate.
        if (!tag.getBoolean("OutputCommitted") && isAssembled()) items.set(SLOT_OUTPUT, ItemStack.EMPTY);
    }
}
