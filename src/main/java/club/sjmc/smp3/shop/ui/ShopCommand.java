package club.sjmc.smp3.shop.ui;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import club.sjmc.smp3.perm.Permissions;
import club.sjmc.smp3.shop.ShopModule;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class ShopCommand {

	private ShopCommand() {}

	public static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("shop")
		    .then(Commands.literal("reload")
		              .requires(source -> Permissions.isAdmin(source, "smp.shop.admin"))
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
