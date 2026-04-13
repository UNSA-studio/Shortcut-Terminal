package unsa.st.com.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import unsa.st.com.gui.TerminalScreen;

public class TerminalPanelBlock extends Block {
    public TerminalPanelBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            // 打开自定义 GUI（需要后续实现 Screen）
            // 暂时使用简单消息，等 GUI 类完成后再替换
            serverPlayer.sendSystemMessage(Component.literal("终端面板已打开（GUI 开发中）"));
        }
        return InteractionResult.SUCCESS;
    }
}
