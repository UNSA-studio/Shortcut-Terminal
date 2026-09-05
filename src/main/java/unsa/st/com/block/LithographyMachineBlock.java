package unsa.st.com.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 光刻机控制器：3×3 平放（自身 + 8 外壳）成型后右键打开 GUI。
 * 加工 20s/批，消耗 1 逻辑晶圆 + 电源线圈能量，按掩膜等级掷良品率。
 */
public class LithographyMachineBlock extends BaseEntityBlock {

    public LithographyMachineBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LithographyMachineBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, ModBlockEntities.LITHOGRAPHY_MACHINE.get(),
                (lvl, pos, st, be) -> be.serverTick(lvl, pos));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof LithographyMachineBlockEntity machine) {
                if (!machine.isFormed()) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            "Multiblock incomplete: surround this controller with 8 Machine Casings (flat 3x3)."), true);
                    return InteractionResult.CONSUME;
                }
                player.openMenu(machine, pos);
            }
        }
        return InteractionResult.SUCCESS;
    }
}