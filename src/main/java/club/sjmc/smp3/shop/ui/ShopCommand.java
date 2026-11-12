package club.sjmc.smp3.shop.ui;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;

public final class ShopCommand {

	private ShopCommand() {}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(
		    Commands.literal("shop")
		        .requires(
		            source
		            -> source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)))
		        .then(Commands.argument("player", StringArgumentType.word())
				          .suggests((context, builder)
				                        -> SharedSuggestionProvider.suggest(
		                                    context.getSource().getServer().getPlayerNames(), builder))
				          .executes(context
				                    -> openFor(context.getSource(), StringArgumentType.getString(context, "player")))));
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
