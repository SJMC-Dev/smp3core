package club.sjmc.smp3.menu.ui;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class MenuCommand {
	private MenuCommand() {}

	public static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("menu").executes(context -> {
			ServerPlayer player = context.getSource().getPlayer();
			if (player == null) {
				context.getSource().sendFailure(Component.literal("只有玩家可以使用 /smp3 menu"));
				return 0;
			}
			MenuView.open(player);
			return 1;
		});
	}
}
