package club.sjmc.smp3.sign.ui;

import club.sjmc.smp3.perm.Permissions;
import club.sjmc.smp3.sign.SignModule;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class SignCommand {

	private SignCommand() {}

	public static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("sign").then(
		    Commands.literal("reload")
		        .requires(source -> Permissions.isAdmin(source, "smp.sign.admin"))
		        .executes(context -> {
			        var source = context.getSource();
			        try {
				        SignModule.reload(source.getServer());
				        source.sendSuccess(() -> Component.literal("签到奖励配置已重载"), false);
				        return 1;
			        } catch (Exception error) {
				        source.sendFailure(Component.literal("签到配置错误：" + error.getMessage()));
				        return 0;
			        }
		        }));
	}
}
