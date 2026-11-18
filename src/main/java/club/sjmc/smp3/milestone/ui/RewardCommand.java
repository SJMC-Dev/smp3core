package club.sjmc.smp3.milestone.ui;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import club.sjmc.smp3.milestone.MilestoneModule;
import club.sjmc.smp3.perm.Permissions;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class RewardCommand {
	private RewardCommand() {}

	public static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("rewards")
		    .requires(source -> Permissions.isAdmin(source, "smp.rewards.admin"))
		    .then(Commands.literal("reload").executes(context -> {
			    var source = context.getSource();
			    try {
				    MilestoneModule.reload(source.getServer());
				    source.sendSuccess(() -> Component.literal("里程碑奖励配置已重载"), true);
				    return 1;
			    } catch (Exception error) {
				    source.sendFailure(Component.literal("里程碑奖励配置重载失败：" + error.getMessage()));
				    return 0;
			    }
		    }));
	}
}
