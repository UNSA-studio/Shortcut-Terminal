package unsa.st.com.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.common.NeoForge;
import unsa.st.com.util.OfflineTeleportManager;

import java.util.UUID;

public class ModCommands {
    private static final SuggestionProvider<CommandSourceStack> ACTIONS = (ctx, builder) -> {
        return SharedSuggestionProvider.suggest(new String[]{"switchingmode", "changebirthpoint", "transmitto_online", "transmitto_offline"}, builder);
    };

    private static final SuggestionProvider<CommandSourceStack> MODES = (ctx, builder) -> {
        return SharedSuggestionProvider.suggest(new String[]{"creative", "survival", "adventure", "spectator", "0", "1", "2", "3"}, builder);
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("ST")
                .then(Commands.literal("User")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("action", StringArgumentType.word()).suggests(ACTIONS)
                            .then(Commands.argument("params", StringArgumentType.greedyString())
                                .executes(ctx -> executeUser(ctx.getSource(), 
                                    EntityArgument.getPlayer(ctx, "player"),
                                    StringArgumentType.getString(ctx, "action"),
                                    StringArgumentType.getString(ctx, "params")))
                            )
                            .executes(ctx -> {
                                // 没有参数的情况，显示用法
                                ctx.getSource().sendFailure(Component.translatable("st.command.user.usage"));
                                return 0;
                            })
                        )
                    )
                )
                // ... 其他原有命令保持不变 ...
        );
    }

    private static int executeUser(CommandSourceStack source, ServerPlayer target, String action, String params) {
        if (!source.hasPermission(2)) {
            source.sendFailure(Component.literal("Permission denied"));
            return 0;
        }

        String[] args = params.split(" ");
        switch (action.toLowerCase()) {
            case "switchingmode":
                if (args.length < 1) {
                    source.sendFailure(Component.literal("Missing mode parameter"));
                    return 0;
                }
                GameType gameType = switch (args[0].toLowerCase()) {
                    case "creative", "1" -> GameType.CREATIVE;
                    case "survival", "0" -> GameType.SURVIVAL;
                    case "adventure", "2" -> GameType.ADVENTURE;
                    case "spectator", "3" -> GameType.SPECTATOR;
                    default -> null;
                };
                if (gameType == null) {
                    source.sendFailure(Component.literal("Invalid game mode"));
                    return 0;
                }
                target.setGameMode(gameType);
                source.sendSuccess(() -> Component.translatable("st.command.user.mode_set", target.getName().getString(), gameType.getName()), true);
                break;

            case "changebirthpoint":
                BlockPos pos = target.blockPosition();
                target.setRespawnPosition(target.level().dimension(), pos, 0, true, false);
                source.sendSuccess(() -> Component.translatable("st.command.user.birth_set", target.getName().getString()), true);
                break;

            case "transmitto_online":
                if (args.length < 1) {
                    source.sendFailure(Component.translatable("st.command.user.invalid_coords"));
                    return 0;
                }
                if (args[0].equalsIgnoreCase("home")) {
                    BlockPos home = target.getRespawnPosition();
                    if (home == null) home = target.level().getSharedSpawnPos();
                    target.teleportTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
                    source.sendSuccess(() -> Component.translatable("st.command.user.teleport_online", target.getName().getString(), "home"), true);
                } else {
                    try {
                        double x = Double.parseDouble(args[0]);
                        double y = Double.parseDouble(args[1]);
                        double z = Double.parseDouble(args[2]);
                        target.teleportTo(x, y, z);
                        source.sendSuccess(() -> Component.translatable("st.command.user.teleport_online", target.getName().getString(), x + " " + y + " " + z), true);
                    } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                        source.sendFailure(Component.translatable("st.command.user.invalid_coords"));
                        return 0;
                    }
                }
                break;

            case "transmitto_offline":
                // 处理离线传送，保存到文件
                if (args.length < 1) {
                    source.sendFailure(Component.translatable("st.command.user.invalid_coords"));
                    return 0;
                }
                UUID uuid = target.getUUID();
                if (args[0].equalsIgnoreCase("home")) {
                    OfflineTeleportManager.saveHomeTeleport(uuid);
                    source.sendSuccess(() -> Component.translatable("st.command.user.teleport_offline", target.getName().getString(), "home"), true);
                } else {
                    try {
                        double x = Double.parseDouble(args[0]);
                        double y = Double.parseDouble(args[1]);
                        double z = Double.parseDouble(args[2]);
                        OfflineTeleportManager.saveCoordTeleport(uuid, x, y, z);
                        source.sendSuccess(() -> Component.translatable("st.command.user.teleport_offline", target.getName().getString(), x + " " + y + " " + z), true);
                    } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                        source.sendFailure(Component.translatable("st.command.user.invalid_coords"));
                        return 0;
                    }
                }
                break;

            default:
                source.sendFailure(Component.translatable("st.command.user.unknown_action", action));
                return 0;
        }
        return 1;
    }
}
