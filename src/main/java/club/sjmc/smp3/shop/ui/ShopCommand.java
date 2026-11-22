package club.sjmc.smp3.shop.ui;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import club.sjmc.smp3.perm.Permissions;
import club.sjmc.smp3.shop.ShopModule;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;

public final class ShopCommand {

	private ShopCommand() {}

	public static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("shop")
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
}
