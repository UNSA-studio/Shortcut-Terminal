package unsa.st.com.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import unsa.st.com.block.AssemblyBenchBlockEntity;
import unsa.st.com.registry.ModBlockEntities;
import unsa.st.com.registry.ModBlocks;

public class AssemblyBenchMenu extends AbstractContainerMenu {
    public static final int WIDTH = 230, HEIGHT = 216;
    public static final int[][] POSITIONS = {{16,40},{46,40},{76,40},{106,40},{136,40},{46,72},{76,72},{194,64}};
    private final Container inventory;
    private final ContainerLevelAccess access;
    // Client ALWAYS has the same eight slots, even before a chunk/BE is available.
    public AssemblyBenchMenu(int id, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(id, playerInv, new SimpleContainer(8), buf.readBlockPos());
    }
    public AssemblyBenchMenu(int id, Inventory playerInv, Container inventory, BlockPos pos) {
        super(ModBlockEntities.ASSEMBLY_BENCH_MENU.get(), id);
        checkContainerSize(inventory, 8);
        this.inventory = inventory;
        this.access = ContainerLevelAccess.create(playerInv.player.level(), pos);
        for (int i = 0; i < 8; i++) {
            final int stage = i;
            addSlot(new Slot(inventory, i, POSITIONS[i][0], POSITIONS[i][1]) {
                @Override public int getMaxStackSize() { return 1; }
                @Override public boolean mayPlace(ItemStack stack) {
                    return stage < 7 && inventory.getItem(7).isEmpty() && previousStagesFilled(stage)
                            && AssemblyBenchBlockEntity.slotAccepts(stage, stack);
                }
                @Override public boolean mayPickup(Player player) {
                    if (stage == 7) return true;
                    for (int j = stage + 1; j < 7; j++) if (!inventory.getItem(j).isEmpty()) return false;
                    return true;
                }
                @Override public void onTake(Player player, ItemStack stack) {
                    if (stage == 7 && !player.level().isClientSide) stack.onCraftedBy(player.level(), player, stack.getCount());
                    super.onTake(player, stack);
                }
            });
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(playerInv, 9 + row * 9 + col, 34 + col * 18, 124 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(playerInv, col, 34 + col * 18, 182));
    }
    public boolean previousStagesFilled(int stage) {
        for (int i = 0; i < stage; i++) if (!AssemblyBenchBlockEntity.slotAccepts(i, inventory.getItem(i))) return false;
        return true;
    }
    public int nextStage() {
        for (int i = 0; i < 7; i++) if (!AssemblyBenchBlockEntity.slotAccepts(i, inventory.getItem(i))) return i;
        return 7;
    }
    public boolean canAssemble() { return nextStage() == 7 && inventory.getItem(7).isEmpty(); }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (button != 0 || !stillValid(player) || !(inventory instanceof AssemblyBenchBlockEntity bench)) return false;
        boolean result = bench.assemble();
        if (result) broadcastChanges();
        return result;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        // Attach identity BEFORE transferring a copy to the player's inventory.
        if (index == 7 && !player.level().isClientSide) stack.onCraftedBy(player.level(), player, stack.getCount());
        ItemStack original = stack.copy();
        if (index < 8) {
            if (!moveItemStackTo(stack, 8, 44, true)) return ItemStack.EMPTY;
        } else {
            int stage = nextStage();
            if (stage >= 7 || !slots.get(stage).mayPlace(stack) || !moveItemStackTo(stack, stage, stage + 1, false))
                return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        ItemStack taken = original.copyWithCount(original.getCount() - stack.getCount());
        slot.onTake(player, taken);
        return original;
    }
    @Override public boolean stillValid(Player player) { return stillValid(access, player, ModBlocks.ASSEMBLY_BENCH.get()); }
}
