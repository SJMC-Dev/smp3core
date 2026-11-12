package club.sjmc.smp3.economy;

import eu.pb4.common.economy.api.CommonEconomy;
import eu.pb4.common.economy.api.EconomyAccount;
import eu.pb4.common.economy.api.EconomyTransaction;

import club.sjmc.smp3.Smp3Core;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class EconomyBridge {

	private static boolean warned;

	private EconomyBridge() {}

	public record Outcome(boolean success, Integer balanceAfter, Component message) {

		static Outcome failure(Component message) { return new Outcome(false, null, message); }
	}

	private static EconomyAccount account(ServerPlayer player, Smp3Currency currency) {
		try {
			return CommonEconomy.getAccount(player, currency.id());
		} catch (Throwable t) {
			if (!warned) {
				warned = true;
				Smp3Core.LOGGER.warn("[SMP3] 无法获取通宝账户（common-economy-api 不可用，需安装 Fuji）：{}",
				                     t.toString());
			}
			return null;
		}
	}

	public static Integer balance(ServerPlayer player, Smp3Currency currency) {
		EconomyAccount account = account(player, currency);
		if (account == null) {
			return null;
		}
		try {
			return Smp3Currency.toUnits(account.balance());
		} catch (Throwable t) {
			Smp3Core.LOGGER.error("[SMP3] 读取余额异常：{}", t.toString());
			return null;
		}
	}

	public static Outcome withdraw(ServerPlayer player, Smp3Currency currency, int units) {
		EconomyAccount account = account(player, currency);
		if (account == null) {
			return Outcome.failure(Component.literal("经济系统不可用"));
		}
		try {
			return toOutcome(account.decreaseBalance(Smp3Currency.toRaw(units)));
		} catch (Throwable t) {
			Smp3Core.LOGGER.error("[SMP3] 扣款异常：{}", t.toString());
			return Outcome.failure(Component.literal("扣款失败"));
		}
	}

	public static Outcome deposit(ServerPlayer player, Smp3Currency currency, int units) {
		EconomyAccount account = account(player, currency);
		if (account == null) {
			return Outcome.failure(Component.literal("经济系统不可用"));
		}
		try {
			return toOutcome(account.increaseBalance(Smp3Currency.toRaw(units)));
		} catch (Throwable t) {
			Smp3Core.LOGGER.error("[SMP3] 入账异常：{}", t.toString());
			return Outcome.failure(Component.literal("入账失败"));
		}
	}

	private static Outcome toOutcome(EconomyTransaction transaction) {
		Integer after = transaction.isSuccessful() ? Smp3Currency.toUnits(transaction.finalBalance()) : null;
		return new Outcome(transaction.isSuccessful(), after, transaction.message());
	}
}
