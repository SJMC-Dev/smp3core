package club.sjmc.smp3.shop.ui;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import club.sjmc.smp3.perm.Permissions;
import club.sjmc.smp3.shop.ShopModule;

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

	public static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("shop")
		    .then(
		        Commands.argument("player", StringArgumentType.word())
		            .requires(source
					          -> source.permissions().hasPermission(
		                          new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)))
		            .suggests((context, builder)
					              -> SharedSuggestionProvider.suggest(context.getSource().getServer().getPlayerNames(),
					                                                  builder))
		            .executes(context -> openFor(context.getSource(), StringArgumentType.getString(context, "player"))))
		    .then(Commands.literal("reload")
		              .requires(source
		                        -> source.getEntity() == null ||
		                               source.permissions().hasPermission(
		                                   new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)) ||
		                               source.getEntity() instanceof ServerPlayer player &&
		                                   Permissions.has(player, "smp.shop.admin", true))
		              .executes(context -> {
			              var source = context.getSource();
			              if (!ShopModule.reload(message
						                         -> source.sendSuccess(() -> Component.literal(message), false),
						                         message -> source.sendFailure(Component.literal(message)))) {
				              source.sendFailure(Component.literal("商店正在加载，请稍候"));
				              return 0;
			              }
			              source.sendSuccess(() -> Component.literal("正在加载商店…"), false);
			              return 1;
		              }));
	}

	private static int openFor(CommandSourceStack source, String playerName) {
		ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(playerName);
		if (target == null) {
			source.sendFailure(Component.literal("玩家不在线：" + playerName).withStyle(ChatFormatting.RED));
			return 0;
		}
		if (ShopModule.catalog() == null) {
			source.sendFailure(Component.literal("商店暂不可用，请检查配置"));
			return 0;
		}
		if (!ShopModule.open(target)) {
			source.sendFailure(Component.literal("请更新 SMP3 客户端以打开商店"));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("已为 " + target.getName().getString() + " 打开商店"), false);
		return 1;
	}
}
