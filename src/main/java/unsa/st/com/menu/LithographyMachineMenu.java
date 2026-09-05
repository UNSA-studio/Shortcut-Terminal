package unsa.st.com.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import unsa.st.com.block.LithographyMachineBlockEntity;
import unsa.st.com.registry.ModBlockEntities;
import unsa.st.com.registry.ModItems;

/**
 * 光刻机 GUI 菜单：3 机器槽 + 玩家背包。
 * 机器槽：0 晶圆(输入)、1 掩膜、2 电源线圈、3 输出(只出不进)。
 */
public class LithographyMachineMenu extends AbstractContainerMenu {
    public final LithographyMachineBlockEntity machine;
    private final ContainerLevelAccess access;

    /** 客户端工厂（IContainerFactory）。 */
    /** 客户端工厂（IContainerFactory）。 */
    public LithographyMachineMenu(int windowId, Inventory playerInv, net.minecraft.network.RegistryFriendlyByteBuf buf) {
        this(windowId, playerInv, BlockPos.STREAM_CODEC.decode(buf));
    }

    public LithographyMachineMenu(int windowId, Inventory playerInv, BlockPos pos) {
        super(ModBlockEntities.LITHOGRAPHY_MACHINE_MENU.get(), windowId);
        this.access = ContainerLevelAccess.create(playerInv.player.level(), pos);
        Level lvl = playerInv.player.level();
        BlockEntity be = lvl.getBlockEntity(pos);
        this.machine = be instanceof LithographyMachineBlockEntity m ? m : null;

        if (machine != null) {
            addSlot(new Slot(machine.getContainer(), 0, 44, 35));  // wafer
            addSlot(new Slot(machine.getContainer(), 1, 80, 35));  // mask
            addSlot(new Slot(machine.getContainer(), 2, 116, 35)); // power coil
            addSlot(new Slot(machine.getContainer(), 3, 152, 35) { // output (extract only)
                @Override public boolean mayPlace(ItemStack stack) { return false; }
                @Override public boolean mayPickup(Player player) { return true; }
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
        // 机器槽区间 [0,3]，玩家背包 [4,39]
        if (index < 4) {
            if (!moveItemStackTo(stack, 4, 40, true)) return ItemStack.EMPTY;
        } else {
            // 快捷投放：晶圆→0，掩膜→1，线圈→2
            if (stack.is(ModItems.LOGIC_WAFER.get())) {
                if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
            } else if (stack.getItem().toString().endsWith("lithography_mask_l1")
                    || stack.getItem().toString().contains("lithography_mask_l")) {
                if (!moveItemStackTo(stack, 1, 2, false)) return ItemStack.EMPTY;
            } else if (stack.is(ModItems.POWER_COIL.get())) {
                if (!moveItemStackTo(stack, 2, 3, false)) return ItemStack.EMPTY;
            } else {
                if (!moveItemStackTo(stack, 0, 3, false)) return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return machine != null && machine.stillValid(player);
    }
}