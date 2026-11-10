package club.sjmc.smp3.command;

import com.mojang.brigadier.CommandDispatcher;

import club.sjmc.smp3.gui.SignGui;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /sign}：打开签到界面，所有玩家可用。
 */
public final class SignCommand {

	private SignCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("sign").executes(context -> {
			ServerPlayer player = context.getSource().getPlayer();
			if (player == null) {
				context.getSource().sendFailure(Component.literal("只有玩家可以使用 /sign"));
				return 0;
			}
			SignGui.open(player);
			return 1;
		}));
	}
}
