package unsa.st.com.item;

import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import unsa.st.com.gui.TerminalScreen;

public class TerminalPanelItem extends Item {
    public TerminalPanelItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            // 服务端可以做一些准备工作（如创建用户目录）
            unsa.st.com.filesystem.UserFileSystem.createUserDirectory(serverPlayer.getUUID());
            // 通知客户端打开 GUI 可以通过发包，这里简化：客户端直接打开
        }
        if (level.isClientSide) {
            openTerminalScreen();
        }
        return InteractionResultHolder.success(stack);
    }

    @OnlyIn(Dist.CLIENT)
    private void openTerminalScreen() {
        Minecraft.getInstance().setScreen(new TerminalScreen());
    }
}
