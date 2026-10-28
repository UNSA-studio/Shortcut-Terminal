package unsa.st.com.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import unsa.st.com.block.LithographyMachineBlockEntity;
import unsa.st.com.registry.ModBlockEntities;
import unsa.st.com.registry.ModBlocks;
import unsa.st.com.registry.ModItems;

public class LithographyMachineMenu extends AbstractContainerMenu {
    public static final int WIDTH = 212, HEIGHT = 200;
    public static final int[][] POSITIONS = {{14,40},{46,40},{78,40},{130,40},{154,40},{178,40}};
    private final Container inventory;
    private final ContainerData data;
    private final ContainerLevelAccess access;
    public LithographyMachineMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, new SimpleContainer(6), new SimpleContainerData(5), buf.readBlockPos());
    }
    public LithographyMachineMenu(int id, Inventory inv, Container inventory, ContainerData data, BlockPos pos) {
        super(ModBlockEntities.LITHOGRAPHY_MACHINE_MENU.get(), id);
        checkContainerSize(inventory, 6);
        checkContainerDataCount(data, 5);
        this.inventory = inventory; this.data = data;
        this.access = ContainerLevelAccess.create(inv.player.level(), pos);
        for (int i = 0; i < 6; i++) {
            final int slot = i;
            addSlot(new Slot(inventory, i, POSITIONS[i][0], POSITIONS[i][1]) {
                @Override public boolean mayPlace(ItemStack stack) {
                    if (isWorking()) return false;
                    return switch (slot) {
                        case 0 -> stack.is(ModItems.LOGIC_WAFER.get());
                        case 1 -> LithographyMachineBlockEntity.maskLevelFromItem(stack) > 0;
                        case 2 -> stack.is(ModItems.POWER_COIL.get());
                        default -> false;
                    };
                }
                @Override public boolean mayPickup(Player player) { return slot >= 3 || !isWorking(); }
            });
        }
        addDataSlots(data);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inv, 9 + row * 9 + col, 26 + col * 18, 116 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 26 + col * 18, 174));
    }
    public int getProgress() { return data.get(0); }
    public int getTargetLevel() { return data.get(1); }
    public int getFuelBatches() { return data.get(2); }
    public boolean isFormed() { return data.get(3) != 0; }
    public boolean isWorking() { return data.get(4) != 0; }
    public boolean outputBlocked() { for (int i = 3; i < 6; i++) if (!inventory.getItem(i).isEmpty()) return true; return false; }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        if (index < 6) {
            if (!moveItemStackTo(stack, 6, 42, true)) return ItemStack.EMPTY;
        } else {
            int target = stack.is(ModItems.LOGIC_WAFER.get()) ? 0
                    : LithographyMachineBlockEntity.maskLevelFromItem(stack) > 0 ? 1
                    : stack.is(ModItems.POWER_COIL.get()) ? 2 : -1;
            if (target >= 0) {
                if (!slots.get(target).mayPlace(stack) || !moveItemStackTo(stack, target, target + 1, false)) return ItemStack.EMPTY;
            } else if (index < 33) {
                if (!moveItemStackTo(stack, 33, 42, false)) return ItemStack.EMPTY;
            } else if (!moveItemStackTo(stack, 6, 33, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, original.copyWithCount(original.getCount() - stack.getCount()));
        return original;
    }
    @Override public boolean stillValid(Player player) { return stillValid(access, player, ModBlocks.LITHOGRAPHY_MACHINE.get()); }
}
