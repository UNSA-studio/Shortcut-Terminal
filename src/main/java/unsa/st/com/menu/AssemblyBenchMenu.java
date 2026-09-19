package unsa.st.com.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import unsa.st.com.block.AssemblyBenchBlockEntity;
import unsa.st.com.registry.ModBlockEntities;

/**
 * 制造台 GUI 菜单：7 个有序装配槽 + 1 个输出槽 + 玩家背包。
 * 顺序约束：只有前面所有槽已填，后面的槽才接受物品。
 */
public class AssemblyBenchMenu extends AbstractContainerMenu {
    public final AssemblyBenchBlockEntity bench;

    /** 客户端工厂（IContainerFactory）。 */
    public AssemblyBenchMenu(int windowId, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(windowId, playerInv, BlockPos.STREAM_CODEC.decode(buf));
    }

    public AssemblyBenchMenu(int windowId, Inventory playerInv, BlockPos pos) {
        super(ModBlockEntities.ASSEMBLY_BENCH_MENU.get(), windowId);
        BlockEntity be = playerInv.player.level().getBlockEntity(pos);
        this.bench = be instanceof AssemblyBenchBlockEntity b ? b : null;

        if (this.bench != null) {
            for (int i = 0; i <= AssemblyBenchBlockEntity.SLOT_GLUE; i++) {
                final int stage = i;
                addSlot(new Slot(this.bench, i, 8 + i * 20, 20) {
                    @Override public boolean mayPlace(ItemStack s) {
                        return bench != null
                                && bench.previousStagesFilled(stage)
                                && AssemblyBenchBlockEntity.slotAccepts(stage, s);
                    }
                });
            }
            addSlot(new Slot(this.bench, AssemblyBenchBlockEntity.SLOT_OUTPUT, 152, 20) {
                @Override public boolean mayPlace(ItemStack s) { return false; }
            });
        }

        // 玩家背包 27 + 热栏 9
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(playerInv, 9 + row * 9 + col, 8 + col * 18, 84 + row * 18));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        final int machineSlots = 8;
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, machineSlots + 36, true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            if (bench != null) {
                for (int i = 0; i <= AssemblyBenchBlockEntity.SLOT_GLUE && !moved; i++) {
                    if (bench.getItem(i).isEmpty()
                            && bench.previousStagesFilled(i)
                            && AssemblyBenchBlockEntity.slotAccepts(i, stack)) {
                        moved = moveItemStackTo(stack, i, i + 1, false);
                    }
                }
            }
            if (!moved && !moveItemStackTo(stack, 0, machineSlots, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return bench != null && bench.stillValid(player);
    }
}
