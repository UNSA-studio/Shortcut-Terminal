package unsa.st.com.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 制造台：右键打开装配 GUI，把铁壳子与全部配件按顺序装进去封合出终端面板。
 */
public class AssemblyBenchBlock extends BaseEntityBlock {

    public static final MapCodec<AssemblyBenchBlock> CODEC = simpleCodec(AssemblyBenchBlock::new);

    public AssemblyBenchBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AssemblyBenchBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof AssemblyBenchBlockEntity bench) {
                player.openMenu(bench, pos);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof AssemblyBenchBlockEntity bench) {
                for (int i = 0; i < AssemblyBenchBlockEntity.SIZE; i++) {
                    net.minecraft.world.item.ItemStack s = bench.getItem(i);
                    if (!s.isEmpty()) {
                        net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), s);
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** 供 GUI/提示使用的标题（保留 translatable 名称）。 */
    public static Component displayName() {
        return Component.translatable("block.shortcutterminal.assembly_bench");
    }
}
