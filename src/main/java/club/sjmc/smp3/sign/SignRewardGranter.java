package club.sjmc.smp3.sign;

import java.math.BigInteger;
import java.util.List;

import club.sjmc.smp3.Smp3Core;
import club.sjmc.smp3.economy.Smp3Currency;
import eu.pb4.common.economy.api.CommonEconomy;
import eu.pb4.common.economy.api.EconomyAccount;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 签到奖励发放器：货币走 common-economy-api，物品进背包（满了掉脚下），
 * 称号通过 LuckPerms 权限节点授予，命令用服务端命令源执行。
 */
public final class SignRewardGranter {

	private SignRewardGranter() {
	}

	public static void grant(ServerPlayer player, List<SignReward> rewards) {
		for (SignReward reward : rewards) {
			switch (reward) {
				case SignReward.Currency currency -> grantCurrency(player, currency);
				case SignReward.Item item -> grantItem(player, item);
				case SignReward.Title title -> grantTitle(player, title);
				case SignReward.Command command -> runCommand(player, command);
			}
		}
	}

	private static void grantCurrency(ServerPlayer player, SignReward.Currency reward) {
		try {
			EconomyAccount account = CommonEconomy.getAccount(player, reward.currency().id());
			if (account == null) {
				Smp3Core.LOGGER.error("[SMP3] 签到发放失败：找不到货币账户 {}", reward.currency().id());
				return;
			}
			BigInteger raw = BigInteger.valueOf((long) reward.amount() * Smp3Currency.RAW_PER_UNIT);
			account.increaseBalance(raw);
		} catch (Throwable t) {
			Smp3Core.LOGGER.error("[SMP3] 签到发放货币失败：{}", t.toString());
		}
	}

	private static void grantItem(ServerPlayer player, SignReward.Item reward) {
		ItemStack stack = new ItemStack(reward.item(), reward.count());
		if (!player.getInventory().add(stack) && !stack.isEmpty()) {
			player.spawnAtLocation(player.level(), stack);
		}
	}

	private static void grantTitle(ServerPlayer player, SignReward.Title reward) {
		try {
			LuckPerms luckPerms = LuckPermsProvider.get();
			User user = luckPerms.getUserManager().getUser(player.getUUID());
			if (user == null) {
				Smp3Core.LOGGER.error("[SMP3] 签到发放称号失败：玩家 {} 的 LuckPerms 数据未加载", player.getUUID());
				return;
			}
			user.data().add(Node.builder("fuji.title.obtain." + reward.titleId()).value(true).build());
			luckPerms.getUserManager().saveUser(user);
		} catch (Throwable t) {
			Smp3Core.LOGGER.error("[SMP3] 签到发放称号失败：{}", t.toString());
		}
	}

	private static void runCommand(ServerPlayer player, SignReward.Command reward) {
		try {
			String command = reward.template().replace("<player>", player.getName().getString());
			player.level().getServer().getCommands()
					.performPrefixedCommand(player.createCommandSourceStack(), command);
		} catch (Throwable t) {
			Smp3Core.LOGGER.error("[SMP3] 签到执行命令失败：{}", t.toString());
		}
	}
}
