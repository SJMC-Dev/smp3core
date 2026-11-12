package club.sjmc.smp3.sign;

import club.sjmc.smp3.Smp3Core;
import club.sjmc.smp3.inventory.PlayerItems;
import club.sjmc.smp3.perm.Permissions;

import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

final class SignRewardGranter {
	public enum Result { SUCCESS, RETRY, REVIEW }
	private SignRewardGranter() {}

	public static Result grant(ServerPlayer player, SignReward reward) {
		try {
			return switch (reward.kind()) {
				case CURRENCY -> throw new IllegalArgumentException("Currency must be committed with its receipt");
				case ITEM -> {
					PlayerItems.give(player,
					                 new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(reward.value())),
					                               reward.amount()));
					yield Result.SUCCESS;
				}
				case TITLE -> {
					Permissions.grant(player, "fuji.title.obtain." + reward.value());
					yield Result.SUCCESS;
				}
				case COMMAND -> command(player, reward.value());
			};
		} catch (RuntimeException | LinkageError e) {
			Smp3Core.LOGGER.error("[SMP3] 签到奖励待核对 player={} reward={}", player.getUUID(), reward, e);
			return reward.kind() == SignReward.Kind.TITLE ? Result.RETRY : Result.REVIEW;
		}
	}

	private static Result command(ServerPlayer player, String template) {
		var server = player.level().getServer();
		String command = Commands.trimOptionalPrefix(template.replace("<player>", player.getGameProfile().name()));
		Result[] result = {Result.REVIEW};
		var source = server.createCommandSourceStack().withSuppressedOutput().withCallback(
		    (success, value) -> { result[0] = success && value > 0 ? Result.SUCCESS : Result.RETRY; });
		var parsed = server.getCommands().getDispatcher().parse(command, source);
		if (parsed.getReader().canRead() || !parsed.getExceptions().isEmpty()) {
			return Result.RETRY;
		}
		server.getCommands().performCommand(parsed, command);
		return result[0];
	}
}
