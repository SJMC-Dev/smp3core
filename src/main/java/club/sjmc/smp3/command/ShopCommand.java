package club.sjmc.smp3.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import club.sjmc.smp3.gui.SheetListGui;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;

/**
 * {@code /shop <玩家>}：为指定玩家打开商店。
 *
 * <p>要求原版 op 等级 4（{@link PermissionLevel#OWNERS}），玩家参数必填，玩家离线时报错。
 * 不提供其它子命令（无 reload、无额度查询/重置）。</p>
 */
public final class ShopCommand {

	private ShopCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("shop")
				.requires(source -> source.permissions().hasPermission(
						new Permission.HasCommandLevel(PermissionLevel.OWNERS)))
				.then(Commands.argument("player", StringArgumentType.word())
						.suggests((context, builder) -> SharedSuggestionProvider.suggest(
								context.getSource().getServer().getPlayerNames(), builder))
						.executes(context -> openFor(context.getSource(),
								StringArgumentType.getString(context, "player")))));
	}

	private static int openFor(CommandSourceStack source, String playerName) {
		ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(playerName);
		if (target == null) {
			source.sendFailure(Component.literal("玩家不在线：" + playerName).withStyle(ChatFormatting.RED));
			return 0;
		}
		new SheetListGui(target).open();
		source.sendSuccess(() -> Component.literal("已为 " + target.getName().getString() + " 打开商店"), false);
		return 1;
	}
}
