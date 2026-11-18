package club.sjmc.smp3.sign.ui;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class SignCommand {

	private SignCommand() {}

	public static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("sign").executes(context -> {
			ServerPlayer player = context.getSource().getPlayer();
			if (player == null) {
				context.getSource().sendFailure(Component.literal("只有玩家可以使用 /smp3 sign"));
				return 0;
			}
			SignGui.open(player);
			return 1;
		});
	}
}
